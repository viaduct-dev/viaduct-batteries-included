package com.example

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

@Serializable
data class UserEntity(
    val id: String,
    val email: String,
    val raw_app_meta_data: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val created_at: String
)

/**
 * Input for setting user admin status RPC call
 */
@Serializable
data class SetUserAdminInput(
    val target_user_id: String,
    val is_admin: Boolean
)

/**
 * Input for searching users RPC call
 */
@Serializable
data class SearchUsersInput(
    val search_query: String
)

/**
 * Input for deleting user RPC call
 */
@Serializable
data class DeleteUserInput(
    val user_id: String
)

/**
 * Request context that can be safely serialized
 * Contains only the user ID, not the authenticated client (which is not serializable)
 */
@Serializable
data class GraphQLRequestContext(
    val userId: String,
    val accessToken: String,
    val isAdmin: Boolean = false
)

/**
 * Auth session response from Supabase GoTrue API
 */
@Serializable
data class AuthSessionResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    val user: AuthUserResponse
)

/**
 * User info from Supabase GoTrue API
 */
@Serializable
data class AuthUserResponse(
    val id: String,
    val email: String? = null
)

/**
 * Sign in/up request body
 */
@Serializable
data class AuthCredentials(
    val email: String,
    val password: String
)

/**
 * Refresh token request body
 */
@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String
)

open class SupabaseService(
    val supabaseUrl: String,
    val supabaseKey: String,
    private val httpClient: HttpClient,
    val serviceRoleKey: String = System.getenv("SUPABASE_SERVICE_ROLE_KEY") ?: supabaseKey
) {
    // Admin client for token verification only
    // Uses the shared HttpClient injected from Koin for connection pooling
    // Lazy-initialized to avoid opening HTTP connections at construction time (CRaC-safe)
    private val adminClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey
        ) {
            install(Auth) {
                // Configure Auth module to use longer timeout for token verification
                // This is needed because local Supabase can be slow
            }

            httpEngine = httpClient.engine
        }
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
    fun createAuthenticatedClient(userAccessToken: String, sharedHttpClient: HttpClient): AuthenticatedSupabaseClient {
        // Create a client with the anon key for apikey header
        // Use accessToken parameter to set Authorization header for all Postgrest requests
        val client = createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey // Use anon key for apikey header
        ) {
            // Provide the user's access token - this sets the Authorization header for Postgrest
            // Note: Cannot use install(Auth) with custom accessToken provider per Supabase SDK
            accessToken = { userAccessToken }

            install(Postgrest) {
                defaultSchema = "public"
            }
        }

        return AuthenticatedSupabaseClient(client, sharedHttpClient, userAccessToken, supabaseUrl, supabaseKey)
    }

    /**
     * Helper function to extract authenticated client from request context
     * This should be called by resolvers to get a client for database operations
     * Uses the shared HttpClient for connection pooling
     */
    open fun getAuthenticatedClient(requestContext: Any?): AuthenticatedSupabaseClient {
        val context = requestContext as? GraphQLRequestContext
            ?: throw IllegalArgumentException("Authentication required: invalid or missing request context")

        // Use the shared HttpClient injected via constructor
        return createAuthenticatedClient(context.accessToken, httpClient)
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Sign in with email and password.
     * Calls Supabase GoTrue API directly.
     */
    suspend fun signIn(email: String, password: String): AuthSessionResponse {
        val response: HttpResponse = httpClient.post("$supabaseUrl/auth/v1/token?grant_type=password") {
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AuthCredentials.serializer(), AuthCredentials(email, password)))
        }

        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.bodyAsText()
            throw IllegalArgumentException("Authentication failed: $errorBody")
        }

        return json.decodeFromString(AuthSessionResponse.serializer(), response.bodyAsText())
    }

    /**
     * Sign up with email and password.
     * Calls Supabase GoTrue API directly.
     */
    suspend fun signUp(email: String, password: String): AuthSessionResponse {
        val response: HttpResponse = httpClient.post("$supabaseUrl/auth/v1/signup") {
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AuthCredentials.serializer(), AuthCredentials(email, password)))
        }

        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.bodyAsText()
            throw IllegalArgumentException("Sign up failed: $errorBody")
        }

        return json.decodeFromString(AuthSessionResponse.serializer(), response.bodyAsText())
    }

    /**
     * Refresh an access token using a refresh token.
     * Calls Supabase GoTrue API directly.
     */
    suspend fun refreshToken(refreshToken: String): AuthSessionResponse {
        val response: HttpResponse = httpClient.post("$supabaseUrl/auth/v1/token?grant_type=refresh_token") {
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RefreshTokenRequest.serializer(), RefreshTokenRequest(refreshToken)))
        }

        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.bodyAsText()
            throw IllegalArgumentException("Token refresh failed: $errorBody")
        }

        return json.decodeFromString(AuthSessionResponse.serializer(), response.bodyAsText())
    }
}

/**
 * Wrapper for an authenticated Supabase client
 * This client uses the user's JWT token, so RLS policies will be enforced automatically
 */
