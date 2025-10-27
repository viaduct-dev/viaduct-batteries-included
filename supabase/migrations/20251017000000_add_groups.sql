-- Add groups functionality (CORE FRAMEWORK)
-- Users can create groups and organize resources via groups
-- Users can only access resources from groups they belong to
--
-- This is CORE framework functionality - keep this migration active

-- Create groups table (renamed from checkbox_groups for genericity)
CREATE TABLE IF NOT EXISTS public.groups (
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
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(group_id, user_id)
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_group_members_user_id ON public.group_members(user_id);
CREATE INDEX IF NOT EXISTS idx_group_members_group_id ON public.group_members(group_id);

-- Helper function to check if a user is a member of a group
-- This is used by RLS policies and policy executors
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
-- Used for owner-only operations (add/remove members, delete group)
CREATE OR REPLACE FUNCTION public.is_group_owner(group_uuid UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1
        FROM public.groups
        WHERE id = group_uuid
        AND owner_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Update trigger for groups
CREATE OR REPLACE FUNCTION update_groups_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_groups_timestamp
    BEFORE UPDATE ON public.groups
    FOR EACH ROW
    EXECUTE FUNCTION update_groups_updated_at();

-- Enable RLS on new tables
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;

-- RLS Policies for groups
-- Users can view groups they are members of or own
CREATE POLICY "Users can view groups they are members of"
    ON public.groups
    FOR SELECT
    USING (public.is_group_member(id) OR owner_id = auth.uid());

-- Users can create their own groups
CREATE POLICY "Users can create their own groups"
    ON public.groups
    FOR INSERT
    WITH CHECK (auth.uid() = owner_id);

-- Group owners can update their groups
CREATE POLICY "Group owners can update their groups"
    ON public.groups
    FOR UPDATE
    USING (owner_id = auth.uid());

-- Group owners can delete their groups
CREATE POLICY "Group owners can delete their groups"
    ON public.groups
    FOR DELETE
    USING (owner_id = auth.uid());

-- RLS Policies for group_members
-- Users can view group memberships they are part of
CREATE POLICY "Users can view group memberships they are part of"
    ON public.group_members
    FOR SELECT
    USING (public.is_group_member(group_id) OR user_id = auth.uid());

-- Group owners can add members
CREATE POLICY "Group owners can add members"
    ON public.group_members
    FOR INSERT
    WITH CHECK (public.is_group_owner(group_id));

-- Group owners and members themselves can remove memberships
CREATE POLICY "Group owners and members can remove memberships"
    ON public.group_members
    FOR DELETE
    USING (public.is_group_owner(group_id) OR user_id = auth.uid());

-- Function to automatically add group owner as a member
-- This ensures owners always have access to their own groups
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
    AFTER INSERT ON public.groups
    FOR EACH ROW
    EXECUTE FUNCTION public.add_owner_to_group();
