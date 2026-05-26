-- ViaAccess: align groups INSERT policy with resolver auth
--
-- createGroup now requires TenantAsset(default): EDITOR at the resolver layer.
-- The RLS INSERT policy previously allowed any authenticated user, which would
-- let a direct DB client bypass the resolver check. Tighten to match.

DROP POLICY IF EXISTS "Authenticated users can create groups" ON public.groups;

CREATE POLICY "Tenant editors can create groups"
    ON public.groups FOR INSERT
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
    );

-- UPDATE: also open to default-tenant EDITOR so archiveGroup can set status.
-- The existing "Admins can update groups" policy is replaced.
DROP POLICY IF EXISTS "Admins can update groups" ON public.groups;

CREATE POLICY "Tenant editors can update groups"
    ON public.groups FOR UPDATE
    USING (
        public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
    )
    WITH CHECK (
        public.is_admin()
        OR public.has_tenant_permission('default', 'EDITOR')
    );
