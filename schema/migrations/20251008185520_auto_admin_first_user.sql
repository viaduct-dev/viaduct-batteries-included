-- Automatically make the first user in the system an admin

-- Function to set the first user as admin
CREATE OR REPLACE FUNCTION public.make_first_user_admin()
RETURNS TRIGGER AS $$
BEGIN
  -- Check if this is the first user (count = 1 after insert)
  IF (SELECT COUNT(*) FROM auth.users) = 1 THEN
    -- Update the new user's app_metadata to include is_admin: true
    UPDATE auth.users
    SET raw_app_meta_data =
      COALESCE(raw_app_meta_data, '{}'::jsonb) ||
      '{"is_admin": true}'::jsonb
    WHERE id = NEW.id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth;

-- Trigger to run after user creation
CREATE TRIGGER make_first_user_admin_trigger
  AFTER INSERT ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION public.make_first_user_admin();
