-- Remove the VIEWER/EDITOR/OWNER constraint from access_requests.requested_permission.
-- Provider permissions are provider-specific (READ/WRITE/ADMIN for GitHub repos, etc.)
-- and do not map to the ViaAccess tenant governance level vocabulary.
-- The resolver enforces validity by checking against availablePermissions at request time.

ALTER TABLE public.access_requests
    DROP CONSTRAINT IF EXISTS access_requests_requested_permission_check;
