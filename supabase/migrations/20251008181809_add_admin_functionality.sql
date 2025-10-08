-- Add admin functionality to the application
-- Admins will have special scope and permissions to mutate all items

-- Helper function to check if a user is an admin
-- Reads from the JWT claims (app_metadata.is_admin)
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN AS $$
BEGIN
  RETURN COALESCE(
    (auth.jwt() -> 'app_metadata' ->> 'is_admin')::boolean,
    false
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Function to set a user as admin (only callable by existing admins)
CREATE OR REPLACE FUNCTION public.set_user_admin(target_user_id UUID, is_admin BOOLEAN)
RETURNS void AS $$
BEGIN
  -- Check if the calling user is an admin
  IF NOT public.is_admin() THEN
    RAISE EXCEPTION 'Only admins can set admin status';
  END IF;

  -- Update the target user's app_metadata
  UPDATE auth.users
  SET raw_app_meta_data =
    COALESCE(raw_app_meta_data, '{}'::jsonb) ||
    jsonb_build_object('is_admin', is_admin)
  WHERE id = target_user_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth;

-- Update RLS policies to allow admins to mutate all items

-- Drop existing mutation policies
DROP POLICY IF EXISTS "Users can create their own checklist items" ON public.checklist_items;
DROP POLICY IF EXISTS "Users can update their own checklist items" ON public.checklist_items;
DROP POLICY IF EXISTS "Users can delete their own checklist items" ON public.checklist_items;

-- Recreate policies with admin access
CREATE POLICY "Users and admins can create checklist items"
  ON public.checklist_items
  FOR INSERT
  WITH CHECK (auth.uid() = user_id OR public.is_admin());

CREATE POLICY "Users and admins can update checklist items"
  ON public.checklist_items
  FOR UPDATE
  USING (auth.uid() = user_id OR public.is_admin());

CREATE POLICY "Users and admins can delete checklist items"
  ON public.checklist_items
  FOR DELETE
  USING (auth.uid() = user_id OR public.is_admin());

-- Keep the SELECT policy unchanged (users can only see their own items)
-- Admins don't need to see all items, just mutate them
