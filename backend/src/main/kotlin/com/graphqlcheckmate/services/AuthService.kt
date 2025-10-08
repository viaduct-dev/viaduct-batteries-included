package com.graphqlcheckmate.services

import com.graphqlcheckmate.AuthenticatedSupabaseClient
import com.graphqlcheckmate.GraphQLRequestContext
import com.graphqlcheckmate.SupabaseService
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Service for handling authentication and authorization
 */
class AuthService(
    private val supabaseService: SupabaseService
) {
    /**
     * Verify a JWT access token with Supabase Auth
     * Returns the user info if valid, throws exception if invalid
     */
    suspend fun verifyToken(accessToken: String): UserInfo {
        return supabaseService.verifyToken(accessToken)
    }

    /**
     * Create a GraphQL request context from an access token
     * This extracts user information and admin status from the token
     */
    suspend fun createRequestContext(accessToken: String): GraphQLRequestContext {
        val userInfo = verifyToken(accessToken)

        // Extract admin status from app_metadata
        val isAdminValue = userInfo.appMetadata?.get("is_admin")
        val isAdmin = when (isAdminValue) {
            is JsonPrimitive -> isAdminValue.booleanOrNull ?: false
            else -> false
        }

        return GraphQLRequestContext(
            userId = userInfo.id,
            accessToken = accessToken,
            isAdmin = isAdmin
        )
    }

    /**
     * Get an authenticated Supabase client for the given request context
     */
    fun getAuthenticatedClient(requestContext: Any?): AuthenticatedSupabaseClient {
        return supabaseService.getAuthenticatedClient(requestContext)
    }

    /**
     * Extract the schema ID based on whether the user is an admin
     */
    fun getSchemaId(requestContext: GraphQLRequestContext): String {
        return if (requestContext.isAdmin) "admin" else "default"
    }
}
