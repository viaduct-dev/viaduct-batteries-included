-- ViaAccess: fix RLS for sync worker writes and identity import
--
-- Two remaining RLS gaps:
--
-- 1. sync_jobs — the worker updates status/error/plan_summary on jobs it runs,
--    but writes are admin-only. The worker runs with the enqueueing user's token,
--    so non-admin tenant editors can enqueue but the worker then fails to update.
--    Fix: tenant editors may INSERT (enqueueing is covered by existing migration
--    20260521000000) and UPDATE their own jobs. We key on the asset's tenant to
--    scope the permission check rather than requiring global admin.
--
-- 2. external_identities — setExternalIdentity / setAsanaIdentity / import
--    resolvers already check tenant EDITOR, but the DB write is admin-only.
--    Additionally, the current SELECT policy (own-only) breaks the import flow,
--    which reads all identities for a provider to detect already-mapped accounts.
--    Fix: tenant EDITOR may read all identities for their tenant's provider, and
--    insert/update identities for any user.

-- ─────────────────────────────────────────────────────────────
-- 1. sync_jobs
-- ─────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Admins can manage sync jobs" ON public.sync_jobs;

-- INSERT: handled by migration 20260521000000 (tenant EDITOR gated on tenant_name)
-- Restate it here cleanly in case the earlier migration is absent in test environments.
CREATE POLICY "Tenant editors can enqueue sync jobs"
    ON public.sync_jobs FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission(tenant_name, 'EDITOR')
    );

-- UPDATE: worker updates status, attempt_count, last_error, plan_summary, completed_at.
-- The worker runs with the token of the user who enqueued the job, so we allow any
-- tenant editor to update jobs for assets in their tenant.
CREATE POLICY "Tenant editors can update sync jobs"
    ON public.sync_jobs FOR UPDATE
    USING (
        public.is_admin()
        OR public.has_tenant_permission(tenant_name, 'EDITOR')
    )
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission(tenant_name, 'EDITOR')
    );

-- DELETE: admin only (jobs are kept for audit purposes; editors never delete them)
CREATE POLICY "Admins can delete sync jobs"
    ON public.sync_jobs FOR DELETE
    USING (public.is_admin());

-- ─────────────────────────────────────────────────────────────
-- 2. external_identities
-- ─────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Users can view own external identities" ON public.external_identities;
DROP POLICY IF EXISTS "Admins can manage external identities" ON public.external_identities;

-- SELECT: own row always visible; tenant editors can see all identities for their
-- tenant's provider (needed by import dedup check and sync pre-flight).
-- Provider → tenant mapping: github → github, asana → asana, everything else → own only.
CREATE POLICY "Users and tenant editors can view external identities"
    ON public.external_identities FOR SELECT
    USING (
        user_id = auth.uid()
        OR public.is_admin()
        OR (provider = 'github' AND public.has_tenant_permission('github', 'EDITOR'))
        OR (provider = 'asana'  AND public.has_tenant_permission('asana',  'EDITOR'))
    );

-- INSERT / UPDATE (upsert): tenant EDITOR for the provider's tenant may create or
-- update any user's identity. The resolver already checks this, but RLS must agree.
CREATE POLICY "Tenant editors can upsert external identities"
    ON public.external_identities FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR (provider = 'github' AND public.has_tenant_permission('github', 'EDITOR'))
        OR (provider = 'asana'  AND public.has_tenant_permission('asana',  'EDITOR'))
    );

CREATE POLICY "Tenant editors can update external identities"
    ON public.external_identities FOR UPDATE
    USING (
        public.is_admin()
        OR (provider = 'github' AND public.has_tenant_permission('github', 'EDITOR'))
        OR (provider = 'asana'  AND public.has_tenant_permission('asana',  'EDITOR'))
    )
    WITH CHECK (
        public.is_admin()
        OR (provider = 'github' AND public.has_tenant_permission('github', 'EDITOR'))
        OR (provider = 'asana'  AND public.has_tenant_permission('asana',  'EDITOR'))
    );

-- DELETE: admin only
CREATE POLICY "Admins can delete external identities"
    ON public.external_identities FOR DELETE
    USING (public.is_admin());
