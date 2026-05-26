-- ViaAccess: align RLS with resolver auth model
--
-- Four tables had admin-only write policies that conflicted with resolver-level
-- tenant permission checks. Resolvers correctly gate writes on has_tenant_permission(),
-- but the DB writes were still blocked for non-admin tenant owners/editors.
--
-- This migration replaces the admin-only INSERT/DELETE/UPDATE policies on:
--   1. tenant_asset_policies  (OWNER on that tenant may grant/revoke)
--   2. assets + concrete asset tables  (EDITOR on that asset's tenant may register)
--   3. group_members  (EDITOR on 'default' tenant may add/remove members)
--
-- SELECT policies are also narrowed: assets and concrete asset tables now require
-- VIEWER-or-higher on the asset's tenant, or admin, rather than any authenticated user.
-- This closes the "asset discovery broader than design allows" gap.

-- ─────────────────────────────────────────────────────────────
-- 1. tenant_asset_policies — OWNER on that tenant may insert/delete
-- ─────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Admins can manage tenant asset policies" ON public.tenant_asset_policies;

CREATE POLICY "Tenant owners can manage tenant asset policies"
    ON public.tenant_asset_policies FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission(
            (SELECT ta.tenant_name FROM public.tenant_assets ta WHERE ta.id = tenant_asset_id),
            'OWNER'
        )
    );

CREATE POLICY "Tenant owners can delete tenant asset policies"
    ON public.tenant_asset_policies FOR DELETE
    USING (
        public.is_admin()
        OR public.has_tenant_permission(
            (SELECT ta.tenant_name FROM public.tenant_assets ta WHERE ta.id = tenant_asset_id),
            'OWNER'
        )
    );

-- ─────────────────────────────────────────────────────────────
-- 2. assets — EDITOR on that asset's tenant may insert
--    SELECT narrowed: requires VIEWER-or-higher on the asset's tenant
-- ─────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Authenticated users can view assets" ON public.assets;
DROP POLICY IF EXISTS "Admins can manage assets" ON public.assets;

CREATE POLICY "Tenant viewers can see assets"
    ON public.assets FOR SELECT
    USING (
        public.is_admin()
        OR public.has_tenant_permission(tenant_name, 'VIEWER')
    );

CREATE POLICY "Tenant editors can register assets"
    ON public.assets FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission(tenant_name, 'EDITOR')
    );

CREATE POLICY "Admins can update or delete assets"
    ON public.assets FOR UPDATE
    USING (public.is_admin());

CREATE POLICY "Tenant editors can delete assets"
    ON public.assets FOR DELETE
    USING (
        public.is_admin()
        OR public.has_tenant_permission(tenant_name, 'EDITOR')
    );

-- ─────────────────────────────────────────────────────────────
-- 3. concrete asset tables (github_repo_assets, github_team_assets,
--    asana_project_assets, asana_portfolio_assets)
--    — SELECT narrowed to tenant viewers; INSERT opened to tenant editors
-- ─────────────────────────────────────────────────────────────

-- github_repo_assets
DROP POLICY IF EXISTS "Authenticated users can view github repo assets" ON public.github_repo_assets;
DROP POLICY IF EXISTS "Admins can manage github repo assets" ON public.github_repo_assets;

CREATE POLICY "Tenant viewers can see github repo assets"
    ON public.github_repo_assets FOR SELECT
    USING (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = github_repo_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'VIEWER')
        )
    );

CREATE POLICY "Tenant editors can register github repo assets"
    ON public.github_repo_assets FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = github_repo_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'EDITOR')
        )
    );

CREATE POLICY "Tenant editors can delete github repo assets"
    ON public.github_repo_assets FOR DELETE
    USING (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = github_repo_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'EDITOR')
        )
    );

-- github_team_assets
DROP POLICY IF EXISTS "Authenticated users can view github team assets" ON public.github_team_assets;
DROP POLICY IF EXISTS "Admins can manage github team assets" ON public.github_team_assets;

CREATE POLICY "Tenant viewers can see github team assets"
    ON public.github_team_assets FOR SELECT
    USING (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = github_team_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'VIEWER')
        )
    );

CREATE POLICY "Tenant editors can register github team assets"
    ON public.github_team_assets FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = github_team_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'EDITOR')
        )
    );

CREATE POLICY "Tenant editors can delete github team assets"
    ON public.github_team_assets FOR DELETE
    USING (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = github_team_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'EDITOR')
        )
    );

-- asana_project_assets
DROP POLICY IF EXISTS "Authenticated users can view asana project assets" ON public.asana_project_assets;
DROP POLICY IF EXISTS "Admins can manage asana project assets" ON public.asana_project_assets;

CREATE POLICY "Tenant viewers can see asana project assets"
    ON public.asana_project_assets FOR SELECT
    USING (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = asana_project_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'VIEWER')
        )
    );

CREATE POLICY "Tenant editors can register asana project assets"
    ON public.asana_project_assets FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = asana_project_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'EDITOR')
        )
    );

CREATE POLICY "Tenant editors can delete asana project assets"
    ON public.asana_project_assets FOR DELETE
    USING (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = asana_project_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'EDITOR')
        )
    );

-- asana_portfolio_assets
DROP POLICY IF EXISTS "Authenticated users can view asana portfolio assets" ON public.asana_portfolio_assets;
DROP POLICY IF EXISTS "Admins can manage asana portfolio assets" ON public.asana_portfolio_assets;

CREATE POLICY "Tenant viewers can see asana portfolio assets"
    ON public.asana_portfolio_assets FOR SELECT
    USING (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = asana_portfolio_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'VIEWER')
        )
    );

CREATE POLICY "Tenant editors can register asana portfolio assets"
    ON public.asana_portfolio_assets FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = asana_portfolio_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'EDITOR')
        )
    );

CREATE POLICY "Tenant editors can delete asana portfolio assets"
    ON public.asana_portfolio_assets FOR DELETE
    USING (
        public.is_admin()
        OR EXISTS (
            SELECT 1 FROM public.assets a
            WHERE a.id = asana_portfolio_assets.id
              AND public.has_tenant_permission(a.tenant_name, 'EDITOR')
        )
    );

-- ─────────────────────────────────────────────────────────────
-- 4. group_members — default-tenant EDITOR may add/remove members
-- ─────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Admins can add group members" ON public.group_members;
DROP POLICY IF EXISTS "Members can remove themselves or admins can remove anyone" ON public.group_members;

CREATE POLICY "Default tenant editors can add group members"
    ON public.group_members FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
    );

CREATE POLICY "Default tenant editors or self can remove group members"
    ON public.group_members FOR DELETE
    USING (
        public.is_admin()
        OR user_id = auth.uid()
        OR public.has_tenant_permission('default', 'EDITOR')
    );
