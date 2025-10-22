package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.QueryResolvers
import viaduct.api.Resolver
import viaduct.api.grts.ChecklistItem

/**
 * Resolver for the checklistItems query.
 * Returns all checklist items from groups the authenticated user is a member of.
 * Authorization: Database RLS policies enforce access control.
 */
@Resolver
class ChecklistItemsQueryResolver : QueryResolvers.ChecklistItems() {
    override suspend fun resolve(ctx: Context): List<ChecklistItem> {
        val itemEntities = ctx.authenticatedClient.getChecklistItems()

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
