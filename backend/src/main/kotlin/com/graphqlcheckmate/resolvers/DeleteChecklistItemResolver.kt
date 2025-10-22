package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import viaduct.api.Resolver

/**
 * Resolver for the deleteChecklistItem mutation.
 * Deletes a checklist item.
 * Authorization: Database RLS policies enforce that only group members can delete items.
 */
@Resolver
class DeleteChecklistItemResolver : MutationResolvers.DeleteChecklistItem() {
    override suspend fun resolve(ctx: Context): Boolean {
        val input = ctx.arguments.input
        // Use Viaduct's internalID property to get the UUID
        val itemId = input.id.internalID

        val client = ctx.authenticatedClient
        return client.deleteChecklistItem(itemId)
    }
}
