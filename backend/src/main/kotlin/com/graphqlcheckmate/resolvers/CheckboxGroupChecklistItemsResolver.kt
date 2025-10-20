package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.CheckboxGroupResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.ChecklistItem

/**
 * Field resolver for CheckboxGroup.checklistItems.
 * Returns all checklist items belonging to the checkbox group.
 */
@Resolver(objectValueFragment = "fragment _ on CheckboxGroup { id }")
class CheckboxGroupChecklistItemsResolver(
    private val groupService: GroupService
) : CheckboxGroupResolvers.ChecklistItems() {
    override suspend fun resolve(ctx: Context): List<ChecklistItem> {
        // Access parent CheckboxGroup via objectValue
        val groupId = ctx.objectValue.getId().internalID

        val client = groupService.supabaseService.getAuthenticatedClient(ctx.requestContext)
        val itemEntities = client.getChecklistItemsByGroup(groupId)

        return itemEntities.map { entity ->
            ChecklistItem.Builder(ctx)
                .id(ctx.globalIDFor(ChecklistItem.Reflection, entity.id))
                .title(entity.title)
                .completed(entity.completed)
                .userId(entity.user_id)
                .groupId(entity.group_id)
                .createdAt(entity.created_at)
                .updatedAt(entity.updated_at)
                .build()
        }
    }
}
