import { getSupabase } from "@/integrations/supabase/client";

interface GraphQLResponse<T> {
  data?: T;
  errors?: Array<{ message: string }>;
}

/**
 * Normalize the GraphQL endpoint URL.
 * Handles various formats:
 * - Full URL: https://example.com/graphql
 * - Host:port from Render: example.onrender.com:443
 * - Hostname only: example.onrender.com
 * - Internal Render hostname: viaduct-backend:10000 (derives public URL from current location)
 * - Local development: http://localhost:8080/graphql
 */
function normalizeGraphQLEndpoint(endpoint: string | undefined): string {
  if (!endpoint) {
    return "http://localhost:8080/graphql";
  }

  // Already a full URL
  if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
    // Ensure it ends with /graphql
    return endpoint.endsWith("/graphql") ? endpoint : `${endpoint}/graphql`;
  }

  // Check if this is an internal Render hostname (no dots, like "viaduct-backend:10000")
  // These are internal service discovery names that browsers can't resolve
  const hostPart = endpoint.split(":")[0];
  if (!hostPart.includes(".") && typeof window !== "undefined") {
    // Derive the backend URL from the frontend URL
    // viaduct-frontend.onrender.com -> viaduct-backend.onrender.com
    const currentHost = window.location.hostname;
    if (currentHost.includes(".onrender.com")) {
      const backendHost = currentHost.replace("-frontend", "-backend");
      console.log(`[GraphQL] Detected internal hostname, using derived URL: https://${backendHost}/graphql`);
      return `https://${backendHost}/graphql`;
    }
  }

  // Host:port format (from Render's fromService.hostport)
  // or hostname only (from Render's fromService.host)
  const baseUrl = `https://${endpoint}`;
  return `${baseUrl}/graphql`;
}

const GRAPHQL_ENDPOINT = normalizeGraphQLEndpoint(import.meta.env.VITE_GRAPHQL_ENDPOINT);

/**
 * Execute a GraphQL query or mutation against the Viaduct backend.
 * Automatically includes authentication headers from Supabase session.
 *
 * @param query - GraphQL query or mutation string
 * @param variables - Variables for the GraphQL operation
 * @returns Parsed response data
 * @throws Error if not authenticated or request fails
 */
export async function executeGraphQL<T>(query: string, variables?: Record<string, any>): Promise<T> {
  // Wait for session to be available, with retries for initialization timing
  let session = null;
  let attempts = 0;
  const maxAttempts = 10;
  const supabase = getSupabase();

  while (!session && attempts < maxAttempts) {
    const { data, error } = await supabase.auth.getSession();

    // Debug logging
    if (attempts === 0) {
      console.log('[GraphQL] Attempting to get session, attempt', attempts + 1);
      console.log('[GraphQL] Session data:', data.session ? 'EXISTS' : 'NULL');
      if (error) console.log('[GraphQL] Session error:', error);
    }

    if (data.session) {
      session = data.session;
      break;
    }

    // Wait a bit for Supabase client to initialize from localStorage
    if (attempts < maxAttempts - 1) {
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    attempts++;
  }

  if (!session) {
    console.error('[GraphQL] No session after', maxAttempts, 'attempts');
    throw new Error("Not authenticated");
  }

  console.log('[GraphQL] Session acquired, making request');

  let response;
  try {
    response = await fetch(GRAPHQL_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${session.access_token}`,
        "X-User-Id": session.user.id,
      },
      body: JSON.stringify({
        query,
        variables,
      }),
    });
    console.log('[GraphQL] Response received:', response.status, response.statusText);
  } catch (fetchError) {
    console.error('[GraphQL] Fetch failed:', fetchError);
    throw new Error(`Fetch error: ${fetchError}`);
  }

  console.log('[GraphQL] Response status:', response.status, response.statusText);

  if (!response.ok) {
    console.error('[GraphQL] HTTP error:', response.status, await response.text());
    throw new Error(`HTTP error: ${response.status}`);
  }

  const result: GraphQLResponse<T> = await response.json();
  console.log('[GraphQL] Response data:', result.data ? 'HAS DATA' : 'NO DATA');
  console.log('[GraphQL] Response errors:', result.errors || 'NONE');

  if (result.errors) {
    console.error('[GraphQL] GraphQL errors:', result.errors);
    throw new Error(result.errors[0]?.message || "GraphQL error");
  }

  console.log('[GraphQL] Request succeeded');
  return result.data as T;
}

// ============================================================================
// CORE FRAMEWORK QUERIES & MUTATIONS
// These are part of the framework and should remain active
// ============================================================================

// ----------------------------------------------------------------------------
// User Management (Admin Only)
// ----------------------------------------------------------------------------

export const SET_USER_ADMIN = `
  mutation SetUserAdmin($userId: String!, $isAdmin: Boolean!) {
    setUserAdmin(input: {
      userId: $userId
      isAdmin: $isAdmin
    })
  }
`;

export const GET_USERS = `
  query GetUsers {
    users {
      id
      email
      isAdmin
      createdAt
    }
  }
`;

export const DELETE_USER = `
  mutation DeleteUser($userId: String!) {
    deleteUser(input: {
      userId: $userId
    })
  }
`;

export const SEARCH_USERS = `
  query SearchUsers($query: String!) {
    searchUsers(query: $query) {
      id
      email
      isAdmin
      createdAt
    }
  }
`;

// ----------------------------------------------------------------------------
// Group Management (Core Framework)
// ----------------------------------------------------------------------------

export const GET_GROUPS = `
  query GetGroups {
    groups {
      id
      name
      description
      ownerId
      createdAt
      members {
        id
        userId
        joinedAt
      }
    }
  }
`;

export const GET_GROUP = `
  query GetGroup($id: ID!) {
    group(id: $id) {
      id
      name
      description
      ownerId
      createdAt
      members {
        id
        userId
        joinedAt
      }
    }
  }
`;

export const CREATE_GROUP = `
  mutation CreateGroup($name: String!, $description: String) {
    createGroup(input: {
      name: $name
      description: $description
    }) {
      id
      name
      description
      ownerId
      createdAt
    }
  }
`;

export const ADD_GROUP_MEMBER = `
  mutation AddGroupMember($groupId: ID!, $userId: String!) {
    addGroupMember(input: {
      groupId: $groupId
      userId: $userId
    }) {
      id
      userId
      groupId
      joinedAt
    }
  }
`;

export const REMOVE_GROUP_MEMBER = `
  mutation RemoveGroupMember($groupId: ID!, $userId: String!) {
    removeGroupMember(input: {
      groupId: $groupId
      userId: $userId
    })
  }
`;

// ----------------------------------------------------------------------------
// Supabase Configuration (Public - no auth required)
// ----------------------------------------------------------------------------

export const GET_SUPABASE_CONFIG = `
  query GetSupabaseConfig {
    supabaseConfig {
      url
      anonKey
    }
  }
`;

/**
 * Fetch Supabase configuration from the backend.
 * This is a public endpoint that doesn't require authentication.
 */
export async function fetchSupabaseConfig(): Promise<{ url: string; anonKey: string }> {
  const response = await fetch(GRAPHQL_ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      query: GET_SUPABASE_CONFIG,
    }),
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch Supabase config: ${response.status}`);
  }

  const result = await response.json();

  if (result.errors) {
    throw new Error(result.errors[0]?.message || "Failed to fetch Supabase config");
  }

  return result.data.supabaseConfig;
}
