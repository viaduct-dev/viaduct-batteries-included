-- EXAMPLE: Checklist Items Table with Group-Based Access Control
--
-- This migration demonstrates the complete pattern for adding a resource table
-- with group-based access control and admin bypass functionality.
--
-- KEY CONCEPTS DEMONSTRATED:
-- 1. Table Structure
--    - Primary key (UUID with default gen_random_uuid())
--    - Foreign key to groups table (group_id)
--    - Foreign key to auth.users (user_id for tracking creator)
--    - Timestamps (created_at, updated_at with triggers)
--    - Resource-specific fields (title, completed)
--
-- 2. Row-Level Security (RLS)
--    - Enabled on the table
--    - Policies for SELECT, INSERT, UPDATE, DELETE
--    - Each policy checks: group membership OR admin status
--    - Backward compatibility for legacy items (group_id IS NULL)
--
-- 3. Helper Functions
--    - is_group_member(group_id) - checks membership
--    - is_admin() - checks admin status from JWT
--    - Both are defined in core migrations
--
-- 4. Indexes
--    - Foreign key indexes for performance
--    - Composite indexes for common query patterns
--
-- 5. Triggers
--    - Auto-update updated_at timestamp
--    - Can add custom business logic triggers
--
-- NOTE: This migration has a future timestamp (99999999999999) and won't run automatically.
-- It's meant as a reference implementation, not active database code.
--
-- TO USE THIS EXAMPLE:
-- 1. Copy this file to supabase/migrations/
-- 2. Rename with current timestamp: YYYYMMDDHHMMSS_your_resource.sql
-- 3. Uncomment the SQL below
-- 4. Customize table name, columns, and constraints for your resource
-- 5. Run: supabase db reset (or supabase db push in production)
--
-- PREREQUISITES:
-- - Core auth migrations (is_admin function)
-- - Core groups migration (groups table, is_group_member function)
-- - User authentication system
--
-- RELATED FILES:
-- - GraphQL Schema: backend/src/main/viaduct/schema/examples/checklist/ChecklistItem.graphqls.example
-- - Resolvers: backend/src/main/kotlin/com/viaduct/examples/checklist/resolvers/
-- - Frontend: src/components/examples/checklist/

-- ============================================================================
-- TABLE CREATION
-- ============================================================================

-- Create the checklist_items table
-- This represents a resource type that belongs to groups
--
-- CUSTOMIZE THIS: Change table name and columns for your resource type
-- Common patterns:
-- - tasks (title, status, priority, due_date, assigned_to)
-- - notes (title, content, category, tags)
-- - projects (name, description, start_date, end_date, status)
-- - documents (title, file_url, mime_type, size_bytes)

-- CREATE TABLE IF NOT EXISTS public.checklist_items (
--     -- Primary key (standard pattern)
--     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--
--     -- Foreign key to groups (REQUIRED for group-based access control)
--     -- ON DELETE CASCADE ensures items are deleted when group is deleted
--     group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
--
--     -- Foreign key to user (tracks who created this item)
--     -- Useful for attribution, filtering, and legacy compatibility
--     user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
--
--     -- Resource-specific fields (CUSTOMIZE THESE)
--     title TEXT NOT NULL,
--     completed BOOLEAN NOT NULL DEFAULT false,
--
--     -- Timestamps (standard pattern)
--     created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
--     updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
-- );

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Create indexes for foreign keys and common query patterns
-- Indexes improve query performance but add overhead to writes
-- Add indexes for columns frequently used in WHERE, JOIN, or ORDER BY clauses

-- CREATE INDEX IF NOT EXISTS idx_checklist_items_group_id
--     ON public.checklist_items(group_id);

-- CREATE INDEX IF NOT EXISTS idx_checklist_items_user_id
--     ON public.checklist_items(user_id);

-- CREATE INDEX IF NOT EXISTS idx_checklist_items_created_at
--     ON public.checklist_items(created_at DESC);

-- Example composite index for common query: items by group, ordered by creation
-- CREATE INDEX IF NOT EXISTS idx_checklist_items_group_created
--     ON public.checklist_items(group_id, created_at DESC);

-- ============================================================================
-- ROW-LEVEL SECURITY (RLS)
-- ============================================================================

-- Enable RLS on the table
-- This is REQUIRED for the security model to work

-- ALTER TABLE public.checklist_items ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- RLS POLICIES
-- ============================================================================

-- Policy Pattern: Users can access resources if they are members of the group
-- OR if they are admins (bypass). Each CRUD operation needs its own policy.

