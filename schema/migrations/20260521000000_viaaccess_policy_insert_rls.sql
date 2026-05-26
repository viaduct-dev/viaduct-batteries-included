-- Allow tenant editors and owners to insert/delete policies for their tenant.
-- The policies table stores policy_type IN ('GITHUB_REPO','GITHUB_TEAM','ASANA_PROJECT','ASANA_PORTFOLIO').
-- We map each policy_type to its tenant name so has_tenant_permission can gate the write.

CREATE OR REPLACE FUNCTION public.policy_type_to_tenant(policy_type TEXT)
RETURNS TEXT AS $$
BEGIN
    RETURN CASE
        WHEN policy_type IN ('GITHUB_REPO', 'GITHUB_TEAM') THEN 'github'
        WHEN policy_type IN ('ASANA_PROJECT', 'ASANA_PORTFOLIO') THEN 'asana'
        ELSE NULL
    END;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Tenant editors/owners can insert policies for their tenant
CREATE POLICY "Tenant editors can insert policies"
    ON public.policies FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission(public.policy_type_to_tenant(policy_type), 'EDITOR')
    );

-- Tenant editors/owners can delete policies for their tenant
CREATE POLICY "Tenant editors can delete policies"
    ON public.policies FOR DELETE
    USING (
        public.is_admin()
        OR public.has_tenant_permission(public.policy_type_to_tenant(policy_type), 'EDITOR')
    );

-- GitHub repo policies: allow tenant editors to insert/delete
CREATE POLICY "Tenant editors can insert github repo policies"
    ON public.github_repo_policies FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission('github', 'EDITOR')
    );

CREATE POLICY "Tenant editors can delete github repo policies"
    ON public.github_repo_policies FOR DELETE
    USING (
        public.is_admin()
        OR public.has_tenant_permission('github', 'EDITOR')
    );

-- GitHub team policies
CREATE POLICY "Tenant editors can insert github team policies"
    ON public.github_team_policies FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission('github', 'EDITOR')
    );

CREATE POLICY "Tenant editors can delete github team policies"
    ON public.github_team_policies FOR DELETE
    USING (
        public.is_admin()
        OR public.has_tenant_permission('github', 'EDITOR')
    );

-- Asana project policies
CREATE POLICY "Tenant editors can insert asana project policies"
    ON public.asana_project_policies FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission('asana', 'EDITOR')
    );

CREATE POLICY "Tenant editors can delete asana project policies"
    ON public.asana_project_policies FOR DELETE
    USING (
        public.is_admin()
        OR public.has_tenant_permission('asana', 'EDITOR')
    );

-- Sync jobs: tenant editors can enqueue sync jobs for their tenant
CREATE POLICY "Tenant editors can insert sync jobs"
    ON public.sync_jobs FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission(tenant_name, 'EDITOR')
    );

-- Asana portfolio policies
CREATE POLICY "Tenant editors can insert asana portfolio policies"
    ON public.asana_portfolio_policies FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission('asana', 'EDITOR')
    );

CREATE POLICY "Tenant editors can delete asana portfolio policies"
    ON public.asana_portfolio_policies FOR DELETE
    USING (
        public.is_admin()
        OR public.has_tenant_permission('asana', 'EDITOR')
    );
