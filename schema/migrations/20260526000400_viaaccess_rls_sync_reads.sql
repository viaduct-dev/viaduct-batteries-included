-- ViaAccess: widen SELECT policies for sync worker reads
--
-- The sync worker runs under the enqueueing user's token and reads:
--   • *_policies tables  — to build the desired-state plan
--   • group_members      — to enumerate identities for preflight
--
-- Currently those SELECT policies are gated on group membership.
-- A tenant EDITOR who enqueued the job may not belong to every group
-- that holds a policy on the asset, so some policy/member rows are
-- invisible → incomplete sync plan.
--
-- Fix: tenant EDITOR on the relevant tenant may read all policies and
-- members for their tenant's assets.  Group membership view is
-- unchanged for regular users (own groups only).

-- ─────────────────────────────────────────────────────────────
-- group_members — add tenant EDITOR read
-- ─────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Users can view group memberships they are part of" ON public.group_members;

CREATE POLICY "Users and tenant editors can view group memberships"
    ON public.group_members FOR SELECT
    USING (
        public.is_admin()
        OR user_id = auth.uid()
        OR public.is_group_member(group_id)
        OR public.has_tenant_permission('default', 'EDITOR')
    );

-- ─────────────────────────────────────────────────────────────
-- github_repo_policies — add github-tenant EDITOR read
-- ─────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Group members can view github repo policies" ON public.github_repo_policies;

CREATE POLICY "Group members and github tenant editors can view github repo policies"
    ON public.github_repo_policies FOR SELECT
    USING (
        public.is_admin()
        OR public.is_group_member(group_id)
        OR public.has_tenant_permission('github', 'EDITOR')
    );

-- ─────────────────────────────────────────────────────────────
-- github_team_policies — add github-tenant EDITOR read
-- ─────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Group members can view github team policies" ON public.github_team_policies;

CREATE POLICY "Group members and github tenant editors can view github team policies"
    ON public.github_team_policies FOR SELECT
    USING (
        public.is_admin()
        OR public.is_group_member(group_id)
        OR public.has_tenant_permission('github', 'EDITOR')
    );

-- ─────────────────────────────────────────────────────────────
-- asana_project_policies — add asana-tenant EDITOR read
-- ─────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Group members can view asana project policies" ON public.asana_project_policies;

CREATE POLICY "Group members and asana tenant editors can view asana project policies"
    ON public.asana_project_policies FOR SELECT
    USING (
        public.is_admin()
        OR public.is_group_member(group_id)
        OR public.has_tenant_permission('asana', 'EDITOR')
    );

-- ─────────────────────────────────────────────────────────────
-- asana_portfolio_policies — add asana-tenant EDITOR read
-- ─────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Group members can view asana portfolio policies" ON public.asana_portfolio_policies;

CREATE POLICY "Group members and asana tenant editors can view asana portfolio policies"
    ON public.asana_portfolio_policies FOR SELECT
    USING (
        public.is_admin()
        OR public.is_group_member(group_id)
        OR public.has_tenant_permission('asana', 'EDITOR')
    );
