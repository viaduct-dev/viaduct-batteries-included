-- ViaAccess: external identity mapping
-- Maps ViaAccess users to their identities in external providers (GitHub, Asana, etc.)

CREATE TABLE IF NOT EXISTS public.external_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,              -- 'github', 'asana', etc.
    external_user_id TEXT NOT NULL,      -- provider's stable numeric/string ID
    external_username TEXT NOT NULL,     -- provider's human-readable handle
    verified_at TIMESTAMP WITH TIME ZONE, -- null until confirmed by reconcile or provider import
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    -- one identity per provider per user
    UNIQUE (user_id, provider),
    -- one system user per external identity
    UNIQUE (provider, external_user_id)
);

CREATE INDEX IF NOT EXISTS idx_external_identities_user_id
    ON public.external_identities(user_id);
CREATE INDEX IF NOT EXISTS idx_external_identities_provider
    ON public.external_identities(provider);

ALTER TABLE public.external_identities ENABLE ROW LEVEL SECURITY;

-- Users can view their own identities; admins can view all
CREATE POLICY "Users can view own external identities"
    ON public.external_identities FOR SELECT
    USING (user_id = auth.uid() OR public.is_admin());

-- Only admins can insert/update/delete (setExternalIdentity requires EDITOR, enforced in resolver)
CREATE POLICY "Admins can manage external identities"
    ON public.external_identities FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());
