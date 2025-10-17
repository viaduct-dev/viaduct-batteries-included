-- Add checkbox groups functionality
-- Users can create groups and share checklist items via groups
-- Users can only see items from groups they belong to

-- Create checkbox_groups table
CREATE TABLE IF NOT EXISTS public.checkbox_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    description TEXT,
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create group_members table (many-to-many relationship)
CREATE TABLE IF NOT EXISTS public.group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.checkbox_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(group_id, user_id)
);

-- Add group_id to checklist_items
ALTER TABLE public.checklist_items
    ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES public.checkbox_groups(id) ON DELETE CASCADE;

-- Create index for performance
CREATE INDEX IF NOT EXISTS idx_checklist_items_group_id ON public.checklist_items(group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user_id ON public.group_members(user_id);
CREATE INDEX IF NOT EXISTS idx_group_members_group_id ON public.group_members(group_id);

-- Helper function to check if a user is a member of a group
CREATE OR REPLACE FUNCTION public.is_group_member(group_uuid UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1
        FROM public.group_members
        WHERE group_id = group_uuid
        AND user_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Helper function to check if a user owns a group
CREATE OR REPLACE FUNCTION public.is_group_owner(group_uuid UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1
        FROM public.checkbox_groups
        WHERE id = group_uuid
        AND owner_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Update trigger for checkbox_groups
CREATE OR REPLACE FUNCTION update_checkbox_groups_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_checkbox_groups_timestamp
    BEFORE UPDATE ON public.checkbox_groups
    FOR EACH ROW
    EXECUTE FUNCTION update_checkbox_groups_updated_at();

-- Enable RLS on new tables
ALTER TABLE public.checkbox_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;

-- RLS Policies for checkbox_groups
CREATE POLICY "Users can view groups they are members of"
    ON public.checkbox_groups
    FOR SELECT
    USING (public.is_group_member(id) OR owner_id = auth.uid());

CREATE POLICY "Users can create their own groups"
    ON public.checkbox_groups
    FOR INSERT
    WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "Group owners can update their groups"
    ON public.checkbox_groups
    FOR UPDATE
    USING (owner_id = auth.uid());

CREATE POLICY "Group owners can delete their groups"
    ON public.checkbox_groups
    FOR DELETE
    USING (owner_id = auth.uid());

-- RLS Policies for group_members
CREATE POLICY "Users can view group memberships they are part of"
    ON public.group_members
    FOR SELECT
    USING (public.is_group_member(group_id) OR user_id = auth.uid());

CREATE POLICY "Group owners can add members"
    ON public.group_members
    FOR INSERT
    WITH CHECK (public.is_group_owner(group_id));

CREATE POLICY "Group owners and members can remove memberships"
    ON public.group_members
    FOR DELETE
    USING (public.is_group_owner(group_id) OR user_id = auth.uid());

-- Update RLS policies for checklist_items to support groups
-- Drop existing policies
DROP POLICY IF EXISTS "Users can view their own checklist items" ON public.checklist_items;
DROP POLICY IF EXISTS "Users and admins can create checklist items" ON public.checklist_items;
DROP POLICY IF EXISTS "Users and admins can update checklist items" ON public.checklist_items;
DROP POLICY IF EXISTS "Users and admins can delete checklist items" ON public.checklist_items;

-- Recreate policies with group support
-- Users can view items if:
-- 1. They are a member of the group (if group_id is set), OR
-- 2. They own the item directly (for backward compatibility with user_id), OR
-- 3. They are an admin
CREATE POLICY "Users can view checklist items from their groups"
    ON public.checklist_items
    FOR SELECT
    USING (
        (group_id IS NOT NULL AND public.is_group_member(group_id))
        OR (group_id IS NULL AND auth.uid() = user_id)
        OR public.is_admin()
    );

-- Users can create items if:
-- 1. They are a member of the group (if group_id is set), OR
-- 2. They are creating their own item (user_id matches), OR
-- 3. They are an admin
CREATE POLICY "Users can create checklist items in their groups"
    ON public.checklist_items
    FOR INSERT
    WITH CHECK (
        (group_id IS NOT NULL AND public.is_group_member(group_id))
        OR (group_id IS NULL AND auth.uid() = user_id)
        OR public.is_admin()
    );

-- Users can update items if:
-- 1. They are a member of the group (if group_id is set), OR
-- 2. They own the item directly (for backward compatibility), OR
-- 3. They are an admin
CREATE POLICY "Users can update checklist items in their groups"
    ON public.checklist_items
    FOR UPDATE
    USING (
        (group_id IS NOT NULL AND public.is_group_member(group_id))
        OR (group_id IS NULL AND auth.uid() = user_id)
        OR public.is_admin()
    );

-- Users can delete items if:
-- 1. They are a member of the group (if group_id is set), OR
-- 2. They own the item directly (for backward compatibility), OR
-- 3. They are an admin
CREATE POLICY "Users can delete checklist items in their groups"
    ON public.checklist_items
    FOR DELETE
    USING (
        (group_id IS NOT NULL AND public.is_group_member(group_id))
        OR (group_id IS NULL AND auth.uid() = user_id)
        OR public.is_admin()
    );

-- Function to automatically add group owner as a member
CREATE OR REPLACE FUNCTION public.add_owner_to_group()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.group_members (group_id, user_id)
    VALUES (NEW.id, NEW.owner_id)
    ON CONFLICT (group_id, user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE TRIGGER add_owner_to_group_trigger
    AFTER INSERT ON public.checkbox_groups
    FOR EACH ROW
    EXECUTE FUNCTION public.add_owner_to_group();
