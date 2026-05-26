-- ViaAccess: tenant governance tables
-- TenantAsset and TenantAssetPolicy are governance-only — they control who
-- may administer a tenant, not what external services a group can access.
-- These do NOT produce RECONCILE_ASSET jobs and are NOT rows in public.assets.

CREATE TABLE IF NOT EXISTS public.tenant_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_name TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (tenant_name)
);

ALTER TABLE public.tenant_assets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can view tenant assets"
    ON public.tenant_assets FOR SELECT
    USING (auth.uid() IS NOT NULL);

CREATE POLICY "Admins can manage tenant assets"
    ON public.tenant_assets FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- Grants a group a permission level on a tenant
CREATE TABLE IF NOT EXISTS public.tenant_asset_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_asset_id UUID NOT NULL REFERENCES public.tenant_assets(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    permission TEXT NOT NULL CHECK (permission IN ('VIEWER', 'EDITOR', 'OWNER')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (tenant_asset_id, group_id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_asset_policies_tenant_asset_id
    ON public.tenant_asset_policies(tenant_asset_id);
CREATE INDEX IF NOT EXISTS idx_tenant_asset_policies_group_id
    ON public.tenant_asset_policies(group_id);

ALTER TABLE public.tenant_asset_policies ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Group members can view tenant asset policies"
    ON public.tenant_asset_policies FOR SELECT
    USING (public.is_group_member(group_id) OR public.is_admin());

CREATE POLICY "Admins can manage tenant asset policies"
    ON public.tenant_asset_policies FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- Helper: check if a user has at least the given permission on a tenant
CREATE OR REPLACE FUNCTION public.has_tenant_permission(tenant TEXT, required_permission TEXT)
RETURNS BOOLEAN AS $$
DECLARE
    permission_rank INT;
    required_rank INT;
BEGIN
    -- Map permission names to numeric rank for hierarchical comparison
    SELECT CASE
        WHEN p.permission = 'VIEWER'  THEN 1
        WHEN p.permission = 'EDITOR'  THEN 2
        WHEN p.permission = 'OWNER'   THEN 3
        ELSE 0
    END INTO permission_rank
    FROM public.tenant_asset_policies p
    JOIN public.tenant_assets ta ON ta.id = p.tenant_asset_id
    JOIN public.group_members gm ON gm.group_id = p.group_id
    WHERE ta.tenant_name = tenant
      AND gm.user_id = auth.uid()
    ORDER BY permission_rank DESC
    LIMIT 1;

    required_rank := CASE required_permission
        WHEN 'VIEWER' THEN 1
        WHEN 'EDITOR' THEN 2
        WHEN 'OWNER'  THEN 3
        ELSE 99
    END;

    RETURN COALESCE(permission_rank, 0) >= required_rank;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Seed the default tenant_asset so bootstrap can proceed
INSERT INTO public.tenant_assets (tenant_name)
VALUES ('default')
ON CONFLICT (tenant_name) DO NOTHING;

INSERT INTO public.tenant_assets (tenant_name)
VALUES ('github')
ON CONFLICT (tenant_name) DO NOTHING;

INSERT INTO public.tenant_assets (tenant_name)
VALUES ('asana')
ON CONFLICT (tenant_name) DO NOTHING;
