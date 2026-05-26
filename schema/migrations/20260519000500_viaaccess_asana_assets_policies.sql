-- ViaAccess: Asana asset and policy tables

-- Asana project assets
CREATE TABLE IF NOT EXISTS public.asana_project_assets (
    id  UUID PRIMARY KEY REFERENCES public.assets(id) ON DELETE CASCADE,
    gid TEXT NOT NULL
);

ALTER TABLE public.asana_project_assets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can view asana project assets"
    ON public.asana_project_assets FOR SELECT
    USING (auth.uid() IS NOT NULL);

CREATE POLICY "Admins can manage asana project assets"
    ON public.asana_project_assets FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- Asana portfolio assets
CREATE TABLE IF NOT EXISTS public.asana_portfolio_assets (
    id  UUID PRIMARY KEY REFERENCES public.assets(id) ON DELETE CASCADE,
    gid TEXT NOT NULL
);

ALTER TABLE public.asana_portfolio_assets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can view asana portfolio assets"
    ON public.asana_portfolio_assets FOR SELECT
    USING (auth.uid() IS NOT NULL);

CREATE POLICY "Admins can manage asana portfolio assets"
    ON public.asana_portfolio_assets FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- Asana project policies
CREATE TABLE IF NOT EXISTS public.asana_project_policies (
    id UUID PRIMARY KEY REFERENCES public.policies(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES public.assets(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    permission TEXT NOT NULL
        CHECK (permission IN ('COMMENTER', 'EDITOR', 'MANAGER')),
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (sync_status IN ('HEALTHY', 'PENDING', 'DEGRADED', 'FAILED')),
    UNIQUE (asset_id, group_id)
);

CREATE INDEX IF NOT EXISTS idx_asana_project_policies_asset_id
    ON public.asana_project_policies(asset_id);
CREATE INDEX IF NOT EXISTS idx_asana_project_policies_group_id
    ON public.asana_project_policies(group_id);

ALTER TABLE public.asana_project_policies ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Group members can view asana project policies"
    ON public.asana_project_policies FOR SELECT
    USING (public.is_group_member(group_id) OR public.is_admin());

CREATE POLICY "Admins can manage asana project policies"
    ON public.asana_project_policies FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- Asana portfolio policies
CREATE TABLE IF NOT EXISTS public.asana_portfolio_policies (
    id UUID PRIMARY KEY REFERENCES public.policies(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES public.assets(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    permission TEXT NOT NULL
        CHECK (permission IN ('COMMENTER', 'EDITOR', 'MANAGER')),
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (sync_status IN ('HEALTHY', 'PENDING', 'DEGRADED', 'FAILED')),
    UNIQUE (asset_id, group_id)
);

CREATE INDEX IF NOT EXISTS idx_asana_portfolio_policies_asset_id
    ON public.asana_portfolio_policies(asset_id);
CREATE INDEX IF NOT EXISTS idx_asana_portfolio_policies_group_id
    ON public.asana_portfolio_policies(group_id);

ALTER TABLE public.asana_portfolio_policies ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Group members can view asana portfolio policies"
    ON public.asana_portfolio_policies FOR SELECT
    USING (public.is_group_member(group_id) OR public.is_admin());

CREATE POLICY "Admins can manage asana portfolio policies"
    ON public.asana_portfolio_policies FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());
