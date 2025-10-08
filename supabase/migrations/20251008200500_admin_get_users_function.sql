-- Function to get all users (admin only)
CREATE OR REPLACE FUNCTION public.get_all_users()
RETURNS TABLE (
  id uuid,
  email text,
  raw_app_meta_data jsonb,
  created_at timestamptz
)
SECURITY DEFINER
SET search_path = public, auth
LANGUAGE plpgsql
AS $$
BEGIN
  -- Check if the current user is an admin
  IF NOT (SELECT COALESCE((auth.jwt() -> 'app_metadata' ->> 'is_admin')::boolean, false)) THEN
    RAISE EXCEPTION 'Only admins can view all users';
  END IF;

  RETURN QUERY
  SELECT
    u.id,
    u.email::text,
    u.raw_app_meta_data,
    u.created_at
  FROM auth.users u
  ORDER BY u.created_at DESC;
END;
$$;

-- Function to delete a user (admin only)
CREATE OR REPLACE FUNCTION public.delete_user_by_id(user_id uuid)
RETURNS boolean
SECURITY DEFINER
SET search_path = public, auth
LANGUAGE plpgsql
AS $$
BEGIN
  -- Check if the current user is an admin
  IF NOT (SELECT COALESCE((auth.jwt() -> 'app_metadata' ->> 'is_admin')::boolean, false)) THEN
    RAISE EXCEPTION 'Only admins can delete users';
  END IF;

  -- Delete the user
  DELETE FROM auth.users WHERE id = user_id;

  RETURN true;
END;
$$;
