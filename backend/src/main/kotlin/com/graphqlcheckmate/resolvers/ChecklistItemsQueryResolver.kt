package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.QueryResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.ChecklistItem

/**
 * Resolver for the checklistItems query.
 * Returns all checklist items from groups the authenticated user is a member of.
 */
@Resolver
class ChecklistItemsQueryResolver(
    private val groupService: GroupService
) : QueryResolvers.ChecklistItems() {
    override suspend fun resolve(ctx: Context): List<ChecklistItem> {
        val client = groupService.supabaseService.getAuthenticatedClient(ctx.requestContext)
        val itemEntities = client.getChecklistItems()

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
