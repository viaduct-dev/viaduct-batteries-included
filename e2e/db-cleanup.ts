import { createClient } from '@supabase/supabase-js';

const supabaseUrl = process.env.VITE_SUPABASE_URL || 'http://127.0.0.1:54321';
const supabaseServiceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY ||
  'sb_secret_N7UND0UgjKTVK-Uodkm0Hg_xSvEMPvz';

// Create a Supabase client with service role key to bypass RLS
const supabase = createClient(supabaseUrl, supabaseServiceRoleKey);

/**
 * Clean up all test data from the database
 * This should be called before each test to ensure a clean slate
 */
export async function cleanupTestData(): Promise<void> {
  try {
    // Call the cleanup function via RPC
    const { error } = await supabase.rpc('cleanup_test_data');

    if (error) {
      console.error('Failed to cleanup test data:', error);
      throw error;
    }

    console.log('✓ Database cleaned up successfully');
  } catch (error) {
    console.error('Error during database cleanup:', error);
    throw error;
  }
}
