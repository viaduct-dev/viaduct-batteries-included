package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.AuthenticatedSupabaseClient
import com.graphqlcheckmate.SupabaseService
import viaduct.api.Resolver
import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers

@Resolver
class DeleteChecklistItemResolver(
    private val supabaseService: SupabaseService
) : MutationResolvers.DeleteChecklistItem() {
    override suspend fun resolve(ctx: Context): Boolean {
        // Get authenticated client from request context
        val authenticatedClient = supabaseService.getAuthenticatedClient(ctx.requestContext)

        val input = ctx.arguments.input
        val internalId = input.id.internalID
        return authenticatedClient.deleteChecklistItem(internalId)
    }
}
