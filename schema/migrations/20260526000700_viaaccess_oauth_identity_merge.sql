-- ViaAccess: fix GitHub OAuth → external_identities merge
--
-- When an admin pre-provisions an external_identities row (e.g. via importProviderIdentities)
-- the row's user_id is the matched ViaAccess user. If that same person later signs in via
-- GitHub OAuth for the first time Supabase creates them as a new auth user and fires this
-- trigger with a different user_id. The previous trigger version only upserted on
-- (user_id, provider), so it would conflict on the (provider, external_user_id) unique
-- constraint and fail silently.
--
-- This version explicitly handles both conflict paths:
--   1. The new OAuth user already has a row for this provider → update the handle/verified_at.
--   2. A different user owns this external_user_id → re-point user_id to the new OAuth user
--      (the person just proved via OAuth that they own this account).

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

    IF v_provider NOT IN ('github', 'google') THEN
        RETURN NEW;
    END IF;

    v_external_user_id := COALESCE(
        NEW.identity_data->>'sub',
        NEW.identity_data->>'provider_id'
    );

    IF v_provider = 'github' THEN
        v_external_username := COALESCE(
            NEW.identity_data->>'user_name',
            NEW.identity_data->>'login',
            NEW.identity_data->>'preferred_username',
            NEW.identity_data->>'email'
        );
    ELSE
        v_external_username := COALESCE(
            NEW.identity_data->>'email',
            NEW.identity_data->>'sub'
        );
    END IF;

    IF v_external_user_id IS NULL OR v_external_username IS NULL THEN
        RETURN NEW;
    END IF;

    -- If another user_id already owns this external identity (admin pre-provisioned),
    -- re-point it to the newly-authenticated OAuth user. This is safe because the
    -- OAuth flow is proof of ownership.
    UPDATE public.external_identities
    SET
        user_id          = NEW.user_id,
        external_username = v_external_username,
        verified_at      = now()
    WHERE provider          = v_provider
      AND external_user_id  = v_external_user_id
      AND user_id          != NEW.user_id;

    -- Now upsert for the authoritative (user_id, provider) row.
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
        now()
    )
    ON CONFLICT (user_id, provider) DO UPDATE SET
        external_user_id  = EXCLUDED.external_user_id,
        external_username = EXCLUDED.external_username,
        verified_at       = EXCLUDED.verified_at;

    RETURN NEW;
END;
$$;

-- Triggers are already installed by the previous migration; replacing the function is enough.
-- But re-create them idempotently in case the previous migration hasn't been applied.
DROP TRIGGER IF EXISTS trg_sync_oauth_identity_to_external ON auth.identities;
CREATE TRIGGER trg_sync_oauth_identity_to_external
    AFTER INSERT ON auth.identities
    FOR EACH ROW
    EXECUTE FUNCTION public.sync_oauth_identity_to_external();

DROP TRIGGER IF EXISTS trg_sync_oauth_identity_update ON auth.identities;
CREATE TRIGGER trg_sync_oauth_identity_update
    AFTER UPDATE ON auth.identities
    FOR EACH ROW
    WHEN (OLD.identity_data IS DISTINCT FROM NEW.identity_data)
    EXECUTE FUNCTION public.sync_oauth_identity_to_external();
