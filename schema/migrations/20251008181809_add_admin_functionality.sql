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

-- Admin functions are now available
-- RLS policies for specific tables should be defined in their respective migrations
