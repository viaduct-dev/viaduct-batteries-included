-- ViaAccess Phase 4: Approval workflows as a policy state machine
--
-- access_requests: tracks self-service access requests from REQUESTER-level users.
-- Approval atomically creates/updates the live policy and enqueues RECONCILE_ASSET.
-- assets.requestable: opt-in flag so REQUESTER can discover approachable assets
-- without gaining full VIEWER visibility.
-- tenant_asset_policies: extend permission CHECK to allow 'REQUESTER'.

-- ─────────────────────────────────────────────────────────────
-- 1. Add requestable flag to assets
-- ─────────────────────────────────────────────────────────────

ALTER TABLE public.assets
    ADD COLUMN IF NOT EXISTS requestable BOOLEAN NOT NULL DEFAULT FALSE;

-- ─────────────────────────────────────────────────────────────
-- 2. Extend tenant_asset_policies to accept REQUESTER
-- ─────────────────────────────────────────────────────────────

ALTER TABLE public.tenant_asset_policies
    DROP CONSTRAINT IF EXISTS tenant_asset_policies_permission_check;

ALTER TABLE public.tenant_asset_policies
    ADD CONSTRAINT tenant_asset_policies_permission_check
    CHECK (permission IN ('REQUESTER', 'VIEWER', 'EDITOR', 'OWNER'));

-- ─────────────────────────────────────────────────────────────
-- 3. Extend has_tenant_permission to include REQUESTER rank
-- ─────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.has_tenant_permission(tenant TEXT, required_permission TEXT)
RETURNS BOOLEAN AS $$
DECLARE
    permission_rank INT;
    required_rank INT;
BEGIN
    SELECT CASE
        WHEN p.permission = 'REQUESTER' THEN 1
        WHEN p.permission = 'VIEWER'    THEN 2
        WHEN p.permission = 'EDITOR'    THEN 3
        WHEN p.permission = 'OWNER'     THEN 4
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
        WHEN 'REQUESTER' THEN 1
        WHEN 'VIEWER'    THEN 2
        WHEN 'EDITOR'    THEN 3
        WHEN 'OWNER'     THEN 4
        ELSE 99
    END;

    RETURN COALESCE(permission_rank, 0) >= required_rank;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- ─────────────────────────────────────────────────────────────
-- 4. access_requests table
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.access_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_name TEXT NOT NULL,
    asset_id UUID NOT NULL REFERENCES public.assets(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    requested_permission TEXT NOT NULL CHECK (requested_permission IN ('VIEWER', 'EDITOR', 'OWNER')),
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED')),
    requested_by UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reviewed_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewer_note TEXT
);

CREATE INDEX IF NOT EXISTS idx_access_requests_asset_id     ON public.access_requests(asset_id);
CREATE INDEX IF NOT EXISTS idx_access_requests_group_id     ON public.access_requests(group_id);
CREATE INDEX IF NOT EXISTS idx_access_requests_requested_by ON public.access_requests(requested_by);
CREATE INDEX IF NOT EXISTS idx_access_requests_status       ON public.access_requests(status);

ALTER TABLE public.access_requests ENABLE ROW LEVEL SECURITY;

-- REQUESTER can create requests (resolver enforces requestable flag check)
CREATE POLICY "Authenticated users can create access requests"
    ON public.access_requests FOR INSERT
    WITH CHECK (auth.uid() IS NOT NULL AND requested_by = auth.uid());

-- Requester sees own requests; EDITOR/OWNER sees all pending for tenants they administer
CREATE POLICY "Requesters see own, editors see all for their tenant"
    ON public.access_requests FOR SELECT
    USING (
        requested_by = auth.uid()
        OR public.is_admin()
        OR public.has_tenant_permission(tenant_name, 'EDITOR')
    );

-- Only EDITOR/OWNER/admin may update (approve/reject/cancel)
CREATE POLICY "Editors can review access requests"
    ON public.access_requests FOR UPDATE
    USING (
        public.is_admin()
        OR public.has_tenant_permission(tenant_name, 'EDITOR')
        OR (requested_by = auth.uid() AND status = 'PENDING')
    )
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission(tenant_name, 'EDITOR')
        OR (requested_by = auth.uid() AND status = 'CANCELED')
    );

-- ─────────────────────────────────────────────────────────────
-- 5. RLS: allow any authenticated user to read requestable assets
-- ─────────────────────────────────────────────────────────────

CREATE POLICY "Anyone can see requestable assets"
    ON public.assets FOR SELECT
    USING (requestable = true AND auth.uid() IS NOT NULL);
