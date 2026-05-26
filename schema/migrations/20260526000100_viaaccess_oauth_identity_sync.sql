-- ViaAccess Phase 3: Auto-sync OAuth identities into external_identities
--
-- Supabase populates auth.identities whenever a user connects a social provider.
-- This trigger watches for github and google provider rows and upserts the
-- corresponding external_identities row so the identity is immediately visible
-- to import/reconcile flows — without requiring a manual setExternalIdentity call.
--
-- For GitHub: we have a stable numeric id and a login, so we can set verified_at
-- right away (the user just proved ownership of that account via OAuth).
--
-- For Google: Google identity does NOT imply a GitHub or Asana identity, so we
-- do not create an external_identities row for provider='github'. We do store
-- a provider='google' row for future use (e.g. linking email-matched accounts).
-- verified_at is set because the OAuth flow verified the email claim.

CREATE OR REPLACE FUNCTION public.sync_oauth_identity_to_external()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_provider TEXT;
    v_external_user_id TEXT;
    v_external_username TEXT;
BEGIN
    v_provider := NEW.provider;

    -- Only handle github and google for now
    IF v_provider NOT IN ('github', 'google') THEN
        RETURN NEW;
    END IF;

    -- Extract the stable provider user id from identity_data
    v_external_user_id := NEW.identity_data->>'sub';
    IF v_external_user_id IS NULL OR v_external_user_id = '' THEN
        -- Fallback for older Supabase identity_data shapes
        v_external_user_id := NEW.identity_data->>'provider_id';
    END IF;

    IF v_provider = 'github' THEN
        -- GitHub exposes the login (handle) in identity_data
        v_external_username := COALESCE(
            NEW.identity_data->>'user_name',
            NEW.identity_data->>'login',
            NEW.identity_data->>'preferred_username',
            NEW.identity_data->>'email'
        );
    ELSE
        -- Google: use email as the "username" since there is no handle concept
        v_external_username := COALESCE(
            NEW.identity_data->>'email',
            NEW.identity_data->>'sub'
        );
    END IF;

    IF v_external_user_id IS NULL OR v_external_username IS NULL THEN
        RETURN NEW;
    END IF;

    INSERT INTO public.external_identities (
        user_id,
        provider,
        external_user_id,
        external_username,
        verified_at
    )
    VALUES (
        NEW.user_id,
        v_provider,
        v_external_user_id,
        v_external_username,
        now()  -- OAuth proves ownership; mark verified immediately
    )
    ON CONFLICT (user_id, provider) DO UPDATE SET
        external_user_id  = EXCLUDED.external_user_id,
        external_username = EXCLUDED.external_username,
        verified_at       = EXCLUDED.verified_at;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_sync_oauth_identity_to_external
    AFTER INSERT ON auth.identities
    FOR EACH ROW
    EXECUTE FUNCTION public.sync_oauth_identity_to_external();

-- Also fire on UPDATE in case the user re-authenticates and their handle changed
CREATE OR REPLACE TRIGGER trg_sync_oauth_identity_update
    AFTER UPDATE ON auth.identities
    FOR EACH ROW
    WHEN (OLD.identity_data IS DISTINCT FROM NEW.identity_data)
    EXECUTE FUNCTION public.sync_oauth_identity_to_external();
