# Implementing a Resource in the GraphQL Policy Framework

This guide walks you through implementing a custom resource type using the GraphQL Policy Framework. We'll use the checklist example as a reference.

## Overview

Implementing a resource involves:
1. **Database layer** - Table with RLS policies
2. **GraphQL layer** - Schema definition
3. **Backend layer** - Resolvers and business logic
4. **Frontend layer** - UI components and queries

All layers integrate with the framework's group-based access control and admin bypass system.

---

## Step 1: Database Table & RLS Policies

### Create a Migration

Create a new migration file: `supabase/migrations/YYYYMMDDHHMMSS_add_your_resource.sql`

### Example Template

```sql
-- Create your resource table
CREATE TABLE IF NOT EXISTS public.your_resources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,

    -- Your resource-specific fields
    title TEXT NOT NULL,
    status TEXT DEFAULT 'pending',

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_your_resources_group_id
    ON public.your_resources(group_id);

CREATE INDEX IF NOT EXISTS idx_your_resources_user_id
    ON public.your_resources(user_id);

-- Enable RLS
ALTER TABLE public.your_resources ENABLE ROW LEVEL SECURITY;

-- RLS Policies (follow this pattern)
CREATE POLICY "Users can view resources from their groups"
    ON public.your_resources FOR SELECT
    USING (public.is_group_member(group_id) OR public.is_admin());

CREATE POLICY "Users can create resources in their groups"
    ON public.your_resources FOR INSERT
    WITH CHECK (public.is_group_member(group_id) OR public.is_admin());

CREATE POLICY "Users can update resources in their groups"
    ON public.your_resources FOR UPDATE
    USING (public.is_group_member(group_id) OR public.is_admin());

CREATE POLICY "Users can delete resources in their groups"
    ON public.your_resources FOR DELETE
    USING (public.is_group_member(group_id) OR public.is_admin());

-- Updated_at trigger
CREATE OR REPLACE FUNCTION update_your_resources_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_your_resources_timestamp
    BEFORE UPDATE ON public.your_resources
    FOR EACH ROW
    EXECUTE FUNCTION update_your_resources_updated_at();
```

### Key Points

- **group_id**: Foreign key to `groups` table (REQUIRED for framework integration)
- **user_id**: Tracks creator (useful for attribution)
- **RLS Policies**: ALWAYS include `is_group_member(group_id) OR is_admin()`
- **Indexes**: Add for foreign keys and frequently queried columns
- **Timestamps**: Use triggers to auto-update `updated_at`

---

## Step 2: GraphQL Schema

### Create Schema File

Create: `backend/src/main/viaduct/schema/YourResource.graphqls`

### Example Template

```graphql
"""
Your resource description.
Only members of the group can access resources.
"""
type YourResource implements Node @scope(to: ["default"]) @requiresGroupMembership(groupIdField: "groupId") {
  id: ID!
  title: String!
  status: String!
  userId: String!
  groupId: String!
  createdAt: String!
  updatedAt: String!
}

input CreateYourResourceInput @scope(to: ["default"]) {
  title: String!
  status: String
  groupId: ID! @idOf(type: "CheckboxGroup")  # Note: Will be "Group" after rename
}

input UpdateYourResourceInput @scope(to: ["default"]) {
  id: ID! @idOf(type: "YourResource")
  title: String
  status: String
}

input DeleteYourResourceInput @scope(to: ["default"]) {
  id: ID! @idOf(type: "YourResource")
}

extend type Query @scope(to: ["default"]) {
  """
  Get all your resources from groups the user is a member of.
  """
  yourResources: [YourResource!]! @resolver

  """
  Get your resources for a specific group.
  """
  yourResourcesByGroup(groupId: ID! @idOf(type: "CheckboxGroup")): [YourResource!]! @resolver
}

extend type Mutation @scope(to: ["default"]) {
  """
  Create a new resource in a group.
  """
  createYourResource(input: CreateYourResourceInput!): YourResource! @resolver

  """
  Update a resource.
  """
  updateYourResource(input: UpdateYourResourceInput!): YourResource! @resolver @requiresGroupMembership

  """
  Delete a resource.
  """
  deleteYourResource(input: DeleteYourResourceInput!): Boolean! @resolver @requiresGroupMembership
}
```

### Key Points

- **@scope(to: ["default"])**: Available to all authenticated users
- **@requiresGroupMembership**: Enforces group access control
- **@idOf**: Type-safe GlobalID references
- **implements Node**: Enables GlobalID support
- **@resolver**: Tells Viaduct to generate resolver base classes

---

## Step 3: Database Access Layer

### Add Methods to SupabaseClient

Edit: `backend/src/main/kotlin/com/viaduct/SupabaseClient.kt`

