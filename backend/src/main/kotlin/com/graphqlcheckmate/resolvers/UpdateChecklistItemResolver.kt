package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.ChecklistItem
import java.util.Base64

/**
 * Resolver for the updateChecklistItem mutation.
 * Updates a checklist item (completion status and/or title).
 * Only members of the item's group can update it (enforced by RLS).
 */
@Resolver
class UpdateChecklistItemResolver(
    private val groupService: GroupService
) : MutationResolvers.UpdateChecklistItem() {
    override suspend fun resolve(ctx: Context): ChecklistItem {
        val input = ctx.arguments.input
        // Decode the GlobalID to get the internal UUID
        val decoded = String(Base64.getDecoder().decode(input.id))
        val itemId = decoded.substringAfter(":")

        val client = groupService.supabaseService.getAuthenticatedClient(ctx.requestContext)

        // Authorization is checked by Viaduct policy executor before this resolver runs
        // The @requiresGroupMembership directive on the mutation ensures only group members can update

        // Update the item with the provided values (null values are not updated)
        val itemEntity = client.updateChecklistItem(
            id = itemId,
            completed = input.completed,
            title = input.title
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
