package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import viaduct.api.Resolver
import viaduct.api.grts.ChecklistItem

/**
 * Resolver for the updateChecklistItem mutation.
 * Updates a checklist item (completion status and/or title).
 * Authorization: Viaduct @requiresGroupMembership policy ensures only group members can update.
 */
@Resolver
class UpdateChecklistItemResolver : MutationResolvers.UpdateChecklistItem() {
    override suspend fun resolve(ctx: Context): ChecklistItem {
        val input = ctx.arguments.input
        // Use Viaduct's internalID property to get the UUID
        val itemId = input.id.internalID

        val client = ctx.authenticatedClient

        // Update the item with the provided values (null values are not updated)
        val itemEntity = client.updateChecklistItem(
            id = itemId,
            completed = input.completed,
            title = input.title
        )

        return ChecklistItem.Builder(ctx)
            .id(input.id)  // Reuse the GlobalID from input instead of regenerating
            .title(itemEntity.title)
            .completed(itemEntity.completed)
            .userId(itemEntity.user_id)
            .groupId(itemEntity.group_id)
            .createdAt(itemEntity.created_at)
            .updatedAt(itemEntity.updated_at)
            .build()
    }
}
