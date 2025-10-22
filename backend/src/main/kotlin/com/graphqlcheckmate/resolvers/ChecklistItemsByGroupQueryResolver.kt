package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.QueryResolvers
import viaduct.api.Resolver
import viaduct.api.grts.ChecklistItem

/**
 * Resolver for the checklistItemsByGroup query.
 * Returns all checklist items for a specific group.
 * Authorization: Database RLS policies enforce that only group members can access items.
 */
@Resolver
class ChecklistItemsByGroupQueryResolver : QueryResolvers.ChecklistItemsByGroup() {
    override suspend fun resolve(ctx: Context): List<ChecklistItem> {
        // Use Viaduct's internalID property to get the UUID
        val groupId = ctx.arguments.groupId.internalID

        val client = ctx.authenticatedClient
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
