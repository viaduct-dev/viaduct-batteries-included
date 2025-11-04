-- Add cleanup function for e2e tests
-- This function clears all user-created data while preserving users

CREATE OR REPLACE FUNCTION public.cleanup_test_data()
RETURNS void AS $$
BEGIN
    -- Delete in order to respect foreign key constraints
    -- Use WHERE true to bypass Supabase's safety check requiring WHERE clauses

    -- Delete all checklist items (both group and non-group items)
    DELETE FROM public.checklist_items WHERE true;

    -- Delete all group members
    DELETE FROM public.group_members WHERE true;

    -- Delete all groups
    DELETE FROM public.groups WHERE true;

    RAISE NOTICE 'Test data cleaned up successfully';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Grant execute permission to authenticated users
GRANT EXECUTE ON FUNCTION public.cleanup_test_data() TO authenticated;
GRANT EXECUTE ON FUNCTION public.cleanup_test_data() TO service_role;