-- SELECT Policy: Who can view items
-- Users can view items if:
-- 1. They are a member of the group that owns the item, OR
-- 2. They are an admin (bypass for admin panel/support)
--
-- Note: For legacy items without group_id, you might add:
-- OR (group_id IS NULL AND auth.uid() = user_id)

-- CREATE POLICY "Users can view items from their groups"
--     ON public.checklist_items
--     FOR SELECT
--     USING (
--         public.is_group_member(group_id)
--         OR public.is_admin()
--     );

-- INSERT Policy: Who can create items
-- Users can create items if they are members of the target group
-- The WITH CHECK clause validates data being inserted

-- CREATE POLICY "Users can create items in their groups"
--     ON public.checklist_items
--     FOR INSERT
--     WITH CHECK (
--         public.is_group_member(group_id)
--         OR public.is_admin()
--     );

-- UPDATE Policy: Who can modify items
-- Users can update items if they are members of the group
-- The USING clause determines which rows can be updated

-- CREATE POLICY "Users can update items in their groups"
--     ON public.checklist_items
--     FOR UPDATE
--     USING (
--         public.is_group_member(group_id)
--         OR public.is_admin()
--     );

-- DELETE Policy: Who can delete items
-- Users can delete items if they are members of the group
-- Consider more restrictive policies (e.g., owner-only delete):
-- USING (user_id = auth.uid() OR public.is_admin())

-- CREATE POLICY "Users can delete items in their groups"
--     ON public.checklist_items
--     FOR DELETE
--     USING (
--         public.is_group_member(group_id)
--         OR public.is_admin()
--     );

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- Trigger to automatically update the updated_at timestamp
-- This is a common pattern for tracking when records were last modified

-- CREATE OR REPLACE FUNCTION update_checklist_items_updated_at()
-- RETURNS TRIGGER AS $$
-- BEGIN
--     NEW.updated_at = now();
--     RETURN NEW;
-- END;
-- $$ LANGUAGE plpgsql;

-- CREATE TRIGGER update_checklist_items_timestamp
--     BEFORE UPDATE ON public.checklist_items
--     FOR EACH ROW
--     EXECUTE FUNCTION update_checklist_items_updated_at();

-- ============================================================================
-- ADDITIONAL TRIGGER EXAMPLES
-- ============================================================================

-- Example: Validate business rules before insert/update
-- Uncomment and customize as needed

-- CREATE OR REPLACE FUNCTION validate_checklist_item()
-- RETURNS TRIGGER AS $$
-- BEGIN
--     -- Example: Ensure title is not empty after trimming
--     IF trim(NEW.title) = '' THEN
--         RAISE EXCEPTION 'Title cannot be empty';
--     END IF;
--
--     -- Example: Ensure title length is reasonable
--     IF length(NEW.title) > 500 THEN
--         RAISE EXCEPTION 'Title is too long (max 500 characters)';
--     END IF;
--
--     RETURN NEW;
-- END;
-- $$ LANGUAGE plpgsql;

-- CREATE TRIGGER validate_checklist_item_trigger
--     BEFORE INSERT OR UPDATE ON public.checklist_items
--     FOR EACH ROW
--     EXECUTE FUNCTION validate_checklist_item();

-- ============================================================================
-- IMPLEMENTATION NOTES
-- ============================================================================

-- SECURITY MODEL:
-- 1. RLS policies enforce access at database level (Supabase/Postgres)
-- 2. Backend policy executors provide additional checks (Viaduct)
-- 3. Frontend UI guards provide user experience (React)
-- All three layers work together for defense in depth

-- ADMIN BYPASS:
-- - Admins can access all resources regardless of group membership
-- - is_admin() reads from JWT token (app_metadata.is_admin)
-- - Useful for admin panels, support, and data migrations
-- - Consider audit logging for admin actions

-- PERFORMANCE:
-- - Add indexes for columns used in WHERE clauses
-- - Use composite indexes for multi-column queries
-- - Monitor slow queries with pg_stat_statements
-- - Consider partitioning for very large tables

-- BACKWARD COMPATIBILITY:
-- - If migrating from non-group items, make group_id nullable initially
-- - Add data migration to assign items to default groups
-- - Update policies to handle both grouped and legacy items
-- - Eventually make group_id required (NOT NULL) after migration

-- TESTING:
-- - Test RLS policies with different user contexts
-- - Test admin bypass functionality
-- - Test group membership changes affect access
-- - Test cascade deletes work correctly

-- MIGRATION STRATEGY:
-- 1. Development: supabase db reset (destructive, recreates database)
-- 2. Production: supabase db push (applies only new migrations)
-- 3. Rollback: Create down migration or use database backups
-- 4. Always test migrations in staging environment first!
