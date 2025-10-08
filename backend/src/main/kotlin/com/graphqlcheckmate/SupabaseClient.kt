package com.graphqlcheckmate

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ChecklistItemEntity(
    val id: String,
    val title: String,
    val completed: Boolean,
    val user_id: String,
    val created_at: String,
    val updated_at: String
)

/**
 * Input for creating a new checklist item
 */
@Serializable
data class CreateChecklistItemInput(
    val title: String,
    val user_id: String,
    val completed: Boolean = false
)

/**
 * Input for updating a checklist item
 */
@Serializable
data class UpdateChecklistItemInput(
    val completed: Boolean
)

/**
 * Request context that can be safely serialized
 * Contains only the user ID, not the authenticated client (which is not serializable)
 */
@Serializable
data class GraphQLRequestContext(
    val userId: String,
    val accessToken: String
)

class SupabaseService(
    private val supabaseUrl: String,
    private val supabaseKey: String
) {
    // Admin client for token verification only
    private val adminClient: SupabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey
    ) {
        install(Auth)
    }

    /**
     * Verify a JWT access token with Supabase Auth
     * Returns the user info if valid, throws exception if invalid
     */
    suspend fun verifyToken(accessToken: String): UserInfo {
        // Use the admin client to verify the token by fetching user info
        // This makes a request to Supabase Auth to validate the JWT
        val response = adminClient.auth.retrieveUser(accessToken)
        return response
    }

    /**
     * Create an authenticated Supabase client for a specific user
     * This client will use the user's JWT token, enabling RLS policies
     */
    fun createAuthenticatedClient(accessToken: String): AuthenticatedSupabaseClient {
        // Create a client that will use the user's access token
        // Supabase RLS will automatically use the JWT claims from this token
        val client = createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = accessToken // Use the user's JWT instead of the anon key
        ) {
            install(Postgrest)
        }

        return AuthenticatedSupabaseClient(client)
    }

    /**
     * Helper function to extract authenticated client from request context
     * This should be called by resolvers to get a client for database operations
     */
    fun getAuthenticatedClient(requestContext: Any?): AuthenticatedSupabaseClient {
        val context = requestContext as? GraphQLRequestContext
            ?: throw IllegalArgumentException("Authentication required: invalid or missing request context")

        return createAuthenticatedClient(context.accessToken)
    }
}

/**
 * Wrapper for an authenticated Supabase client
 * This client uses the user's JWT token, so RLS policies will be enforced automatically
 */
class AuthenticatedSupabaseClient(
    private val client: SupabaseClient
) {

    /**
     * Get all checklist items for the authenticated user
     * RLS policies will automatically filter by the user's ID from the JWT
     */
    suspend fun getChecklistItems(): List<ChecklistItemEntity> {
        return client.from("checklist_items")
            .select()
            .decodeList<ChecklistItemEntity>()
    }

    /**
     * Get a checklist item by ID
     * RLS policies will ensure the user can only access their own items
     */
    suspend fun getChecklistItemById(id: String): ChecklistItemEntity? {
        return client.from("checklist_items")
            .select {
                filter {
                    eq("id", id)
                }
            }
            .decodeSingleOrNull<ChecklistItemEntity>()
    }

    /**
     * Create a new checklist item
     * RLS policies will automatically set user_id from the JWT
     */
    suspend fun createChecklistItem(title: String, userId: String): ChecklistItemEntity {
        return client.from("checklist_items")
            .insert(
                CreateChecklistItemInput(
                    title = title,
                    user_id = userId,
                    completed = false
                )
            ) {
                select()
            }
            .decodeSingle<ChecklistItemEntity>()
    }

    /**
     * Update a checklist item
     * RLS policies will ensure the user can only update their own items
     */
    suspend fun updateChecklistItem(id: String, completed: Boolean): ChecklistItemEntity {
        return client.from("checklist_items")
            .update(
                UpdateChecklistItemInput(completed = completed)
            ) {
                filter {
                    eq("id", id)
                }
                select()
            }
            .decodeSingle<ChecklistItemEntity>()
    }

    /**
     * Delete a checklist item
     * RLS policies will ensure the user can only delete their own items
     */
    suspend fun deleteChecklistItem(id: String): Boolean {
        client.from("checklist_items")
            .delete {
                filter {
                    eq("id", id)
                }
            }
        return true
    }
}
