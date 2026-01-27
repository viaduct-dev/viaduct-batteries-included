import { createClient, SupabaseClient } from '@supabase/supabase-js';
import type { Database } from './types';

// Try to get config from environment variables first (local development)
const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL;
const SUPABASE_PUBLISHABLE_KEY = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY;

// Check if we have local config
const hasLocalConfig = Boolean(SUPABASE_URL && SUPABASE_PUBLISHABLE_KEY);

// Singleton instance
let supabaseInstance: SupabaseClient<Database> | null = null;
let initPromise: Promise<SupabaseClient<Database>> | null = null;

/**
 * Create Supabase client with the given config.
 */
function createSupabaseClient(url: string, key: string): SupabaseClient<Database> {
  return createClient<Database>(url, key, {
    auth: {
      storage: localStorage,
      persistSession: true,
      autoRefreshToken: true,
    }
  });
}

/**
 * Initialize Supabase client.
 * - Uses env vars if available (local development)
 * - Otherwise fetches config from backend (production)
 */
export async function initSupabase(): Promise<SupabaseClient<Database>> {
  // Return existing instance if already initialized
  if (supabaseInstance) {
    return supabaseInstance;
  }

  // Return existing promise if initialization is in progress
  if (initPromise) {
    return initPromise;
  }

  initPromise = (async () => {
    // Use local config if available
    if (hasLocalConfig) {
      console.log('[Supabase] Using local environment configuration');
      supabaseInstance = createSupabaseClient(SUPABASE_URL!, SUPABASE_PUBLISHABLE_KEY!);
      return supabaseInstance;
    }

    // Fetch config from backend
    console.log('[Supabase] Fetching configuration from backend...');
    try {
      // Import dynamically to avoid circular dependency
      const { fetchSupabaseConfig } = await import('@/lib/graphql');
      const config = await fetchSupabaseConfig();

      console.log('[Supabase] Configuration received, initializing client');
      supabaseInstance = createSupabaseClient(config.url, config.anonKey);
      return supabaseInstance;
    } catch (error) {
      console.error('[Supabase] Failed to fetch configuration:', error);
      throw new Error(
        'Failed to initialize Supabase. Make sure the backend is running and configured.'
      );
    }
  })();

  return initPromise;
}

/**
 * Get the Supabase client synchronously.
 * Throws if not initialized - call initSupabase() first.
 */
export function getSupabase(): SupabaseClient<Database> {
  if (!supabaseInstance) {
    throw new Error('Supabase not initialized. Call initSupabase() first.');
  }
  return supabaseInstance;
}

/**
 * Legacy export for backward compatibility.
 * Creates a placeholder client if not configured.
 * Prefer using initSupabase() and getSupabase() for new code.
 */
export const supabase = hasLocalConfig
  ? createSupabaseClient(SUPABASE_URL!, SUPABASE_PUBLISHABLE_KEY!)
  : createSupabaseClient('https://placeholder.supabase.co', 'placeholder-key');

// Export configuration status
export const supabaseConfigured = hasLocalConfig;
