-- ViaAccess: shared parent tables for assets and policies
-- All concrete asset/policy tables FK back to these for referential integrity

-- Shared parent for all asset types
CREATE TABLE IF NOT EXISTS public.assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_type TEXT NOT NULL
        CHECK (asset_type IN ('GITHUB_REPO', 'GITHUB_TEAM', 'ASANA_PROJECT', 'ASANA_PORTFOLIO')),
    tenant_name TEXT NOT NULL,           -- 'github', 'asana', etc.
    external_id TEXT NOT NULL,           -- provider-specific identifier
    name TEXT NOT NULL,                  -- human-readable label, never used as a key
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    -- prevent duplicate registrations of the same external resource
    UNIQUE (tenant_name, asset_type, external_id)
);

CREATE INDEX IF NOT EXISTS idx_assets_tenant_name ON public.assets(tenant_name);
CREATE INDEX IF NOT EXISTS idx_assets_asset_type  ON public.assets(asset_type);

ALTER TABLE public.assets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can view assets"
    ON public.assets FOR SELECT
    USING (auth.uid() IS NOT NULL);

CREATE POLICY "Admins can manage assets"
    ON public.assets FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- Shared parent for all external-access policy types
-- NOTE: TenantAssetPolicy is governance-only and NOT in this table
CREATE TABLE IF NOT EXISTS public.policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_type TEXT NOT NULL
        CHECK (policy_type IN ('GITHUB_REPO', 'GITHUB_TEAM', 'ASANA_PROJECT', 'ASANA_PORTFOLIO')),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_policies_group_id ON public.policies(group_id);

ALTER TABLE public.policies ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Group members can view policies"
    ON public.policies FOR SELECT
    USING (public.is_group_member(group_id) OR public.is_admin());

CREATE POLICY "Admins can manage policies"
    ON public.policies FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());
