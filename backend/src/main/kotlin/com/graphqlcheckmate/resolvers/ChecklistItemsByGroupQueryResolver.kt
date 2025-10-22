package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.config.RequestContext
import com.graphqlcheckmate.resolvers.resolverbases.QueryResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.ChecklistItem
import java.util.Base64

/**
 * Resolver for the checklistItemsByGroup query.
 * Returns all checklist items for a specific group.
 * Only accessible if the user is a member of the group.
 */
@Resolver
class ChecklistItemsByGroupQueryResolver(
    private val groupService: GroupService
) : QueryResolvers.ChecklistItemsByGroup() {
    override suspend fun resolve(ctx: Context): List<ChecklistItem> {
        // Decode the GlobalID to get the internal UUID
        val globalIdString = ctx.arguments.groupId
        val decoded = String(Base64.getDecoder().decode(globalIdString))
        val groupId = decoded.substringAfter(":")

        val requestContext = ctx.requestContext as RequestContext
        val client = requestContext.authenticatedClient
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
