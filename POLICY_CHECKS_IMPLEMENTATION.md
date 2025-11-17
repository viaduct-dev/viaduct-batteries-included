# Per-Row Policy Checks with Group Membership

This document describes the implementation of per-row policy checks using Viaduct's CheckerExecutor feature for group-based access control in Viaduct Template.

## Overview

We've implemented a group-based access control system where:
- Users can create **checkbox groups**
- Groups have **members** (many-to-many relationship)
- Checklist items belong to **groups**
- **Per-row policy checks** verify group membership before allowing access to items

This demonstrates Viaduct's policy check feature with custom `@requiresGroupMembership` directive.

## Architecture

### Database Layer (RLS + Application Logic)
- `checkbox_groups` table: stores group information
- `group_members` table: manages group memberships
- `checklist_items.group_id`: links items to groups
- RLS policies enforce group membership at the database level
- Application-level policy checks provide GraphQL-specific error messages

### GraphQL Layer (Viaduct Policy Checks)

#### 1. Custom Directive (`PolicyDirective.graphqls`)
```graphql
directive @requiresGroupMembership(
  groupIdField: String = "groupId"
) on FIELD_DEFINITION | OBJECT
```

Applied to types and fields that require group membership verification:
```graphql
type ChecklistItem implements Node @requiresGroupMembership(groupIdField: "groupId") {
  id: ID!
  title: String!
  groupId: String
  # ...
}

type CheckboxGroup implements Node @requiresGroupMembership(groupIdField: "id") {
  id: ID!
  name: String!
  # ...
}
```

#### 2. Policy Executor (`GroupMembershipPolicyExecutor.kt`)
Implements `CheckerExecutor` interface to perform the actual policy check:

```kotlin
class GroupMembershipPolicyExecutor(
    private val groupIdFieldName: String,
    private val groupService: GroupService
) : CheckerExecutor {
    override suspend fun execute(
        arguments: Map<String, Any?>,
        objectDataMap: Map<String, EngineObjectData>,
        context: EngineExecutionContext
    ): CheckerResult {
        // Extract user ID from request context
        val userId = (context.requestContext as GraphQLRequestContext).userId

        // Get group ID from object data or arguments
        val groupId = extractGroupId(objectDataMap, arguments)

        // Check membership via GroupService
        return if (groupService.isUserMemberOfGroup(userId, groupId)) {
            CheckerResult.Success
        } else {
            GroupMembershipErrorResult(
                RuntimeException("Access denied: You are not a member of this group")
            )
        }
    }
}
```

**Key Features:**
- Reads group ID from resolved object data
- Verifies user membership via database query
- Returns clear error messages on access denial
- Supports null group IDs for backward compatibility

#### 3. Checker Factory (`GroupMembershipCheckerFactory.kt`)
Implements `CheckerExecutorFactory` to create policy executors based on schema directives:

```kotlin
class GroupMembershipCheckerFactory(
    private val schema: ViaductSchema,
    private val groupService: GroupService
) : CheckerExecutorFactory {
    override fun checkerExecutorForField(
        typeName: String,
        fieldName: String
    ): CheckerExecutor? {
        val field = schema.schema.getObjectType(typeName)
            ?.getFieldDefinition(fieldName)
            ?: return null

        if (!field.hasAppliedDirective("requiresGroupMembership")) {
            return null
        }

        val directive = field.getAppliedDirective("requiresGroupMembership")
        val groupIdField = directive.getArgument("groupIdField")?.getValue() as? String ?: "groupId"

        return GroupMembershipPolicyExecutor(groupIdField, groupService)
    }

    override fun checkerExecutorForType(typeName: String): CheckerExecutor? {
        // Similar implementation for types
    }
}
```

#### 4. Group Service (`GroupService.kt`)
Provides membership verification and group management:

```kotlin
class GroupService(private val supabaseService: SupabaseService) {
    suspend fun isUserMemberOfGroup(userId: String, groupId: String): Boolean {
        // Query group_members table
        val members = /* database query */
        return members.isNotEmpty()
    }

    // Other group management methods...
}
```

## Policy Check Flow

When a GraphQL query requests a field with `@requiresGroupMembership`:

1. **Query Execution Starts**
   ```graphql
   query {
     checklistItems {
       id
       title
       groupId
     }
   }
   ```

2. **Viaduct Identifies Policy Check**
   - Sees `ChecklistItem` type has `@requiresGroupMembership` directive
   - Calls `GroupMembershipCheckerFactory.checkerExecutorForType("ChecklistItem")`

3. **Factory Creates Executor**
   - Reads `groupIdField` parameter from directive
   - Creates `GroupMembershipPolicyExecutor` with appropriate configuration

4. **Policy Check Executes**
   - Extracts user ID from request context
   - Extracts group ID from resolved object data
   - Queries database for membership: `SELECT * FROM group_members WHERE user_id = ? AND group_id = ?`

