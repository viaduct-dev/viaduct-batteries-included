package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.SupabaseService
import viaduct.api.Resolver
import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers

@Resolver
class SetUserAdminResolver(
    private val supabaseService: SupabaseService
) : MutationResolvers.SetUserAdmin() {
    override suspend fun resolve(ctx: Context): Boolean {
        // Get authenticated client from request context
        val authenticatedClient = supabaseService.getAuthenticatedClient(ctx.requestContext)

        val input = ctx.arguments.input

        // Call the set_user_admin PostgreSQL function
        // This function verifies that the caller is an admin via is_admin()
        // and updates the target user's app_metadata
        authenticatedClient.callSetUserAdmin(input.userId, input.isAdmin)

        return true
    }
}
