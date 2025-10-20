package com.graphqlcheckmate.services

import com.graphqlcheckmate.AuthenticatedSupabaseClient
import com.graphqlcheckmate.SupabaseService
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
     * Uses an authenticated client to respect RLS policies.
     */
    open suspend fun isUserMemberOfGroup(userId: String, groupId: String, client: AuthenticatedSupabaseClient): Boolean {
        val members = client.getGroupMembers(groupId)
        return members.any { it.user_id == userId }
    }

    /**
     * Get all groups that a user is a member of.
     */
    suspend fun getUserGroups(client: AuthenticatedSupabaseClient): List<CheckboxGroupEntity> {
        return client.getCheckboxGroups()
    }

    /**
     * Get a specific group by ID.
     */
    suspend fun getGroupById(client: AuthenticatedSupabaseClient, groupId: String): CheckboxGroupEntity? {
        return client.getCheckboxGroupById(groupId)
    }

    /**
     * Create a new checkbox group.
     * The creator is automatically added as a member via database trigger.
     */
    suspend fun createGroup(
        client: AuthenticatedSupabaseClient,
        name: String,
        description: String?,
        ownerId: String
    ): CheckboxGroupEntity {
        return client.createCheckboxGroup(name, description, ownerId)
    }

    /**
     * Get all members of a group.
     */
    suspend fun getGroupMembers(client: AuthenticatedSupabaseClient, groupId: String): List<GroupMemberEntity> {
        return client.getGroupMembers(groupId)
    }

    /**
     * Add a member to a group.
     */
    suspend fun addGroupMember(
        client: AuthenticatedSupabaseClient,
        groupId: String,
        userId: String
    ): GroupMemberEntity {
        return client.addGroupMember(groupId, userId)
    }

    /**
     * Remove a member from a group.
     */
    suspend fun removeGroupMember(
        client: AuthenticatedSupabaseClient,
        groupId: String,
        userId: String
    ): Boolean {
        return client.removeGroupMember(groupId, userId)
    }
}
