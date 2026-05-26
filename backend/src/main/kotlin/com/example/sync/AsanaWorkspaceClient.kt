package com.example.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AsanaWorkspaceClient(
    private val asanaToken: String,
    private val workspaceGid: String,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class AsanaUser(
        val gid: String,
        val name: String,
        val email: String? = null,
    )

    @Serializable
    private data class UserListResponse(
        val data: List<AsanaUser>,
        val next_page: NextPage? = null,
    )

    @Serializable
    private data class NextPage(val offset: String)

    suspend fun listWorkspaceUsers(): List<AsanaUser> {
        val users = mutableListOf<AsanaUser>()
        var offset: String? = null
        while (true) {
            val response = httpClient.get("https://app.asana.com/api/1.0/workspaces/$workspaceGid/users") {
                header("Authorization", "Bearer $asanaToken")
                parameter("opt_fields", "gid,name,email")
                parameter("limit", "100")
                if (offset != null) parameter("offset", offset)
            }
            val page = json.decodeFromString<UserListResponse>(response.bodyAsText())
            users += page.data
            offset = page.next_page?.offset ?: break
        }
        return users
    }

    suspend fun searchUsers(query: String): List<AsanaUser> {
        val all = listWorkspaceUsers()
        return if (query.isBlank()) all
        else all.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.email?.contains(query, ignoreCase = true) == true
        }
    }

    companion object {
        fun fromEnv(httpClient: HttpClient): AsanaWorkspaceClient? {
            val token = System.getenv("ASANA_TOKEN") ?: return null
            val workspace = System.getenv("ASANA_WORKSPACE_GID") ?: return null
            return AsanaWorkspaceClient(token, workspace, httpClient)
        }
    }
}
