package com.graphqlcheckmate.services

import com.graphqlcheckmate.SupabaseService
import com.graphqlcheckmate.UserEntity

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
    suspend fun getAllUsers(requestContext: Any?): List<UserEntity> {
        val client = supabaseService.getAuthenticatedClient(requestContext)
        return client.getAllUsers()
    }

    /**
     * Set a user's admin status
     * Only admins can call this
     */
    suspend fun setUserAdmin(requestContext: Any?, userId: String, isAdmin: Boolean) {
        val client = supabaseService.getAuthenticatedClient(requestContext)
        client.callSetUserAdmin(userId, isAdmin)
    }

    /**
     * Delete a user from the system
     * Only admins can call this
     */
    suspend fun deleteUser(requestContext: Any?, userId: String): Boolean {
        val client = supabaseService.getAuthenticatedClient(requestContext)
        return client.deleteUser(userId)
    }
}
