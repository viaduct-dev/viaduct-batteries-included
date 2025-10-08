package com.graphqlcheckmate.services

import com.graphqlcheckmate.ChecklistItemEntity
import com.graphqlcheckmate.SupabaseService

/**
 * Service for managing checklist items
 */
class ChecklistItemService(
    private val supabaseService: SupabaseService
) {
    /**
     * Get all checklist items for the authenticated user
     * RLS policies will automatically filter by user
     */
    suspend fun getChecklistItems(requestContext: Any?): List<ChecklistItemEntity> {
        val client = supabaseService.getAuthenticatedClient(requestContext)
        return client.getChecklistItems()
    }

    /**
     * Get a checklist item by ID
     * RLS policies will ensure the user can only access their own items
     */
    suspend fun getChecklistItemById(requestContext: Any?, id: String): ChecklistItemEntity? {
        val client = supabaseService.getAuthenticatedClient(requestContext)
        return client.getChecklistItemById(id)
    }

    /**
     * Create a new checklist item
     */
    suspend fun createChecklistItem(
        requestContext: Any?,
        title: String,
        userId: String
    ): ChecklistItemEntity {
        val client = supabaseService.getAuthenticatedClient(requestContext)
        return client.createChecklistItem(title, userId)
    }

    /**
     * Update a checklist item
     */
    suspend fun updateChecklistItem(
        requestContext: Any?,
        id: String,
        completed: Boolean
    ): ChecklistItemEntity {
        val client = supabaseService.getAuthenticatedClient(requestContext)
        return client.updateChecklistItem(id, completed)
    }

    /**
     * Delete a checklist item
     */
    suspend fun deleteChecklistItem(requestContext: Any?, id: String): Boolean {
        val client = supabaseService.getAuthenticatedClient(requestContext)
        return client.deleteChecklistItem(id)
    }
}
