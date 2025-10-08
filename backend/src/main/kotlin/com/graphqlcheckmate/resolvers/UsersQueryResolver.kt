package com.graphqlcheckmate.resolvers

import viaduct.api.grts.User
import com.graphqlcheckmate.SupabaseService
import com.graphqlcheckmate.resolvers.resolverbases.QueryResolvers
import viaduct.api.Resolver
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@Resolver
class UsersQueryResolver(
    private val supabaseService: SupabaseService
) : QueryResolvers.Users() {
    override suspend fun resolve(ctx: Context): List<User> {
        // Get authenticated client from request context
        val authenticatedClient = supabaseService.getAuthenticatedClient(ctx.requestContext)

        // Get all users from auth.users
        val userEntities = authenticatedClient.getAllUsers()

        return userEntities.map { entity ->
            // Extract is_admin from raw_app_meta_data
            val isAdminValue = entity.raw_app_meta_data?.get("is_admin")
            val isAdmin = when (isAdminValue) {
                is JsonPrimitive -> isAdminValue.booleanOrNull ?: false
                else -> false
            }

            User.Builder(ctx)
                .id(entity.id)
                .email(entity.email ?: "")
                .isAdmin(isAdmin)
                .createdAt(entity.created_at)
                .build()
        }
    }
}
