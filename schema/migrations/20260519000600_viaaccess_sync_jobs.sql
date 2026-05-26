-- ViaAccess: sync jobs queue
-- Tracks all async reconciliation jobs. The worker polls this table.

CREATE TABLE IF NOT EXISTS public.sync_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES public.assets(id) ON DELETE CASCADE,
    -- asset_type and tenant_name are denormalized snapshots copied from assets at
    -- enqueue time. They avoid a join in the worker hot path and preserve intent
    -- if the asset row is later changed. They must not diverge from assets.
    asset_type TEXT NOT NULL
        CHECK (asset_type IN ('GITHUB_REPO', 'GITHUB_TEAM', 'ASANA_PROJECT', 'ASANA_PORTFOLIO')),
    tenant_name TEXT NOT NULL,
    action TEXT NOT NULL
        CHECK (action IN ('RECONCILE_ASSET', 'PREVIEW_ASSET', 'IMPORT_ASSET')),
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'BLOCKED', 'EXHAUSTED', 'ABANDONED')),
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    plan_summary TEXT,   -- populated after a SUCCEEDED PREVIEW_ASSET job
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_sync_jobs_asset_id ON public.sync_jobs(asset_id);
CREATE INDEX IF NOT EXISTS idx_sync_jobs_status   ON public.sync_jobs(status);

-- Only one active RECONCILE_ASSET per asset at a time
CREATE UNIQUE INDEX idx_sync_jobs_one_active_reconcile
    ON public.sync_jobs(asset_id)
    WHERE status IN ('PENDING', 'RUNNING') AND action = 'RECONCILE_ASSET';

ALTER TABLE public.sync_jobs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can view sync jobs"
    ON public.sync_jobs FOR SELECT
    USING (auth.uid() IS NOT NULL);

CREATE POLICY "Admins can manage sync jobs"
    ON public.sync_jobs FOR ALL
    USING (public.is_admin())
    WITH CHECK (public.is_admin());
