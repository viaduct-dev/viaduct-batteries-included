package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.CheckboxGroupResolvers
import viaduct.api.Resolver
import viaduct.api.grts.ChecklistItem

/**
 * Field resolver for CheckboxGroup.checklistItems.
 * Returns all checklist items belonging to the checkbox group.
 * Authorization: Database RLS policies enforce access control.
 */
@Resolver(objectValueFragment = "fragment _ on CheckboxGroup { id }")
class CheckboxGroupChecklistItemsResolver : CheckboxGroupResolvers.ChecklistItems() {
    override suspend fun resolve(ctx: Context): List<ChecklistItem> {
        // Access parent CheckboxGroup via objectValue
        val groupId = ctx.objectValue.getId().internalID

        val itemEntities = ctx.authenticatedClient.getChecklistItemsByGroup(groupId)

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