```kotlin
// In AuthenticatedSupabaseClient class

suspend fun getYourResources(): List<YourResourceEntity> {
    return client.from("your_resources")
        .select()
        .decodeList<YourResourceEntity>()
}

suspend fun getYourResourcesByGroup(groupId: String): List<YourResourceEntity> {
    return client.from("your_resources")
        .select()
        .eq("group_id", groupId)
        .decodeList<YourResourceEntity>()
}

suspend fun createYourResource(
    title: String,
    status: String?,
    userId: String,
    groupId: String
): YourResourceEntity {
    return client.from("your_resources")
        .insert(mapOf(
            "title" to title,
            "status" to (status ?: "pending"),
            "user_id" to userId,
            "group_id" to groupId
        ))
        .decodeSingle<YourResourceEntity>()
}

suspend fun updateYourResource(
    id: String,
    title: String?,
    status: String?
): YourResourceEntity {
    val updates = mutableMapOf<String, Any>()
    title?.let { updates["title"] = it }
    status?.let { updates["status"] = it }

    return client.from("your_resources")
        .update(updates)
        .eq("id", id)
        .decodeSingle<YourResourceEntity>()
}

suspend fun deleteYourResource(id: String): Boolean {
    client.from("your_resources")
        .delete()
        .eq("id", id)
    return true
}

// Add entity class
@Serializable
data class YourResourceEntity(
    val id: String,
    val title: String,
    val status: String,
    val user_id: String,
    val group_id: String,
    val created_at: String,
    val updated_at: String
)
```

---

## Step 4: Resolvers

### Create Resolvers

Create files in: `backend/src/main/kotlin/com/viaduct/resolvers/`

#### Create Resolver

```kotlin
package com.viaduct.resolvers

import com.viaduct.resolvers.resolverbases.MutationResolvers
import viaduct.api.Resolver
import viaduct.api.grts.YourResource

@Resolver
class CreateYourResourceResolver : MutationResolvers.CreateYourResource() {
    override suspend fun resolve(ctx: Context): YourResource {
        val input = ctx.arguments.input
        val userId = ctx.userId
        val groupId = input.groupId.internalID

        val entity = ctx.authenticatedClient.createYourResource(
            title = input.title,
            status = input.status,
            userId = userId,
            groupId = groupId
        )

        return YourResource.Builder(ctx)
            .id(ctx.globalIDFor(YourResource.Reflection, entity.id))
            .title(entity.title)
            .status(entity.status)
            .userId(entity.user_id)
            .groupId(entity.group_id)
            .createdAt(entity.created_at)
            .updatedAt(entity.updated_at)
            .build()
    }
}
```

#### Query Resolver

```kotlin
package com.viaduct.resolvers

import com.viaduct.resolvers.resolverbases.QueryResolvers
import viaduct.api.Resolver
import viaduct.api.grts.YourResource

@Resolver
class YourResourcesQueryResolver : QueryResolvers.YourResources() {
    override suspend fun resolve(ctx: Context): List<YourResource> {
        val entities = ctx.authenticatedClient.getYourResources()

        return entities.map { entity ->
            YourResource.Builder(ctx)
                .id(ctx.globalIDFor(YourResource.Reflection, entity.id))
                .title(entity.title)
                .status(entity.status)
                .userId(entity.user_id)
                .groupId(entity.group_id)
                .createdAt(entity.created_at)
                .updatedAt(entity.updated_at)
                .build()
        }
    }
}
```

Create similar resolvers for:
- Update
- Delete
- Query by group

---

## Step 5: Frontend Queries

### Create Query File

Create: `src/lib/your-resource-queries.ts`

```typescript
export const GET_YOUR_RESOURCES = `
  query GetYourResources {
    yourResources {
      id
      title
      status
      userId
      groupId
      createdAt
    }
  }
`;

export const CREATE_YOUR_RESOURCE = `
  mutation CreateYourResource($title: String!, $status: String, $groupId: ID!) {
    createYourResource(input: {
      title: $title
      status: $status
      groupId: $groupId
    }) {
      id
      title
      status
      groupId
      createdAt
    }
  }
`;

export const UPDATE_YOUR_RESOURCE = `
  mutation UpdateYourResource($id: ID!, $title: String, $status: String) {
    updateYourResource(input: {
      id: $id
      title: $title
      status: $status
    }) {
      id
      title
      status
      updatedAt
    }
  }
`;

export const DELETE_YOUR_RESOURCE = `
  mutation DeleteYourResource($id: ID!) {
    deleteYourResource(input: {
      id: $id
    })
  }
`;
```

---

## Step 6: Frontend Component

### Create Component

Create: `src/components/YourResourceManager.tsx`

