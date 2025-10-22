package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import viaduct.api.Resolver
import viaduct.api.grts.ChecklistItem

/**
 * Resolver for the createChecklistItem mutation.
 * Creates a new checklist item in a group.
 * Authorization: Database RLS policies enforce that only group members can create items.
 */
@Resolver
class CreateChecklistItemResolver : MutationResolvers.CreateChecklistItem() {
    override suspend fun resolve(ctx: Context): ChecklistItem {
        val input = ctx.arguments.input

        // Get the user ID from extension property
        val userId = ctx.userId

        // Use Viaduct's internalID property to get the UUID
        val groupId = input.groupId.internalID

        val client = ctx.authenticatedClient
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
