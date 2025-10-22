package com.graphqlcheckmate.services

import com.graphqlcheckmate.AuthenticatedSupabaseClient
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
    suspend fun getAllUsers(authenticatedClient: AuthenticatedSupabaseClient): List<UserEntity> {
        return authenticatedClient.getAllUsers()
    }

    /**
     * Search for users by email
     * Available to all authenticated users
     */
    suspend fun searchUsers(authenticatedClient: AuthenticatedSupabaseClient, query: String): List<UserEntity> {
        return authenticatedClient.searchUsers(query)
    }

    /**
     * Set a user's admin status
     * Only admins can call this
     */
    suspend fun setUserAdmin(authenticatedClient: AuthenticatedSupabaseClient, userId: String, isAdmin: Boolean) {
        authenticatedClient.callSetUserAdmin(userId, isAdmin)
    }

    /**
     * Delete a user from the system
     * Only admins can call this
     */
    suspend fun deleteUser(authenticatedClient: AuthenticatedSupabaseClient, userId: String): Boolean {
        return authenticatedClient.deleteUser(userId)
    }
}