5. **Result Handling**
   - **Success**: Field resolves normally
   - **Failure**: Field returns `null` with error in GraphQL response:
     ```json
     {
       "data": { "checklistItems": null },
       "errors": [{
         "message": "Access denied: You are not a member of this group",
         "path": ["checklistItems", 0]
       }]
     }
     ```

## Registration (TODO)

**Current Status**: The policy check implementation is complete, but registration with Viaduct is pending.

`BasicViaductFactory` (used in `Application.kt`) doesn't expose `CheckerExecutorFactory` registration. To complete the integration:

### Option 1: Use StandardViaduct.Builder (Recommended)

Add explicit dependency in `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.airbnb.viaduct:service-wiring:0.4.0")
    // ...
}
```

Then update `Application.kt`:
```kotlin
import viaduct.service.runtime.StandardViaduct
import viaduct.service.runtime.SchemaConfiguration

val viaduct = StandardViaduct.Builder()
    .withTenantAPIBootstrapperBuilder(/* ... */)
    .withSchemaConfiguration(/* ... */)
    .withCheckerExecutorFactoryCreator { viaductSchema ->
        GroupMembershipCheckerFactory(viaductSchema, groupService)
    }
    .build()
```

### Option 2: Implement in Resolvers

Move policy checks into resolver logic as a workaround:
```kotlin
@Resolver
class ChecklistItemsQueryResolver(
    private val groupService: GroupService
) : QueryResolvers.ChecklistItems() {
    override suspend fun resolve(ctx: Context): List<ChecklistItem> {
        val userId = (ctx.requestContext as GraphQLRequestContext).userId

        val items = /* fetch from database */

        // Manual policy check
        return items.filter { item ->
            item.groupId == null || groupService.isUserMemberOfGroup(userId, item.groupId)
        }
    }
}
```

## Benefits of This Approach

1. **Separation of Concerns**: Authorization logic is separate from business logic
2. **Declarative Security**: Use GraphQL directives to mark protected fields/types
3. **Per-Row Granularity**: Each object is checked individually
4. **Clear Error Messages**: Users understand why access was denied
5. **Reusable**: The `@requiresGroupMembership` directive can be applied to any type/field
6. **Type-Safe**: Leverages Viaduct's generated types and resolver bases

## Testing

Test policy checks using Viaduct's test utilities:

```kotlin
@Test
fun `access denied when not group member`() {
    val groupId = "group-123"
    val nonMemberUserId = "user-456"

    MockTenantModuleBootstrapper(SDL) {
        field("Query" to "checklistItemsByGroup") {
            resolver {
                fn { _, _, _, _, _ ->
                    listOf(ChecklistItem(id = "item-1", groupId = groupId))
                }
            }
            checker {
                fn { args, objectDataMap ->
                    val groupId = args["groupId"] as String
                    if (!groupService.isUserMemberOfGroup(nonMemberUserId, groupId)) {
                        throw RuntimeException("Access denied")
                    }
                }
            }
        }
    }.runFeatureTest {
        val result = viaduct.runQuery(
            "query { checklistItemsByGroup(groupId: \"$groupId\") { id } }",
            requestContext = GraphQLRequestContext(nonMemberUserId, "token")
        )

        assertNull(result.getData()["checklistItemsByGroup"])
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors[0].message.contains("Access denied"))
    }
}
```

## Files Modified/Created

### Database
- `supabase/migrations/20251008000000_initial_schema.sql` - Initial checklist_items table
- `supabase/migrations/20251017000000_add_checkbox_groups.sql` - Groups, members, and updated RLS policies

### GraphQL Schema
- `backend/src/main/viaduct/schema/PolicyDirective.graphqls` - Custom directive
- `backend/src/main/viaduct/schema/CheckboxGroup.graphqls` - Group types and operations
- `backend/src/main/viaduct/schema/ChecklistItem.graphqls` - Updated with group support

### Kotlin Implementation
- `backend/src/main/kotlin/com/viaduct/policy/GroupMembershipPolicyExecutor.kt` - Policy executor
- `backend/src/main/kotlin/com/viaduct/policy/GroupMembershipCheckerFactory.kt` - Executor factory
- `backend/src/main/kotlin/com/viaduct/services/GroupService.kt` - Group operations and membership checks
- `backend/src/main/kotlin/com/viaduct/SupabaseClient.kt` - Added group-related database methods
- `backend/src/main/kotlin/com/viaduct/config/KoinModule.kt` - Registered GroupService

## Next Steps

1. **Complete Viaduct Integration**: Add service-wiring dependency and register CheckerExecutorFactory
2. **Implement Resolvers**: Create resolver implementations for all group-related queries/mutations
3. **Add Tests**: Write unit and integration tests for policy checks
4. **Frontend Integration**: Update React app to work with groups
5. **Documentation**: Add API documentation and usage examples
