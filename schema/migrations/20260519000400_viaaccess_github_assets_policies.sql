-- ViaAccess: GitHub asset and policy tables

-- GitHub repository assets
CREATE TABLE IF NOT EXISTS public.github_repo_assets (
    id UUID PRIMARY KEY REFERENCES public.assets(id) ON DELETE CASCADE,
    owner TEXT NOT NULL,
    repo  TEXT NOT NULL
);

ALTER TABLE public.github_repo_assets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can view github repo assets"
    ON public.github_repo_assets FOR SELECT
    USING (auth.uid() IS NOT NULL);

CREATE POLICY "Admins can manage github repo assets"
    ON public.github_repo_assets FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- GitHub team assets
CREATE TABLE IF NOT EXISTS public.github_team_assets (
    id UUID PRIMARY KEY REFERENCES public.assets(id) ON DELETE CASCADE,
    org  TEXT NOT NULL,
    slug TEXT NOT NULL
);

ALTER TABLE public.github_team_assets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can view github team assets"
    ON public.github_team_assets FOR SELECT
    USING (auth.uid() IS NOT NULL);

CREATE POLICY "Admins can manage github team assets"
    ON public.github_team_assets FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- GitHub repo policies
-- group_id is denormalized from policies parent to enable UNIQUE(asset_id, group_id)
CREATE TABLE IF NOT EXISTS public.github_repo_policies (
    id UUID PRIMARY KEY REFERENCES public.policies(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES public.assets(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    permission TEXT NOT NULL
        CHECK (permission IN ('READ', 'TRIAGE', 'WRITE', 'MAINTAIN', 'ADMIN')),
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (sync_status IN ('HEALTHY', 'PENDING', 'DEGRADED', 'FAILED')),
    UNIQUE (asset_id, group_id)
);

CREATE INDEX IF NOT EXISTS idx_github_repo_policies_asset_id
    ON public.github_repo_policies(asset_id);
CREATE INDEX IF NOT EXISTS idx_github_repo_policies_group_id
    ON public.github_repo_policies(group_id);

ALTER TABLE public.github_repo_policies ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Group members can view github repo policies"
    ON public.github_repo_policies FOR SELECT
    USING (public.is_group_member(group_id) OR public.is_admin());

CREATE POLICY "Admins can manage github repo policies"
    ON public.github_repo_policies FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- GitHub team policies
CREATE TABLE IF NOT EXISTS public.github_team_policies (
    id UUID PRIMARY KEY REFERENCES public.policies(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES public.assets(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    permission TEXT NOT NULL
        CHECK (permission IN ('MEMBER', 'MAINTAINER')),
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (sync_status IN ('HEALTHY', 'PENDING', 'DEGRADED', 'FAILED')),
    UNIQUE (asset_id, group_id)
);

CREATE INDEX IF NOT EXISTS idx_github_team_policies_asset_id
    ON public.github_team_policies(asset_id);
CREATE INDEX IF NOT EXISTS idx_github_team_policies_group_id
    ON public.github_team_policies(group_id);

ALTER TABLE public.github_team_policies ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Group members can view github team policies"
    ON public.github_team_policies FOR SELECT
    USING (public.is_group_member(group_id) OR public.is_admin());

CREATE POLICY "Admins can manage github team policies"
    ON public.github_team_policies FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());