class AuthenticatedSupabaseClient(
    private val client: SupabaseClient,
    private val httpClient: HttpClient,
    private val accessToken: String,
    private val supabaseUrl: String,
    private val supabaseKey: String
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Set a user's admin status
     * Calls the set_user_admin PostgreSQL function
     * Only admins can call this (enforced by the database function)
     */
    suspend fun callSetUserAdmin(userId: String, isAdmin: Boolean) {
        // Call the PostgreSQL RPC function via HTTP
        val input = SetUserAdminInput(target_user_id = userId, is_admin = isAdmin)
        httpClient.post("$supabaseUrl/rest/v1/rpc/set_user_admin") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SetUserAdminInput.serializer(), input))
        }
    }

    /**
     * Get a user by ID
     * Available to all authenticated users
     */
    suspend fun getUserById(userId: String): UserEntity? {
        // Call the PostgreSQL RPC function via HTTP
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/rpc/get_user_by_id") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
            setBody("""{"user_id":"$userId"}""")
        }
        val jsonString = response.bodyAsText()
        // The RPC function returns a single user or empty array
        val users = json.decodeFromString<List<UserEntity>>(jsonString)
        return users.firstOrNull()
    }

    /**
     * Get all users in the system
     * Only admins can call this
     */
    suspend fun getAllUsers(): List<UserEntity> {
        // Call the PostgreSQL RPC function via HTTP
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/rpc/get_all_users") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
        }
        val jsonString = response.bodyAsText()
        return json.decodeFromString(jsonString)
    }

    /**
     * Search for users by email
     * Available to all authenticated users
     */
    suspend fun searchUsers(query: String): List<UserEntity> {
        // Call the PostgreSQL RPC function via HTTP
        val input = SearchUsersInput(search_query = query)
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/rpc/search_users") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SearchUsersInput.serializer(), input))
        }
        val jsonString = response.bodyAsText()
        return json.decodeFromString(jsonString)
    }

    /**
     * Delete a user from the system
     * Only admins can call this
     */
    suspend fun deleteUser(userId: String): Boolean {
        // Call the PostgreSQL RPC function via HTTP
        val input = DeleteUserInput(user_id = userId)
        httpClient.post("$supabaseUrl/rest/v1/rpc/delete_user_by_id") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(DeleteUserInput.serializer(), input))
        }
        return true
    }

    /**
     * Get all groups the user is a member of
     * Uses the Supabase Postgrest client which properly handles RLS policies
     */
    suspend fun getGroups(): List<com.example.services.CheckboxGroupEntity> {
        return client.from("groups")
            .select()
            .decodeList<com.example.services.CheckboxGroupEntity>()
    }

    /**
     * Get a specific group by ID
     * Uses the Supabase Postgrest client which properly handles RLS policies
     */
    suspend fun getGroupById(groupId: String): com.example.services.CheckboxGroupEntity? {
        return client.from("groups")
            .select {
                filter {
                    eq("id", groupId)
                }
            }
            .decodeSingleOrNull<com.example.services.CheckboxGroupEntity>()
    }

    /**
     * Create a new group
     */
    suspend fun createGroup(
        name: String,
        description: String?,
        createdBy: String
    ): com.example.services.CheckboxGroupEntity {
        val input = com.example.services.CreateGroupInput(
            name = name,
            description = description,
            created_by = createdBy
        )
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/groups") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(com.example.services.CreateGroupInput.serializer(), input))
        }
        val jsonString = response.bodyAsText()
        val groups = json.decodeFromString<List<com.example.services.CheckboxGroupEntity>>(jsonString)
        return groups.first()
    }

    /**
     * Get all members of a group
     */
    suspend fun getGroupMembers(groupId: String): List<com.example.services.GroupMemberEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/group_members") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("group_id", "eq.$groupId")
            parameter("select", "*")
        }
        val jsonString = response.bodyAsText()
        return json.decodeFromString(jsonString)
    }

    suspend fun getGroupMembersForGroups(groupIds: List<String>): List<com.example.services.GroupMemberEntity> {
        if (groupIds.isEmpty()) return emptyList()
        val inClause = groupIds.joinToString(",") { "\"$it\"" }
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/group_members") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("group_id", "in.($inClause)")
            parameter("select", "*")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    /**
     * Add a member to a group
     */
    suspend fun addGroupMember(groupId: String, userId: String): com.example.services.GroupMemberEntity {
        val input = com.example.services.AddMemberInput(
            group_id = groupId,
            user_id = userId
        )
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/group_members") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(com.example.services.AddMemberInput.serializer(), input))
        }
        val jsonString = response.bodyAsText()
        val members = json.decodeFromString<List<com.example.services.GroupMemberEntity>>(jsonString)
        return members.first()
    }

    /**
     * Remove a member from a group
     */
    suspend fun removeGroupMember(groupId: String, userId: String): Boolean {
        httpClient.delete("$supabaseUrl/rest/v1/group_members") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("group_id", "eq.$groupId")
            parameter("user_id", "eq.$userId")
        }
        return true
    }

    suspend fun updateGroupStatus(groupId: String, status: String): com.example.services.CheckboxGroupEntity {
        val response: HttpResponse = httpClient.patch("$supabaseUrl/rest/v1/groups") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            parameter("id", "eq.$groupId")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"$status"}""")
        }
        return json.decodeFromString<List<com.example.services.CheckboxGroupEntity>>(response.bodyAsText()).first()
    }

    // -------------------------------------------------------------------------
    // ViaAccess: Assets
    // -------------------------------------------------------------------------

    suspend fun getAssets(tenantName: String? = null): List<com.example.services.AssetEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("order", "created_at.asc")
            if (tenantName != null) parameter("tenant_name", "eq.$tenantName")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun getAssetById(id: String): com.example.services.AssetEntity? {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("id", "eq.$id")
        }
        return json.decodeFromString<List<com.example.services.AssetEntity>>(response.bodyAsText()).firstOrNull()
    }

    suspend fun createAsset(
        assetType: String,
        tenantName: String,
        externalId: String,
        name: String
    ): com.example.services.AssetEntity {
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"asset_type":"$assetType","tenant_name":"$tenantName","external_id":"$externalId","name":"$name"}""")
        }
        return json.decodeFromString<List<com.example.services.AssetEntity>>(response.bodyAsText()).first()
    }

    suspend fun setAssetRequestable(id: String, requestable: Boolean): com.example.services.AssetEntity {
        val response: HttpResponse = httpClient.patch("$supabaseUrl/rest/v1/assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            parameter("id", "eq.$id")
            contentType(ContentType.Application.Json)
            setBody("""{"requestable":$requestable}""")
        }
        return json.decodeFromString<List<com.example.services.AssetEntity>>(response.bodyAsText()).first()
    }

    suspend fun insertAuditEvent(actorId: String, eventType: String, targetType: String, targetId: String, after: String? = null) {
        val afterJson = if (after != null) after else "null"
        httpClient.post("$supabaseUrl/rest/v1/audit_events") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
            setBody("""{"actor_id":"$actorId","event_type":"$eventType","target_type":"AccessRequest","target_id":"$targetId","after":$afterJson}""")
        }
    }

    // -------------------------------------------------------------------------
    // ViaAccess: TenantAssets
    // -------------------------------------------------------------------------

    suspend fun getTenantAssets(): List<com.example.services.TenantAssetEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/tenant_assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun getTenantAssetByName(tenantName: String): com.example.services.TenantAssetEntity? {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/tenant_assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("tenant_name", "eq.$tenantName")
        }
        return json.decodeFromString<List<com.example.services.TenantAssetEntity>>(response.bodyAsText()).firstOrNull()
    }

    suspend fun getTenantAssetById(id: String): com.example.services.TenantAssetEntity? {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/tenant_assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("id", "eq.$id")
        }
        return json.decodeFromString<List<com.example.services.TenantAssetEntity>>(response.bodyAsText()).firstOrNull()
    }

    suspend fun getTenantAssetPolicies(tenantAssetId: String): List<com.example.services.TenantAssetPolicyEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/tenant_asset_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("tenant_asset_id", "eq.$tenantAssetId")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun getTenantAssetPolicyById(id: String): com.example.services.TenantAssetPolicyEntity? {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/tenant_asset_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("id", "eq.$id")
        }
        return json.decodeFromString<List<com.example.services.TenantAssetPolicyEntity>>(response.bodyAsText()).firstOrNull()
    }

    suspend fun userHasTenantAccess(userId: String, tenantName: String): Boolean {
        val tenantAsset = getTenantAssetByName(tenantName) ?: return false
        val policies = getTenantAssetPolicies(tenantAsset.id)
        if (policies.isEmpty()) return false
        val groupIds = policies.map { it.group_id }
        val members = getGroupMembersForGroups(groupIds)
        return members.any { it.user_id == userId }
    }


    suspend fun getUserTenantNames(userId: String): Set<String> {
        val nonDefaultTenants = getTenantAssets().filter { it.tenant_name != "default" }
        if (nonDefaultTenants.isEmpty()) return emptySet()
        val allPolicies = nonDefaultTenants.flatMap { getTenantAssetPolicies(it.id) }
        if (allPolicies.isEmpty()) return emptySet()
        val members = getGroupMembersForGroups(allPolicies.map { it.group_id }.distinct())
        val userGroupIds = members.filter { it.user_id == userId }.map { it.group_id }.toSet()
        val accessibleTenantAssetIds = allPolicies.filter { it.group_id in userGroupIds }.map { it.tenant_asset_id }.toSet()
        return nonDefaultTenants.filter { it.id in accessibleTenantAssetIds }.map { it.tenant_name }.toSet()
    }

    suspend fun createTenantAssetPolicy(
        tenantAssetId: String,
        groupId: String,
        permission: String
    ): com.example.services.TenantAssetPolicyEntity {
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/tenant_asset_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"tenant_asset_id":"$tenantAssetId","group_id":"$groupId","permission":"$permission"}""")
        }
        return json.decodeFromString<List<com.example.services.TenantAssetPolicyEntity>>(response.bodyAsText()).first()
    }

    suspend fun deleteTenantAssetPolicy(id: String): Boolean {
        httpClient.delete("$supabaseUrl/rest/v1/tenant_asset_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("id", "eq.$id")
        }
        return true
    }

    // -------------------------------------------------------------------------
    // ViaAccess: GitHub assets and policies
    // -------------------------------------------------------------------------

    suspend fun getGitHubRepoAssets(): List<Pair<com.example.services.AssetEntity, com.example.services.GitHubRepoAssetEntity>> {
        val assets = getAssets("github").filter { it.asset_type == "GITHUB_REPO" }
        return assets.mapNotNull { asset ->
            val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/github_repo_assets") {
                header("Authorization", "Bearer $accessToken")
                header("apikey", supabaseKey)
                parameter("select", "*")
                parameter("id", "eq.${asset.id}")
            }
            val detail = json.decodeFromString<List<com.example.services.GitHubRepoAssetEntity>>(response.bodyAsText()).firstOrNull()
            detail?.let { asset to it }
        }
    }

    suspend fun getGitHubRepoAssetById(id: String): Pair<com.example.services.AssetEntity, com.example.services.GitHubRepoAssetEntity>? {
        val asset = getAssetById(id) ?: return null
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/github_repo_assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("id", "eq.$id")
        }
        val detail = json.decodeFromString<List<com.example.services.GitHubRepoAssetEntity>>(response.bodyAsText()).firstOrNull()
            ?: return null
        return asset to detail
    }

    suspend fun createGitHubRepoAsset(owner: String, repo: String, name: String): Pair<com.example.services.AssetEntity, com.example.services.GitHubRepoAssetEntity> {
        val externalId = "$owner/$repo"
        val asset = createAsset("GITHUB_REPO", "github", externalId, name)
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/github_repo_assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${asset.id}","owner":"$owner","repo":"$repo"}""")
        }
        val detail = json.decodeFromString<List<com.example.services.GitHubRepoAssetEntity>>(response.bodyAsText()).first()
        return asset to detail
    }

    suspend fun getGitHubTeamAssets(): List<Pair<com.example.services.AssetEntity, com.example.services.GitHubTeamAssetEntity>> {
        val assets = getAssets("github").filter { it.asset_type == "GITHUB_TEAM" }
        return assets.mapNotNull { asset ->
            val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/github_team_assets") {
                header("Authorization", "Bearer $accessToken")
                header("apikey", supabaseKey)
                parameter("select", "*")
                parameter("id", "eq.${asset.id}")
            }
            val detail = json.decodeFromString<List<com.example.services.GitHubTeamAssetEntity>>(response.bodyAsText()).firstOrNull()
            detail?.let { asset to it }
        }
    }

    suspend fun createGitHubTeamAsset(org: String, slug: String, name: String): Pair<com.example.services.AssetEntity, com.example.services.GitHubTeamAssetEntity> {
        val externalId = "$org/$slug"
        val asset = createAsset("GITHUB_TEAM", "github", externalId, name)
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/github_team_assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${asset.id}","org":"$org","slug":"$slug"}""")
        }
        val detail = json.decodeFromString<List<com.example.services.GitHubTeamAssetEntity>>(response.bodyAsText()).first()
        return asset to detail
    }

    suspend fun getGitHubRepoPoliciesByAsset(assetId: String): List<com.example.services.GitHubRepoPolicyEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/github_repo_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("asset_id", "eq.$assetId")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun createGitHubRepoPolicy(assetId: String, groupId: String, permission: String): com.example.services.GitHubRepoPolicyEntity {
        // Insert parent policies row first
        val policyResponse: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"policy_type":"GITHUB_REPO","group_id":"$groupId"}""")
        }
        val policyId = json.decodeFromString<List<com.example.services.PolicyEntity>>(policyResponse.bodyAsText()).first().id

        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/github_repo_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"$policyId","asset_id":"$assetId","group_id":"$groupId","permission":"$permission"}""")
        }
        return json.decodeFromString<List<com.example.services.GitHubRepoPolicyEntity>>(response.bodyAsText()).first()
    }

    // Upsert variant: updates permission on the existing policy row if it already exists.
    // Used by approval flow where the group may already have a policy at a different level.
    suspend fun upsertGitHubRepoPolicy(assetId: String, groupId: String, permission: String): com.example.services.GitHubRepoPolicyEntity {
        // Check for existing policy first; if present, update permission in-place.
        val existing = getGitHubRepoPoliciesByAsset(assetId).firstOrNull { it.group_id == groupId }
        if (existing != null) {
            val response: HttpResponse = httpClient.patch("$supabaseUrl/rest/v1/github_repo_policies") {
                header("Authorization", "Bearer $accessToken")
                header("apikey", supabaseKey)
                header("Prefer", "return=representation")
                parameter("id", "eq.${existing.id}")
                contentType(ContentType.Application.Json)
                setBody("""{"permission":"$permission"}""")
            }
            return json.decodeFromString<List<com.example.services.GitHubRepoPolicyEntity>>(response.bodyAsText()).first()
        }
        return createGitHubRepoPolicy(assetId, groupId, permission)
    }

    suspend fun deleteGitHubRepoPolicy(id: String): Boolean {
        // Deleting the parent policies row cascades to github_repo_policies
        httpClient.delete("$supabaseUrl/rest/v1/policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("id", "eq.$id")
        }
        return true
    }

    suspend fun getGitHubTeamPoliciesByAsset(assetId: String): List<com.example.services.GitHubTeamPolicyEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/github_team_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("asset_id", "eq.$assetId")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun createGitHubTeamPolicy(assetId: String, groupId: String, permission: String): com.example.services.GitHubTeamPolicyEntity {
        val policyResponse: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"policy_type":"GITHUB_TEAM","group_id":"$groupId"}""")
        }
        val policyId = json.decodeFromString<List<com.example.services.PolicyEntity>>(policyResponse.bodyAsText()).first().id

        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/github_team_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"$policyId","asset_id":"$assetId","group_id":"$groupId","permission":"$permission"}""")
        }
        return json.decodeFromString<List<com.example.services.GitHubTeamPolicyEntity>>(response.bodyAsText()).first()
    }

    suspend fun upsertGitHubTeamPolicy(assetId: String, groupId: String, permission: String): com.example.services.GitHubTeamPolicyEntity {
        val existing = getGitHubTeamPoliciesByAsset(assetId).firstOrNull { it.group_id == groupId }
        if (existing != null) {
            val response: HttpResponse = httpClient.patch("$supabaseUrl/rest/v1/github_team_policies") {
                header("Authorization", "Bearer $accessToken")
                header("apikey", supabaseKey)
                header("Prefer", "return=representation")
                parameter("id", "eq.${existing.id}")
                contentType(ContentType.Application.Json)
                setBody("""{"permission":"$permission"}""")
            }
            return json.decodeFromString<List<com.example.services.GitHubTeamPolicyEntity>>(response.bodyAsText()).first()
        }
        return createGitHubTeamPolicy(assetId, groupId, permission)
    }

    suspend fun deleteGitHubTeamPolicy(id: String): Boolean {
        httpClient.delete("$supabaseUrl/rest/v1/policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("id", "eq.$id")
        }
        return true
    }

    // -------------------------------------------------------------------------
    // ViaAccess: Asana assets and policies
    // -------------------------------------------------------------------------

    suspend fun getAsanaProjectAssets(): List<Pair<com.example.services.AssetEntity, com.example.services.AsanaProjectAssetEntity>> {
        val assets = getAssets("asana").filter { it.asset_type == "ASANA_PROJECT" }
        return assets.mapNotNull { asset ->
            val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/asana_project_assets") {
                header("Authorization", "Bearer $accessToken")
                header("apikey", supabaseKey)
                parameter("select", "*")
                parameter("id", "eq.${asset.id}")
            }
            val detail = json.decodeFromString<List<com.example.services.AsanaProjectAssetEntity>>(response.bodyAsText()).firstOrNull()
            detail?.let { asset to it }
        }
    }

    suspend fun getAsanaProjectAssetById(id: String): Pair<com.example.services.AssetEntity, com.example.services.AsanaProjectAssetEntity>? {
        val asset = getAssetById(id) ?: return null
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/asana_project_assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("id", "eq.$id")
        }
        val detail = json.decodeFromString<List<com.example.services.AsanaProjectAssetEntity>>(response.bodyAsText()).firstOrNull()
            ?: return null
        return asset to detail
    }

    suspend fun createAsanaProjectAsset(gid: String, name: String): Pair<com.example.services.AssetEntity, com.example.services.AsanaProjectAssetEntity> {
        val asset = createAsset("ASANA_PROJECT", "asana", gid, name)
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/asana_project_assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${asset.id}","gid":"$gid"}""")
        }
        val detail = json.decodeFromString<List<com.example.services.AsanaProjectAssetEntity>>(response.bodyAsText()).first()
        return asset to detail
    }

    suspend fun getAsanaPortfolioAssets(): List<Pair<com.example.services.AssetEntity, com.example.services.AsanaPortfolioAssetEntity>> {
        val assets = getAssets("asana").filter { it.asset_type == "ASANA_PORTFOLIO" }
        return assets.mapNotNull { asset ->
            val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/asana_portfolio_assets") {
                header("Authorization", "Bearer $accessToken")
                header("apikey", supabaseKey)
                parameter("select", "*")
                parameter("id", "eq.${asset.id}")
            }
            val detail = json.decodeFromString<List<com.example.services.AsanaPortfolioAssetEntity>>(response.bodyAsText()).firstOrNull()
            detail?.let { asset to it }
        }
    }

    suspend fun createAsanaPortfolioAsset(gid: String, name: String): Pair<com.example.services.AssetEntity, com.example.services.AsanaPortfolioAssetEntity> {
        val asset = createAsset("ASANA_PORTFOLIO", "asana", gid, name)
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/asana_portfolio_assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${asset.id}","gid":"$gid"}""")
        }
        val detail = json.decodeFromString<List<com.example.services.AsanaPortfolioAssetEntity>>(response.bodyAsText()).first()
        return asset to detail
    }

    suspend fun getAsanaProjectPoliciesByAsset(assetId: String): List<com.example.services.AsanaProjectPolicyEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/asana_project_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("asset_id", "eq.$assetId")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun createAsanaProjectPolicy(assetId: String, groupId: String, permission: String): com.example.services.AsanaProjectPolicyEntity {
        val policyResponse: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"policy_type":"ASANA_PROJECT","group_id":"$groupId"}""")
        }
        val policyId = json.decodeFromString<List<com.example.services.PolicyEntity>>(policyResponse.bodyAsText()).first().id

        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/asana_project_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"$policyId","asset_id":"$assetId","group_id":"$groupId","permission":"$permission"}""")
        }
        return json.decodeFromString<List<com.example.services.AsanaProjectPolicyEntity>>(response.bodyAsText()).first()
    }

    suspend fun upsertAsanaProjectPolicy(assetId: String, groupId: String, permission: String): com.example.services.AsanaProjectPolicyEntity {
        val existing = getAsanaProjectPoliciesByAsset(assetId).firstOrNull { it.group_id == groupId }
        if (existing != null) {
            val response: HttpResponse = httpClient.patch("$supabaseUrl/rest/v1/asana_project_policies") {
                header("Authorization", "Bearer $accessToken")
                header("apikey", supabaseKey)
                header("Prefer", "return=representation")
                parameter("id", "eq.${existing.id}")
                contentType(ContentType.Application.Json)
                setBody("""{"permission":"$permission"}""")
            }
            return json.decodeFromString<List<com.example.services.AsanaProjectPolicyEntity>>(response.bodyAsText()).first()
        }
        return createAsanaProjectPolicy(assetId, groupId, permission)
    }

    suspend fun deleteAsanaProjectPolicy(id: String): Boolean {
        httpClient.delete("$supabaseUrl/rest/v1/policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("id", "eq.$id")
        }
        return true
    }

    suspend fun getAsanaPortfolioPoliciesByAsset(assetId: String): List<com.example.services.AsanaPortfolioPolicyEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/asana_portfolio_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("asset_id", "eq.$assetId")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun createAsanaPortfolioPolicy(assetId: String, groupId: String, permission: String): com.example.services.AsanaPortfolioPolicyEntity {
        val policyResponse: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"policy_type":"ASANA_PORTFOLIO","group_id":"$groupId"}""")
        }
        val policyId = json.decodeFromString<List<com.example.services.PolicyEntity>>(policyResponse.bodyAsText()).first().id

        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/asana_portfolio_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"$policyId","asset_id":"$assetId","group_id":"$groupId","permission":"$permission"}""")
        }
        return json.decodeFromString<List<com.example.services.AsanaPortfolioPolicyEntity>>(response.bodyAsText()).first()
    }

    suspend fun upsertAsanaPortfolioPolicy(assetId: String, groupId: String, permission: String): com.example.services.AsanaPortfolioPolicyEntity {
        val existing = getAsanaPortfolioPoliciesByAsset(assetId).firstOrNull { it.group_id == groupId }
        if (existing != null) {
            val response: HttpResponse = httpClient.patch("$supabaseUrl/rest/v1/asana_portfolio_policies") {
                header("Authorization", "Bearer $accessToken")
                header("apikey", supabaseKey)
                header("Prefer", "return=representation")
                parameter("id", "eq.${existing.id}")
                contentType(ContentType.Application.Json)
                setBody("""{"permission":"$permission"}""")
            }
            return json.decodeFromString<List<com.example.services.AsanaPortfolioPolicyEntity>>(response.bodyAsText()).first()
        }
        return createAsanaPortfolioPolicy(assetId, groupId, permission)
    }

    suspend fun deleteAsanaPortfolioPolicy(id: String): Boolean {
        httpClient.delete("$supabaseUrl/rest/v1/policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("id", "eq.$id")
        }
        return true
    }

    // -------------------------------------------------------------------------
    // ViaAccess: Sync jobs
    // -------------------------------------------------------------------------

    suspend fun getSyncJobById(id: String): com.example.services.SyncJobEntity? {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/sync_jobs") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("id", "eq.$id")
        }
        return json.decodeFromString<List<com.example.services.SyncJobEntity>>(response.bodyAsText()).firstOrNull()
    }

    suspend fun createSyncJob(
        assetId: String,
        assetType: String,
        tenantName: String,
        action: String
    ): com.example.services.SyncJobEntity {
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/sync_jobs") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody("""{"asset_id":"$assetId","asset_type":"$assetType","tenant_name":"$tenantName","action":"$action"}""")
        }
        return json.decodeFromString<List<com.example.services.SyncJobEntity>>(response.bodyAsText()).first()
    }

    suspend fun updateSyncJob(
        id: String,
        status: String,
        lastError: String? = null,
        planSummary: String? = null,
        attemptCount: Int? = null,
        nextRetryAt: String? = null,
    ): com.example.services.SyncJobEntity {
        val completedAt = if (status in listOf("SUCCEEDED", "FAILED", "EXHAUSTED", "ABANDONED")) "\"now()\"" else "null"
        val errorJson = if (lastError != null) "\"${lastError.replace("\"", "\\\"")}\"" else "null"
        val summaryJson = if (planSummary != null) "\"${planSummary.replace("\"", "\\\"")}\"" else "null"
        val nextRetryJson = if (nextRetryAt != null) "\"$nextRetryAt\"" else "null"
        val attemptFragment = if (attemptCount != null) ""","attempt_count":$attemptCount""" else ""
        val body = """{"status":"$status","last_error":$errorJson,"plan_summary":$summaryJson,"completed_at":$completedAt,"next_retry_at":$nextRetryJson$attemptFragment}"""
        val response: HttpResponse = httpClient.patch("$supabaseUrl/rest/v1/sync_jobs") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            parameter("id", "eq.$id")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return json.decodeFromString<List<com.example.services.SyncJobEntity>>(response.bodyAsText()).first()
    }

    // -------------------------------------------------------------------------
    // ViaAccess: External identities
    // -------------------------------------------------------------------------

    suspend fun getExternalIdentitiesForUser(userId: String): List<com.example.services.ExternalIdentityEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/external_identities") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("user_id", "eq.$userId")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun getExternalIdentitiesByProvider(provider: String): List<com.example.services.ExternalIdentityEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/external_identities") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("provider", "eq.$provider")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun getExternalIdentitiesForUsersAndProvider(
        userIds: List<String>,
        provider: String
    ): List<com.example.services.ExternalIdentityEntity> {
        if (userIds.isEmpty()) return emptyList()
        val inClause = userIds.joinToString(",") { "\"$it\"" }
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/external_identities") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("provider", "eq.$provider")
            parameter("user_id", "in.($inClause)")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun upsertExternalIdentity(
        userId: String,
        provider: String,
        externalUserId: String,
        externalUsername: String,
        verified: Boolean = false
    ): com.example.services.ExternalIdentityEntity {
        val verifiedAt = if (verified) "\"now()\"" else "null"
        val body = """{"user_id":"$userId","provider":"$provider","external_user_id":"$externalUserId","external_username":"$externalUsername","verified_at":$verifiedAt}"""
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/external_identities") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation,resolution=merge-duplicates")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return json.decodeFromString<List<com.example.services.ExternalIdentityEntity>>(response.bodyAsText()).first()
    }

    // -------------------------------------------------------------------------
    // ViaAccess: Cross-tenant policy summary (for Group.accessSummary)
    // -------------------------------------------------------------------------

    suspend fun getPolicySummaryForGroup(groupId: String): List<com.example.services.PolicySummaryRow> {
        val rows = mutableListOf<com.example.services.PolicySummaryRow>()

        // GitHub repo policies
        val repoResponse: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/github_repo_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "asset_id,permission,sync_status")
            parameter("group_id", "eq.$groupId")
        }
        for (p in json.decodeFromString<List<com.example.services.GitHubRepoPolicyEntity>>(repoResponse.bodyAsText())) {
            val asset = getAssetById(p.asset_id) ?: continue
            rows.add(com.example.services.PolicySummaryRow(
                asset_type = "GITHUB_REPO", asset_name = asset.name,
                external_id = asset.external_id, permission = p.permission, sync_status = p.sync_status
            ))
        }

        // GitHub team policies
        val teamResponse: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/github_team_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "asset_id,permission,sync_status")
            parameter("group_id", "eq.$groupId")
        }
        for (p in json.decodeFromString<List<com.example.services.GitHubTeamPolicyEntity>>(teamResponse.bodyAsText())) {
            val asset = getAssetById(p.asset_id) ?: continue
            rows.add(com.example.services.PolicySummaryRow(
                asset_type = "GITHUB_TEAM", asset_name = asset.name,
                external_id = asset.external_id, permission = p.permission, sync_status = p.sync_status
            ))
        }

        // Asana project policies
        val asanaProjectResponse: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/asana_project_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "asset_id,permission,sync_status")
            parameter("group_id", "eq.$groupId")
        }
        for (p in json.decodeFromString<List<com.example.services.AsanaProjectPolicyEntity>>(asanaProjectResponse.bodyAsText())) {
            val asset = getAssetById(p.asset_id) ?: continue
            rows.add(com.example.services.PolicySummaryRow(
                asset_type = "ASANA_PROJECT", asset_name = asset.name,
                external_id = asset.external_id, permission = p.permission, sync_status = p.sync_status
            ))
        }

        // Asana portfolio policies
        val asanaPortfolioResponse: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/asana_portfolio_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "asset_id,permission,sync_status")
            parameter("group_id", "eq.$groupId")
        }
        for (p in json.decodeFromString<List<com.example.services.AsanaPortfolioPolicyEntity>>(asanaPortfolioResponse.bodyAsText())) {
            val asset = getAssetById(p.asset_id) ?: continue
            rows.add(com.example.services.PolicySummaryRow(
                asset_type = "ASANA_PORTFOLIO", asset_name = asset.name,
                external_id = asset.external_id, permission = p.permission, sync_status = p.sync_status
            ))
        }

        return rows
    }

    // -------------------------------------------------------------------------
    // Admin: provider user roster + invite-and-link
    // -------------------------------------------------------------------------

    /**
     * List all known external identities for a given provider, joined to system user email where available.
     * Returns all rows from external_identities — both linked (have a user_id) and unlinked
     * entries created by importProviderIdentities for unmatched users.
     *
     * Note: unlinked entries have external_user_id set but user_id points to a sentinel or is
     * managed externally; the resolver filters/enriches via getAllUsers.
     */
    suspend fun getExternalIdentitiesByProviderWithUsers(provider: String): List<com.example.services.ExternalIdentityEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/external_identities") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("provider", "eq.$provider")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    // -------------------------------------------------------------------------
    // ViaAccess: Tenant permission check and affected-asset lookup
    // -------------------------------------------------------------------------

    /**
     * Checks whether the given user has at least the required permission on the named tenant.
     * Calls the `has_tenant_permission` Postgres RPC function.
     */
    suspend fun userHasTenantPermission(
        userId: String,
        tenantName: String,
        required: com.example.services.TenantPermission
    ): Boolean {
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/rpc/has_tenant_permission") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
            setBody("""{"tenant":"$tenantName","required_permission":"${required.name}"}""")
        }
        return json.decodeFromString<Boolean>(response.bodyAsText())
    }

    /**
     * Returns all assets that have a policy referencing the given group.
     * Queries github_repo_policies, github_team_policies, asana_project_policies,
     * and asana_portfolio_policies for the group, then resolves each asset.
     */
    suspend fun getAssetsAffectedByGroup(groupId: String): List<com.example.services.AffectedAsset> {
        val affected = mutableListOf<com.example.services.AffectedAsset>()

        // GitHub repo policies
        val repoResponse: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/github_repo_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "asset_id")
            parameter("group_id", "eq.$groupId")
        }
        for (p in json.decodeFromString<List<com.example.services.GitHubRepoPolicyEntity>>(repoResponse.bodyAsText())) {
            val asset = getAssetById(p.asset_id) ?: continue
            affected.add(com.example.services.AffectedAsset(assetId = asset.id, assetType = asset.asset_type, tenantName = asset.tenant_name))
        }

        // GitHub team policies
        val teamResponse: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/github_team_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "asset_id")
            parameter("group_id", "eq.$groupId")
        }
        for (p in json.decodeFromString<List<com.example.services.GitHubTeamPolicyEntity>>(teamResponse.bodyAsText())) {
            val asset = getAssetById(p.asset_id) ?: continue
            affected.add(com.example.services.AffectedAsset(assetId = asset.id, assetType = asset.asset_type, tenantName = asset.tenant_name))
        }

        // Asana project policies
        val asanaProjectResponse: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/asana_project_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "asset_id")
            parameter("group_id", "eq.$groupId")
        }
        for (p in json.decodeFromString<List<com.example.services.AsanaProjectPolicyEntity>>(asanaProjectResponse.bodyAsText())) {
            val asset = getAssetById(p.asset_id) ?: continue
            affected.add(com.example.services.AffectedAsset(assetId = asset.id, assetType = asset.asset_type, tenantName = asset.tenant_name))
        }

        // Asana portfolio policies
        val asanaPortfolioResponse: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/asana_portfolio_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "asset_id")
            parameter("group_id", "eq.$groupId")
        }
        for (p in json.decodeFromString<List<com.example.services.AsanaPortfolioPolicyEntity>>(asanaPortfolioResponse.bodyAsText())) {
            val asset = getAssetById(p.asset_id) ?: continue
            affected.add(com.example.services.AffectedAsset(assetId = asset.id, assetType = asset.asset_type, tenantName = asset.tenant_name))
        }

        return affected
    }

    // -------------------------------------------------------------------------
    // ViaAccess: Single-policy lookups (used before deletion for reconcile)
    // -------------------------------------------------------------------------

    suspend fun getGitHubRepoPolicyById(id: String): com.example.services.GitHubRepoPolicyEntity? {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/github_repo_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("id", "eq.$id")
        }
        return json.decodeFromString<List<com.example.services.GitHubRepoPolicyEntity>>(response.bodyAsText()).firstOrNull()
    }

    suspend fun getGitHubTeamPolicyById(id: String): com.example.services.GitHubTeamPolicyEntity? {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/github_team_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("id", "eq.$id")
        }
        return json.decodeFromString<List<com.example.services.GitHubTeamPolicyEntity>>(response.bodyAsText()).firstOrNull()
    }

    suspend fun getAsanaProjectPolicyById(id: String): com.example.services.AsanaProjectPolicyEntity? {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/asana_project_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("id", "eq.$id")
        }
        return json.decodeFromString<List<com.example.services.AsanaProjectPolicyEntity>>(response.bodyAsText()).firstOrNull()
    }

    suspend fun getAsanaPortfolioPolicyById(id: String): com.example.services.AsanaPortfolioPolicyEntity? {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/asana_portfolio_policies") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("id", "eq.$id")
        }
        return json.decodeFromString<List<com.example.services.AsanaPortfolioPolicyEntity>>(response.bodyAsText()).firstOrNull()
    }

    /**
     * Update sync_status on all policy rows for a given asset across all policy tables.
     * Called by the sync executor after a job SUCCEEDS or FAILS.
     */
    suspend fun updatePolicySyncStatus(assetId: String, syncStatus: String) {
        val body = """{"sync_status":"$syncStatus"}"""
        for (table in listOf("github_repo_policies", "github_team_policies", "asana_project_policies", "asana_portfolio_policies")) {
            httpClient.patch("$supabaseUrl/rest/v1/$table") {
                header("Authorization", "Bearer $accessToken")
                header("apikey", supabaseKey)
                parameter("asset_id", "eq.$assetId")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    // -------------------------------------------------------------------------
    // ViaAccess Phase 4: Access requests
    // -------------------------------------------------------------------------

    suspend fun getRequestableAssets(tenantName: String?, query: String?): List<com.example.services.AssetEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/assets") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("requestable", "eq.true")
            if (tenantName != null) parameter("tenant_name", "eq.$tenantName")
            if (!query.isNullOrBlank()) parameter("name", "ilike.*$query*")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun createAccessRequest(
        tenantName: String,
        assetId: String,
        groupId: String,
        requestedPermission: String,
        requestedBy: String,
    ): com.example.services.AccessRequestEntity {
        val body = """{"tenant_name":"$tenantName","asset_id":"$assetId","group_id":"$groupId","requested_permission":"$requestedPermission","requested_by":"$requestedBy"}"""
        val response: HttpResponse = httpClient.post("$supabaseUrl/rest/v1/access_requests") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return json.decodeFromString<List<com.example.services.AccessRequestEntity>>(response.bodyAsText()).first()
    }

    suspend fun getAccessRequestById(id: String): com.example.services.AccessRequestEntity? {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/access_requests") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("id", "eq.$id")
        }
        return json.decodeFromString<List<com.example.services.AccessRequestEntity>>(response.bodyAsText()).firstOrNull()
    }

    suspend fun getPendingAccessRequests(tenantName: String): List<com.example.services.AccessRequestEntity> {
        val response: HttpResponse = httpClient.get("$supabaseUrl/rest/v1/access_requests") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            parameter("select", "*")
            parameter("tenant_name", "eq.$tenantName")
            parameter("status", "eq.PENDING")
            parameter("order", "requested_at.asc")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    // Atomic compare-and-swap: only transitions from PENDING to the target status.
    // Returns null if the row was already processed by another reviewer (status != PENDING).
    suspend fun transitionAccessRequestFromPending(
        id: String,
        status: String,
        reviewedBy: String? = null,
        reviewerNote: String? = null,
    ): com.example.services.AccessRequestEntity? {
        val reviewedByJson = if (reviewedBy != null) "\"$reviewedBy\"" else "null"
        val noteJson = if (reviewerNote != null) "\"${reviewerNote.replace("\"", "\\\"")}\"" else "null"
        val body = """{"status":"$status","reviewed_by":$reviewedByJson,"reviewer_note":$noteJson,"reviewed_at":"now()"}"""
        val response: HttpResponse = httpClient.patch("$supabaseUrl/rest/v1/access_requests") {
            header("Authorization", "Bearer $accessToken")
            header("apikey", supabaseKey)
            header("Prefer", "return=representation")
            parameter("id", "eq.$id")
            parameter("status", "eq.PENDING")  // Only update if still PENDING — concurrent reviewer gets empty response
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return json.decodeFromString<List<com.example.services.AccessRequestEntity>>(response.bodyAsText()).firstOrNull()
    }

    /**
     * Invite a new user by email via Supabase Admin API (service role required).
     * Creates a Supabase Auth account and sends an invite email.
     * Returns the new user's ID.
     */
    suspend fun inviteUserByEmail(email: String, serviceRoleKey: String): String {
        val response: HttpResponse = httpClient.post("$supabaseUrl/auth/v1/admin/users") {
            header("Authorization", "Bearer $serviceRoleKey")
            header("apikey", serviceRoleKey)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","email_confirm":false,"send_invitation":true}""")
        }
        val body = response.bodyAsText()
        return json.decodeFromString<kotlinx.serialization.json.JsonObject>(body)["id"]
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?: error("Failed to create user: $body")
    }
}
