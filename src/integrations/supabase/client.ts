import { createClient } from '@supabase/supabase-js';
import type { Database } from './types';

const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL;
const SUPABASE_PUBLISHABLE_KEY = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY;

// Check for missing configuration
const isConfigured = SUPABASE_URL && SUPABASE_PUBLISHABLE_KEY;

if (!isConfigured) {
  console.warn(
    '%c⚠️ Supabase not configured',
    'color: orange; font-weight: bold; font-size: 14px;'
  );
  console.warn(
    'Set VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY environment variables.\n' +
    'Get your credentials from: https://supabase.com/dashboard → Settings → API'
  );
}

// Use placeholder values if not configured (allows app to load for viewing)
const url = SUPABASE_URL || 'https://placeholder.supabase.co';
const key = SUPABASE_PUBLISHABLE_KEY || 'placeholder-key';

// Import the supabase client like this:
// import { supabase } from "@/integrations/supabase/client";

export const supabase = createClient<Database>(url, key, {
  auth: {
    storage: localStorage,
    persistSession: true,
    autoRefreshToken: true,
  }
});

// Export configuration status for use elsewhere
export const supabaseConfigured = isConfigured;