import { supabase } from "@/integrations/supabase/client";

interface GraphQLResponse<T> {
  data?: T;
  errors?: Array<{ message: string }>;
}

const GRAPHQL_ENDPOINT = import.meta.env.VITE_GRAPHQL_ENDPOINT || "http://localhost:8080/graphql";

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

// GraphQL queries and mutations for user management
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

// GraphQL queries and mutations for groups and checklists
export const GET_CHECKBOX_GROUPS = `
  query GetCheckboxGroups {
    checkboxGroups {
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

export const GET_CHECKBOX_GROUP = `
  query GetCheckboxGroup($id: ID!) {
    checkboxGroup(id: $id) {
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
      checklistItems {
        id
        title
        completed
        userId
        groupId
        createdAt
      }
    }
  }
`;

export const CREATE_CHECKBOX_GROUP = `
  mutation CreateCheckboxGroup($name: String!, $description: String) {
    createCheckboxGroup(input: {
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

export const CREATE_CHECKLIST_ITEM = `
  mutation CreateChecklistItem($title: String!, $groupId: ID!) {
    createChecklistItem(input: {
      title: $title
      groupId: $groupId
    }) {
      id
      title
      completed
      userId
      groupId
      createdAt
    }
  }
`;

export const UPDATE_CHECKLIST_ITEM = `
  mutation UpdateChecklistItem($id: ID!, $completed: Boolean, $title: String) {
    updateChecklistItem(input: {
      id: $id
      completed: $completed
      title: $title
    }) {
      id
      title
      completed
      updatedAt
    }
  }
`;

export const GET_CHECKLIST_ITEMS = `
  query GetChecklistItems {
    checklistItems {
      id
      title
      completed
      userId
      groupId
      createdAt
    }
  }
`;

export const DELETE_CHECKLIST_ITEM = `
  mutation DeleteChecklistItem($id: ID!) {
    deleteChecklistItem(input: {
      id: $id
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
