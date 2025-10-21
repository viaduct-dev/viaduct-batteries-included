-- Function to search users by email (available to all authenticated users)
CREATE OR REPLACE FUNCTION public.search_users(search_query text)
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
  -- All authenticated users can search for other users
  -- This is needed for adding members to groups

  RETURN QUERY
  SELECT
    u.id,
    u.email::text,
    u.raw_app_meta_data,
    u.created_at
  FROM auth.users u
  WHERE u.email ILIKE '%' || search_query || '%'
  ORDER BY u.email
  LIMIT 10;
END;
$$;
