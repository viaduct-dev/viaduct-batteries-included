package com.graphqlcheckmate.services

import com.graphqlcheckmate.SupabaseService
import com.graphqlcheckmate.UserEntity
import com.graphqlcheckmate.config.RequestContext

/**
 * Service for managing users and admin operations
 */
class UserService(
    private val supabaseService: SupabaseService
) {
    /**
     * Get all users in the system
     * Only admins can call this
     */
    suspend fun getAllUsers(requestContext: RequestContext): List<UserEntity> {
        return requestContext.authenticatedClient.getAllUsers()
    }

    /**
     * Search for users by email
     * Available to all authenticated users
     */
    suspend fun searchUsers(requestContext: RequestContext, query: String): List<UserEntity> {
        return requestContext.authenticatedClient.searchUsers(query)
    }

    /**
     * Set a user's admin status
     * Only admins can call this
     */
    suspend fun setUserAdmin(requestContext: RequestContext, userId: String, isAdmin: Boolean) {
        requestContext.authenticatedClient.callSetUserAdmin(userId, isAdmin)
    }

    /**
     * Delete a user from the system
     * Only admins can call this
     */
    suspend fun deleteUser(requestContext: RequestContext, userId: String): Boolean {
        return requestContext.authenticatedClient.deleteUser(userId)
    }
}
