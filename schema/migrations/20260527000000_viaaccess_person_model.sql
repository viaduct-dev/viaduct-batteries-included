-- ViaAccess: person-centric identity model
--
-- Core insight: provisioning is the primary use case. A person can be added to a group
-- and have their access provisioned in GitHub/Asana before they ever log into ViaAccess.
-- Multiple external identities (github, asana, ...) can belong to the same person.
-- Auth users are optional and linked after the fact (e.g. on first OAuth sign-in).
--
-- Model:
--   groups --< group_members >-- persons --< external_identities
--                                   |
--                                   `-- auth.users (nullable)
--
-- Migration steps:
--   1. Create persons table
--   2. Auto-provision persons for all existing auth users
--   3. Add person_id to group_members and external_identities
--   4. Back-fill person_id from the provisioned persons
--   5. Drop old user_id columns and constraints
--   6. Update RLS helper functions to join through persons
--   7. Update triggers (add_owner_to_group, sync_oauth_identity_to_external)
--   8. Add trigger to auto-create person on new auth.users row

-- ─────────────────────────────────────────────────────────────
-- 1. persons table
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.persons (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name    TEXT,
    email           TEXT,
    auth_user_id    UUID UNIQUE REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_persons_auth_user_id ON public.persons(auth_user_id);

ALTER TABLE public.persons ENABLE ROW LEVEL SECURITY;

-- Users can see their own person; EDITOR+ can see all
CREATE POLICY "Users can view own person"
    ON public.persons FOR SELECT
    USING (
        auth_user_id = auth.uid()
        OR public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
    );

-- EDITOR+ can create/update persons (import, reconcile flows)
CREATE POLICY "Editors can manage persons"
    ON public.persons FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
    );

CREATE POLICY "Editors can update persons"
    ON public.persons FOR UPDATE
    USING (
        public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
    );

CREATE POLICY "Admins can delete persons"
    ON public.persons FOR DELETE
    USING (public.is_admin());

-- ─────────────────────────────────────────────────────────────
-- 2. Auto-provision persons for all existing auth users
-- ─────────────────────────────────────────────────────────────

INSERT INTO public.persons (auth_user_id)
SELECT id FROM auth.users
ON CONFLICT (auth_user_id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────
-- 3 + 4. Migrate group_members: user_id → person_id
-- ─────────────────────────────────────────────────────────────

ALTER TABLE public.group_members
    ADD COLUMN IF NOT EXISTS person_id UUID REFERENCES public.persons(id) ON DELETE CASCADE;

UPDATE public.group_members gm
SET person_id = p.id
FROM public.persons p
WHERE p.auth_user_id = gm.user_id;

-- Drop rows that couldn't be matched (should be none in a clean install)
DELETE FROM public.group_members WHERE person_id IS NULL;

ALTER TABLE public.group_members
    ALTER COLUMN person_id SET NOT NULL;

-- Recreate unique constraint on (group_id, person_id)
ALTER TABLE public.group_members
    DROP CONSTRAINT IF EXISTS group_members_group_id_user_id_key;

ALTER TABLE public.group_members
    ADD CONSTRAINT group_members_group_id_person_id_key UNIQUE (group_id, person_id);

-- Drop all policies that reference user_id before dropping the column, regardless of
-- which prior migration created them.
DROP POLICY IF EXISTS "Admins can add group members" ON public.group_members;
DROP POLICY IF EXISTS "Members can remove themselves or admins can remove anyone" ON public.group_members;
DROP POLICY IF EXISTS "Default tenant editors can add group members" ON public.group_members;
DROP POLICY IF EXISTS "Default tenant editors or self can remove group members" ON public.group_members;
DROP POLICY IF EXISTS "Users and tenant editors can view group memberships" ON public.group_members;
DROP POLICY IF EXISTS "Users can view group memberships they are part of" ON public.group_members;
DROP POLICY IF EXISTS "Group members can view memberships" ON public.group_members;
DROP POLICY IF EXISTS "Authenticated users can view their group memberships" ON public.group_members;

ALTER TABLE public.group_members
    DROP COLUMN IF EXISTS user_id;

-- ─────────────────────────────────────────────────────────────
-- 3 + 4. Migrate external_identities: user_id → person_id
-- ─────────────────────────────────────────────────────────────

ALTER TABLE public.external_identities
    ADD COLUMN IF NOT EXISTS person_id UUID REFERENCES public.persons(id) ON DELETE CASCADE;

UPDATE public.external_identities ei
SET person_id = p.id
FROM public.persons p
WHERE p.auth_user_id = ei.user_id;

-- Rows with no matching person: create orphan person records for them
INSERT INTO public.persons (auth_user_id)
SELECT DISTINCT user_id
FROM public.external_identities
WHERE person_id IS NULL
ON CONFLICT (auth_user_id) DO NOTHING;

UPDATE public.external_identities ei
SET person_id = p.id
FROM public.persons p
WHERE p.auth_user_id = ei.user_id
  AND ei.person_id IS NULL;

DELETE FROM public.external_identities WHERE person_id IS NULL;

ALTER TABLE public.external_identities
    ALTER COLUMN person_id SET NOT NULL;

-- Drop old unique constraint on (user_id, provider), replace with (person_id, provider)
ALTER TABLE public.external_identities
    DROP CONSTRAINT IF EXISTS external_identities_user_id_provider_key;

ALTER TABLE public.external_identities
    ADD CONSTRAINT external_identities_person_id_provider_key UNIQUE (person_id, provider);

-- Drop policies that reference user_id before dropping the column
DROP POLICY IF EXISTS "Users and tenant editors can view external identities" ON public.external_identities;

ALTER TABLE public.external_identities
    DROP COLUMN IF EXISTS user_id;

-- ─────────────────────────────────────────────────────────────
-- 5. Update RLS on group_members to use person_id
-- ─────────────────────────────────────────────────────────────

CREATE POLICY "Editors can add group members"
    ON public.group_members FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
    );

CREATE POLICY "Editors can remove group members"
    ON public.group_members FOR DELETE
    USING (
        public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
        -- A person can remove themselves
        OR person_id IN (SELECT id FROM public.persons WHERE auth_user_id = auth.uid())
    );

CREATE POLICY "Members can view group memberships"
    ON public.group_members FOR SELECT
    USING (
        public.is_admin()
        OR public.is_group_member(group_id)
        OR public.has_tenant_permission('default', 'EDITOR')
    );

-- ─────────────────────────────────────────────────────────────
-- 6. Update external_identities RLS to use person_id
-- ─────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Users can view own external identities" ON public.external_identities;
DROP POLICY IF EXISTS "Admins can manage external identities" ON public.external_identities;
-- Also drop policies from the worker/identity migration if present
DROP POLICY IF EXISTS "Authenticated users can view their own identities" ON public.external_identities;
DROP POLICY IF EXISTS "Editors can view all identities" ON public.external_identities;
DROP POLICY IF EXISTS "Service role manages all identities" ON public.external_identities;
DROP POLICY IF EXISTS "Editors can write identities" ON public.external_identities;

CREATE POLICY "Users can view own external identities"
    ON public.external_identities FOR SELECT
    USING (
        person_id IN (SELECT id FROM public.persons WHERE auth_user_id = auth.uid())
        OR public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
    );

CREATE POLICY "Editors can manage external identities"
    ON public.external_identities FOR ALL
    USING (
        public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
    )
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
    );

-- ─────────────────────────────────────────────────────────────
-- 7. Update is_group_member() to join through persons
-- ─────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.is_group_member(group_uuid UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1
        FROM public.group_members gm
        JOIN public.persons p ON p.id = gm.person_id
        WHERE gm.group_id = group_uuid
          AND p.auth_user_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- ─────────────────────────────────────────────────────────────
-- 8. Update has_tenant_permission() to join through persons
-- ─────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.has_tenant_permission(tenant TEXT, required_permission TEXT)
RETURNS BOOLEAN AS $$
DECLARE
    permission_rank  INT;
    required_rank    INT;
BEGIN
    SELECT CASE
        WHEN tap.permission = 'REQUESTER' THEN 1
        WHEN tap.permission = 'VIEWER'    THEN 2
        WHEN tap.permission = 'EDITOR'    THEN 3
        WHEN tap.permission = 'OWNER'     THEN 4
        ELSE 0
    END INTO permission_rank
    FROM public.tenant_asset_policies tap
    JOIN public.tenant_assets          ta  ON ta.id  = tap.tenant_asset_id
    JOIN public.group_members          gm  ON gm.group_id = tap.group_id
    JOIN public.persons                p   ON p.id   = gm.person_id
    WHERE ta.tenant_name = tenant
      AND p.auth_user_id = auth.uid()
    ORDER BY permission_rank DESC
    LIMIT 1;

    required_rank := CASE required_permission
        WHEN 'REQUESTER' THEN 1
        WHEN 'VIEWER'    THEN 2
        WHEN 'EDITOR'    THEN 3
        WHEN 'OWNER'     THEN 4
        ELSE 99
    END;

    RETURN COALESCE(permission_rank, 0) >= required_rank;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- ─────────────────────────────────────────────────────────────
-- 9. Update add_owner_to_group trigger to use person_id
-- ─────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.add_owner_to_group()
RETURNS TRIGGER AS $$
DECLARE
    v_person_id UUID;
BEGIN
    -- Find or create the person for the group creator (auth user)
    SELECT id INTO v_person_id FROM public.persons WHERE auth_user_id = NEW.created_by;
    IF v_person_id IS NULL THEN
        INSERT INTO public.persons (auth_user_id) VALUES (NEW.created_by)
        RETURNING id INTO v_person_id;
    END IF;

    INSERT INTO public.group_members (group_id, person_id)
    VALUES (NEW.id, v_person_id)
    ON CONFLICT (group_id, person_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- ─────────────────────────────────────────────────────────────
-- 10. Update sync_oauth_identity_to_external trigger
-- ─────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.sync_oauth_identity_to_external()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_provider          TEXT;
    v_external_user_id  TEXT;
    v_external_username TEXT;
    v_person_id         UUID;
BEGIN
    v_provider := NEW.provider;
    IF v_provider NOT IN ('github', 'google') THEN RETURN NEW; END IF;

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

    IF v_external_user_id IS NULL OR v_external_username IS NULL THEN RETURN NEW; END IF;

    -- Is there already an external identity for this provider account?
    -- (admin pre-provisioned it during import)
    SELECT person_id INTO v_person_id
    FROM public.external_identities
    WHERE provider = v_provider AND external_user_id = v_external_user_id;

    IF v_person_id IS NOT NULL THEN
        -- Person was pre-provisioned — link their auth account now that they've signed in.
        -- trg_create_person_for_new_user may have created a blank placeholder for this
        -- auth_user_id. Remove it only if it is truly empty (no memberships, no
        -- identities), so we don't accidentally discard a real user's data.
        DELETE FROM public.persons p
        WHERE p.auth_user_id = NEW.user_id
          AND p.id <> v_person_id
          AND NOT EXISTS (SELECT 1 FROM public.group_members gm WHERE gm.person_id = p.id)
          AND NOT EXISTS (SELECT 1 FROM public.external_identities ei WHERE ei.person_id = p.id);

        UPDATE public.persons SET auth_user_id = NEW.user_id WHERE id = v_person_id;
        -- Update username in case it changed
        UPDATE public.external_identities
        SET external_username = v_external_username, verified_at = now()
        WHERE provider = v_provider AND external_user_id = v_external_user_id;
    ELSE
        -- No pre-provisioned identity. Find or create a person for this auth user.
        -- (auto-creation trigger on auth.users may have already created it)
        SELECT id INTO v_person_id FROM public.persons WHERE auth_user_id = NEW.user_id;
        IF v_person_id IS NULL THEN
            INSERT INTO public.persons (auth_user_id) VALUES (NEW.user_id)
            RETURNING id INTO v_person_id;
        END IF;

        -- Insert the new identity, or update if this person already connected this provider
        INSERT INTO public.external_identities (
            person_id, provider, external_user_id, external_username, verified_at
        )
        VALUES (v_person_id, v_provider, v_external_user_id, v_external_username, now())
        ON CONFLICT (person_id, provider) DO UPDATE SET
            external_user_id  = EXCLUDED.external_user_id,
            external_username = EXCLUDED.external_username,
            verified_at       = EXCLUDED.verified_at;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_oauth_identity_to_external ON auth.identities;
CREATE TRIGGER trg_sync_oauth_identity_to_external
    AFTER INSERT ON auth.identities
    FOR EACH ROW EXECUTE FUNCTION public.sync_oauth_identity_to_external();

DROP TRIGGER IF EXISTS trg_sync_oauth_identity_update ON auth.identities;
CREATE TRIGGER trg_sync_oauth_identity_update
    AFTER UPDATE ON auth.identities
    FOR EACH ROW
    WHEN (OLD.identity_data IS DISTINCT FROM NEW.identity_data)
    EXECUTE FUNCTION public.sync_oauth_identity_to_external();

-- ─────────────────────────────────────────────────────────────
-- 11. Auto-create person when a new auth user is created
-- ─────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.create_person_for_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.persons (auth_user_id)
    VALUES (NEW.id)
    ON CONFLICT (auth_user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_create_person_for_new_user ON auth.users;
CREATE TRIGGER trg_create_person_for_new_user
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.create_person_for_new_user();

-- ─────────────────────────────────────────────────────────────
-- 12. Fix assets UPDATE policy to allow tenant EDITOR
-- ─────────────────────────────────────────────────────────────
-- The original policy only allowed admins to update assets.
-- setAssetRequestable requires UPDATE, and tenant EDITORs must be
-- able to control the requestable flag on their own tenant's assets.

DROP POLICY IF EXISTS "Admins can update or delete assets" ON public.assets;

CREATE POLICY "Tenant editors can update assets"
    ON public.assets FOR UPDATE
    USING (
        public.is_admin()
        OR public.has_tenant_permission(tenant_name, 'EDITOR')
    );
