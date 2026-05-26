package com.example.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GitHubOrgClient(
    private val githubToken: String,
    private val githubOrg: String,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class GitHubUser(
        val id: Long,
        val login: String,
        val email: String? = null,
    )

    @Serializable
    private data class GitHubMemberDetail(
        val id: Long,
        val login: String,
    )

    suspend fun searchMembers(query: String): List<GitHubUser> {
        // GitHub org member search: list all members and filter client-side
        // (GitHub's org member search API requires specific scopes)
        val all = listOrgMembers()
        return if (query.isBlank()) all
        else all.filter { it.login.contains(query, ignoreCase = true) }
    }

    suspend fun listOrgMembers(): List<GitHubUser> {
        val members = mutableListOf<GitHubUser>()
        var page = 1
        while (true) {
            val response = httpClient.get("https://api.github.com/orgs/$githubOrg/members") {
                header("Authorization", "Bearer $githubToken")
                header("Accept", "application/vnd.github+json")
                parameter("per_page", "100")
                parameter("page", page)
            }
            val batch = json.decodeFromString<List<GitHubMemberDetail>>(response.bodyAsText())
            if (batch.isEmpty()) break
            // Fetch public email for each member
            members += batch.map { member ->
                val email = fetchUserEmail(member.login)
                GitHubUser(id = member.id, login = member.login, email = email)
            }
            if (batch.size < 100) break
            page++
        }
        return members
    }

    private suspend fun fetchUserEmail(login: String): String? {
        val response = httpClient.get("https://api.github.com/users/$login") {
            header("Authorization", "Bearer $githubToken")
            header("Accept", "application/vnd.github+json")
        }
        @Serializable
        data class UserDetail(val email: String? = null)
        return runCatching {
            json.decodeFromString<UserDetail>(response.bodyAsText()).email
        }.getOrNull()
    }

    companion object {
        fun fromEnv(httpClient: HttpClient): GitHubOrgClient? {
            val token = System.getenv("GITHUB_TOKEN") ?: return null
            val org = System.getenv("GITHUB_ORG") ?: return null
            return GitHubOrgClient(token, org, httpClient)
        }
    }
}
