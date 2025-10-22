package com.graphqlcheckmate.services

import com.graphqlcheckmate.AuthenticatedSupabaseClient
import com.graphqlcheckmate.SupabaseService
import com.graphqlcheckmate.config.RequestContext
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CheckboxGroupEntity(
    val id: String,
    val name: String,
    val description: String? = null,
    val owner_id: String,
    val created_at: String,
    val updated_at: String
)

@Serializable
data class GroupMemberEntity(
    val id: String,
    val group_id: String,
    val user_id: String,
    val joined_at: String
)

@Serializable
data class CreateGroupInput(
    val name: String,
    val description: String? = null,
    val owner_id: String
)

@Serializable
data class AddMemberInput(
    val group_id: String,
    val user_id: String
)

/**
 * Service for managing checkbox groups and group memberships.
 * Handles group creation, membership checks, and group-related queries.
 */
open class GroupService(
    internal val supabaseService: SupabaseService
) {
    private val httpClient = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Check if a user is a member of a specific group.
     * This is called by the policy executor to enforce per-row access control.
     * Uses an authenticated client from the request context to respect RLS policies.
     */
    open suspend fun isUserMemberOfGroup(userId: String, groupId: String, requestContext: RequestContext): Boolean {
        val members = requestContext.authenticatedClient.getGroupMembers(groupId)
        return members.any { it.user_id == userId }
    }

    /**
     * Get all groups that a user is a member of.
     */
    suspend fun getUserGroups(authenticatedClient: AuthenticatedSupabaseClient): List<CheckboxGroupEntity> {
        return authenticatedClient.getCheckboxGroups()
    }

    /**
     * Get all groups that a user is a member of.
     * @deprecated Use getUserGroups(AuthenticatedSupabaseClient) instead
     */
    @Deprecated("Use getUserGroups(AuthenticatedSupabaseClient) instead", ReplaceWith("getUserGroups(requestContext.authenticatedClient)"))
    suspend fun getUserGroups(requestContext: RequestContext): List<CheckboxGroupEntity> {
        return getUserGroups(requestContext.authenticatedClient)
    }

    /**
     * Get a specific group by ID.
     */
    suspend fun getGroupById(requestContext: RequestContext, groupId: String): CheckboxGroupEntity? {
        return requestContext.authenticatedClient.getCheckboxGroupById(groupId)
    }

    /**
     * Create a new checkbox group.
     * The creator is automatically added as a member via database trigger.
     */
    suspend fun createGroup(
        requestContext: RequestContext,
        name: String,
        description: String?,
        ownerId: String
    ): CheckboxGroupEntity {
        return requestContext.authenticatedClient.createCheckboxGroup(name, description, ownerId)
    }

    /**
     * Get all members of a group.
     */
    suspend fun getGroupMembers(requestContext: RequestContext, groupId: String): List<GroupMemberEntity> {
        return requestContext.authenticatedClient.getGroupMembers(groupId)
    }

    /**
     * Add a member to a group.
     */
    suspend fun addGroupMember(
        requestContext: RequestContext,
        groupId: String,
        userId: String
    ): GroupMemberEntity {
        return requestContext.authenticatedClient.addGroupMember(groupId, userId)
    }

    /**
     * Remove a member from a group.
     */
    suspend fun removeGroupMember(
        requestContext: RequestContext,
        groupId: String,
        userId: String
    ): Boolean {
        return requestContext.authenticatedClient.removeGroupMember(groupId, userId)
    }
}
