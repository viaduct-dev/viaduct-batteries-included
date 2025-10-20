package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import java.util.Base64

/**
 * Resolver for the deleteChecklistItem mutation.
 * Deletes a checklist item.
 * Only members of the item's group can delete it (enforced by RLS).
 */
@Resolver
class DeleteChecklistItemResolver(
    private val groupService: GroupService
) : MutationResolvers.DeleteChecklistItem() {
    override suspend fun resolve(ctx: Context): Boolean {
        val input = ctx.arguments.input
        // Decode the GlobalID to get the internal UUID
        val decoded = String(Base64.getDecoder().decode(input.id))
        val itemId = decoded.substringAfter(":")

        val client = groupService.supabaseService.getAuthenticatedClient(ctx.requestContext)
        return client.deleteChecklistItem(itemId)
    }
}