```typescript
import { useState, useEffect } from "react";
import { useToast } from "@/hooks/use-toast";
import { executeGraphQL, GET_GROUPS } from "@/lib/graphql";
import {
  GET_YOUR_RESOURCES,
  CREATE_YOUR_RESOURCE,
  UPDATE_YOUR_RESOURCE,
  DELETE_YOUR_RESOURCE
} from "@/lib/your-resource-queries";

interface YourResource {
  id: string;
  title: string;
  status: string;
  groupId: string;
  createdAt: string;
}

export const YourResourceManager = () => {
  const [resources, setResources] = useState<YourResource[]>([]);
  const [groups, setGroups] = useState<any[]>([]);
  const { toast } = useToast();

  // Fetch data
  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [resourcesRes, groupsRes] = await Promise.all([
        executeGraphQL<{ yourResources: YourResource[] }>(GET_YOUR_RESOURCES),
        executeGraphQL<{ checkboxGroups: any[] }>(GET_GROUPS)
      ]);
      setResources(resourcesRes.yourResources);
      setGroups(groupsRes.checkboxGroups);
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to load data",
        variant: "destructive"
      });
    }
  };

  // Create, update, delete methods...
  // (See GroupManager.tsx.example for full implementation)

  return (
    <div>
      {/* Your UI here */}
    </div>
  );
};
```

---

## Step 7: Testing

### Backend Tests

```bash
# Compile and build
./gradlew clean build

# Run tests (if any)
./gradlew test
```

### Database Tests

```bash
# Apply migrations
supabase db reset

# Test RLS policies manually
psql -h localhost -p 54322 -U postgres -d postgres
```

Test queries as different users:
```sql
-- Test as group member
SET request.jwt.claims = '{"sub": "user-uuid", "role": "authenticated"}';
SELECT * FROM your_resources WHERE group_id = 'group-uuid';

-- Should return results if user is member
```

### Frontend Tests

```bash
# Build
npm run build

# Run dev server
npm run dev
```

Manual testing:
1. Create a group
2. Add a resource to the group
3. Update the resource
4. Delete the resource
5. Try accessing as non-member (should fail)
6. Try as admin (should bypass)

---

## Common Patterns

### Soft Delete

Add `deleted_at` column:
```sql
ALTER TABLE public.your_resources
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
```

Update RLS policies to exclude deleted:
```sql
USING (
    deleted_at IS NULL
    AND (public.is_group_member(group_id) OR public.is_admin())
)
```

### Status Workflow

Add validation trigger:
```sql
CREATE OR REPLACE FUNCTION validate_status_transition()
RETURNS TRIGGER AS $$
BEGIN
    -- Only allow specific transitions
    IF OLD.status = 'completed' AND NEW.status != 'completed' THEN
        RAISE EXCEPTION 'Cannot reopen completed resource';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

### Audit Log

Create audit table:
```sql
CREATE TABLE public.your_resources_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id UUID NOT NULL,
    action TEXT NOT NULL,
    old_value JSONB,
    new_value JSONB,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
```

### File Attachments

Add file reference:
```sql
ALTER TABLE public.your_resources
    ADD COLUMN file_url TEXT,
    ADD COLUMN file_name TEXT,
    ADD COLUMN file_size_bytes BIGINT;
```

Use Supabase Storage for actual files.

---

## Troubleshooting

### RLS Policy Not Working
- Check `is_group_member()` function exists
- Verify user is actually a group member
- Test with admin user (should bypass)
- Check JWT token has correct claims

### Resolver Not Found
- Ensure schema has `@resolver` directive
- Run `./gradlew build` to regenerate
- Check package names match
- Verify resolver extends correct base class

### GlobalID Errors
- Use `.internalID` to decode GlobalIDs
- Use `ctx.globalIDFor()` to encode
- Ensure type implements `Node` interface
- Check `@idOf` directive in schema

### Frontend 404 Errors
- Verify GraphQL endpoint is correct
- Check authentication token is present
- Ensure user is logged in
- Check browser console for errors

---

## Next Steps

1. **Add pagination** for large lists
2. **Implement search** functionality
3. **Add real-time updates** with Supabase Realtime
4. **Create comprehensive tests**
5. **Add data validation** rules
6. **Implement caching** with React Query
7. **Add error boundaries** in frontend
8. **Create admin views** for monitoring

---

## Resources

- [Checklist Example](../backend/src/main/kotlin/com/viaduct/examples/checklist/)
- [Viaduct Documentation](https://viaduct.ai/docs)
- [Supabase RLS Guide](https://supabase.com/docs/guides/auth/row-level-security)
- [GraphQL Best Practices](https://graphql.org/learn/best-practices/)
