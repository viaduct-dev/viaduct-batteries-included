-- Create a function to get a user by ID
-- Available to all authenticated users
CREATE OR REPLACE FUNCTION get_user_by_id(user_id UUID)
RETURNS TABLE (
    id UUID,
    email VARCHAR(255),
    raw_app_meta_data JSONB,
    created_at TIMESTAMPTZ
)
SECURITY DEFINER
SET search_path = public
LANGUAGE plpgsql
AS $$
BEGIN
    -- Any authenticated user can get basic user info
    RETURN QUERY
    SELECT
        u.id,
        u.email::VARCHAR(255),
        u.raw_app_meta_data,
        u.created_at
    FROM auth.users u
    WHERE u.id = user_id;
END;
$$;
