-- ViaAccess: audit event log
-- Append-only table. No updates or deletes permitted via RLS.

CREATE TABLE IF NOT EXISTS public.audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    before JSONB,
    after JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_events_actor_id    ON public.audit_events(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_target_id   ON public.audit_events(target_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_occurred_at ON public.audit_events(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_event_type  ON public.audit_events(event_type);

ALTER TABLE public.audit_events ENABLE ROW LEVEL SECURITY;

-- Any authenticated user can insert (the resolver writes on their behalf)
CREATE POLICY "Authenticated users can insert audit events"
    ON public.audit_events FOR INSERT
    WITH CHECK (auth.uid() IS NOT NULL);

-- Users can read their own audit events; admins can read all
CREATE POLICY "Users can view own audit events"
    ON public.audit_events FOR SELECT
    USING (actor_id = auth.uid() OR public.is_admin());

-- No UPDATE or DELETE policies — table is append-only
