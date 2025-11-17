import { supabase } from "@/integrations/supabase/client";

interface GraphQLResponse<T> {
  data?: T;
  errors?: Array<{ message: string }>;
}

const GRAPHQL_ENDPOINT = import.meta.env.VITE_GRAPHQL_ENDPOINT || "http://localhost:8080/graphql";

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
// Blog Post Management (Multi-Tenant Blog Platform)
// ----------------------------------------------------------------------------

export const GET_BLOG_POSTS_BY_GROUP = `
  query GetBlogPostsByGroup($groupId: ID!) {
    blogPostsByGroup(groupId: $groupId) {
      id
      title
      slug
      content
      published
      createdAt
      updatedAt
      userId
      groupId
      author {
        id
        email
      }
    }
  }
`;

export const GET_BLOG_POST = `
  query GetBlogPost($id: ID!) {
    blogPost(id: $id) {
      id
      title
      slug
      content
      published
      createdAt
      updatedAt
      userId
      groupId
      author {
        id
        email
      }
      group {
        id
        name
        description
      }
    }
  }
`;

export const GET_BLOG_POST_BY_SLUG = `
  query GetBlogPostBySlug($groupId: ID!, $slug: String!) {
    blogPostBySlug(groupId: $groupId, slug: $slug) {
      id
      title
      slug
      content
      published
      createdAt
      updatedAt
      userId
      groupId
      author {
        id
        email
      }
      group {
        id
        name
        description
      }
    }
  }
`;

export const GET_MY_BLOG_POSTS = `
  query GetMyBlogPosts {
    myBlogPosts {
      id
      title
      slug
      content
      published
      createdAt
      updatedAt
      userId
      groupId
      group {
        id
        name
      }
    }
  }
`;

export const CREATE_BLOG_POST = `
  mutation CreateBlogPost($groupId: ID!, $title: String!, $slug: String!, $content: String!, $published: Boolean) {
    createBlogPost(input: {
      groupId: $groupId
      title: $title
      slug: $slug
      content: $content
      published: $published
    }) {
      id
      title
      slug
      content
      published
      createdAt
      updatedAt
      userId
      groupId
    }
  }
`;

export const UPDATE_BLOG_POST = `
  mutation UpdateBlogPost($id: ID!, $title: String, $slug: String, $content: String, $published: Boolean) {
    updateBlogPost(input: {
      id: $id
      title: $title
      slug: $slug
      content: $content
      published: $published
    }) {
      id
      title
      slug
      content
      published
      createdAt
      updatedAt
      userId
      groupId
    }
  }
`;

export const DELETE_BLOG_POST = `
  mutation DeleteBlogPost($id: ID!) {
    deleteBlogPost(input: {
      id: $id
    })
  }
`;

export const PUBLISH_BLOG_POST = `
  mutation PublishBlogPost($id: ID!, $published: Boolean!) {
    publishBlogPost(input: {
      id: $id
      published: $published
    }) {
      id
      title
      slug
      published
      updatedAt
    }
  }
`;
