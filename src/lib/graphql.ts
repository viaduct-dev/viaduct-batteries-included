import { supabase } from "@/integrations/supabase/client";

interface GraphQLResponse<T> {
  data?: T;
  errors?: Array<{ message: string }>;
}

const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL;
const SUPABASE_ANON_KEY = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY;

export async function executeGraphQL<T>(query: string, variables?: Record<string, any>): Promise<T> {
  const { data: session } = await supabase.auth.getSession();
  
  if (!session.session) {
    throw new Error("Not authenticated");
  }

  const response = await fetch(`${SUPABASE_URL}/graphql/v1`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "apikey": SUPABASE_ANON_KEY,
      "Authorization": `Bearer ${session.session.access_token}`,
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

// GraphQL queries and mutations
export const GET_CHECKLIST_ITEMS = `
  query GetChecklistItems {
    checklist_itemsCollection {
      edges {
        node {
          id
          title
          completed
          created_at
          updated_at
        }
      }
    }
  }
`;

export const CREATE_CHECKLIST_ITEM = `
  mutation CreateChecklistItem($title: String!, $user_id: UUID!) {
    insertIntochecklist_itemsCollection(objects: [{
      title: $title
      user_id: $user_id
      completed: false
    }]) {
      records {
        id
        title
        completed
        created_at
        updated_at
      }
    }
  }
`;

export const UPDATE_CHECKLIST_ITEM = `
  mutation UpdateChecklistItem($id: UUID!, $completed: Boolean!) {
    updatechecklist_itemsCollection(
      filter: { id: { eq: $id } }
      set: { completed: $completed }
    ) {
      records {
        id
        title
        completed
        updated_at
      }
    }
  }
`;

export const DELETE_CHECKLIST_ITEM = `
  mutation DeleteChecklistItem($id: UUID!) {
    deleteFromchecklist_itemsCollection(filter: { id: { eq: $id } }) {
      records {
        id
      }
    }
  }
`;
