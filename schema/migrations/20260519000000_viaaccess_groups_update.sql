-- ViaAccess: update groups table
-- Rename owner_id -> created_by (audit metadata only, no permission weight)
-- Add status column for group lifecycle (ACTIVE | DELETING | ARCHIVED)
-- Drop is_group_owner() RLS function — replaced by TenantAsset policy checks

ALTER TABLE public.groups
    RENAME COLUMN owner_id TO created_by;

ALTER TABLE public.groups
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'DELETING', 'ARCHIVED'));

-- Drop the owner-based RLS policies that referenced owner_id
DROP POLICY IF EXISTS "Users can view groups they are members of" ON public.groups;
DROP POLICY IF EXISTS "Users can create their own groups" ON public.groups;
DROP POLICY IF EXISTS "Group owners can update their groups" ON public.groups;
DROP POLICY IF EXISTS "Group owners can delete their groups" ON public.groups;
DROP POLICY IF EXISTS "Group owners can add members" ON public.group_members;
DROP POLICY IF EXISTS "Group owners and members can remove memberships" ON public.group_members;

-- Drop the is_group_owner helper — no longer used
DROP FUNCTION IF EXISTS public.is_group_owner(UUID);

-- Recreate group RLS policies without owner semantics
-- Visibility: members can see the group; creator can always see it (covers RETURNING after INSERT)
CREATE POLICY "Group members can view groups"
    ON public.groups FOR SELECT
    USING (public.is_group_member(id) OR public.is_admin() OR created_by = auth.uid());

-- Any authenticated user can create a group (they become creator via trigger)
-- TenantAsset EDITOR/OWNER enforcement for membership management lives in the resolver layer
CREATE POLICY "Authenticated users can create groups"
    ON public.groups FOR INSERT
    WITH CHECK (auth.uid() IS NOT NULL);

CREATE POLICY "Admins can update groups"
    ON public.groups FOR UPDATE
    USING (public.is_admin());

CREATE POLICY "Admins can delete groups"
    ON public.groups FOR DELETE
    USING (public.is_admin());

-- group_members: insert/delete gated by is_admin()
CREATE POLICY "Admins can add group members"
    ON public.group_members FOR INSERT
    WITH CHECK (public.is_admin());

CREATE POLICY "Members can remove themselves or admins can remove anyone"
    ON public.group_members FOR DELETE
    USING (user_id = auth.uid() OR public.is_admin());

-- Update add_owner_to_group trigger to use created_by
CREATE OR REPLACE FUNCTION public.add_owner_to_group()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.group_members (group_id, user_id)
    VALUES (NEW.id, NEW.created_by)
    ON CONFLICT (group_id, user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
