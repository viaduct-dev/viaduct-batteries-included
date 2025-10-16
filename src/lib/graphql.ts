import { supabase } from "@/integrations/supabase/client";

interface GraphQLResponse<T> {
  data?: T;
  errors?: Array<{ message: string }>;
}

const GRAPHQL_ENDPOINT = import.meta.env.VITE_GRAPHQL_ENDPOINT || "http://localhost:8080/graphql";

export async function executeGraphQL<T>(query: string, variables?: Record<string, any>): Promise<T> {
  const { data: session } = await supabase.auth.getSession();

  if (!session.session) {
    throw new Error("Not authenticated");
  }

  const response = await fetch(GRAPHQL_ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${session.session.access_token}`,
      "X-User-Id": session.session.user.id,
    },
    body: JSON.stringify({
      query,
      variables,
    }),
  });

  const result: GraphQLResponse<T> = await response.json();

  if (result.errors) {
    throw new Error(result.errors[0]?.message || "GraphQL error");
  }

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
