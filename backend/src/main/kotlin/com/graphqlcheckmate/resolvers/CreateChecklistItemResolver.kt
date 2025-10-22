package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.config.RequestContext
import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.ChecklistItem
import java.util.Base64

/**
 * Resolver for the createChecklistItem mutation.
 * Creates a new checklist item in a group.
 * Only members of the group can create items (enforced by RLS).
 */
@Resolver
class CreateChecklistItemResolver(
    private val groupService: GroupService
) : MutationResolvers.CreateChecklistItem() {
    override suspend fun resolve(ctx: Context): ChecklistItem {
        val input = ctx.arguments.input

        // Extract the request context
        val requestContext = ctx.requestContext as RequestContext

        // Get the user ID from the GraphQL context
        val userId = requestContext.graphQLContext.userId

        // Decode the GlobalID to get the internal UUID
        val decoded = String(Base64.getDecoder().decode(input.groupId))
        val groupId = decoded.substringAfter(":")

        val client = requestContext.authenticatedClient
        val itemEntity = client.createChecklistItem(
            title = input.title,
            userId = userId,
            groupId = groupId
        )

        return ChecklistItem.Builder(ctx)
            .id(ctx.globalIDFor(ChecklistItem.Reflection, itemEntity.id))
            .title(itemEntity.title)
            .completed(itemEntity.completed)
            .userId(itemEntity.user_id)
            .groupId(itemEntity.group_id)
            .createdAt(itemEntity.created_at)
            .updatedAt(itemEntity.updated_at)
            .build()
    }
}
