package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.SupabaseService
import viaduct.api.Resolver
import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers

@Resolver
class DeleteUserResolver(
    private val supabaseService: SupabaseService
) : MutationResolvers.DeleteUser() {
    override suspend fun resolve(ctx: Context): Boolean {
        // Get authenticated client from request context
        val authenticatedClient = supabaseService.getAuthenticatedClient(ctx.requestContext)

        val input = ctx.arguments.input
        return authenticatedClient.deleteUser(input.userId)
    }
}
