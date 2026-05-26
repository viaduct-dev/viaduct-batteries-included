package com.example.sync

import com.pulumi.Context
import com.pulumi.github.RepositoryCollaborator
import com.pulumi.github.RepositoryCollaboratorArgs
import kotlinx.coroutines.runBlocking

/**
 * Pulumi stack program for a single GitHub repository asset.
 *
 * Declares one RepositoryCollaborator resource per effective (user, permission) pair.
 * When multiple groups cover the same user, the highest permission wins.
 * The program is a pure function of (ownerRepo, db) — no policy data lives in the program itself.
 */
fun githubRepoStackProgram(ownerRepo: String, assetId: String, db: StackDb): (Context) -> Unit = { _ ->
    val effectiveAccess = runBlocking { resolveEffectiveAccess(assetId, db) }
    for ((username, permission) in effectiveAccess) {
        RepositoryCollaborator(
            "collab-$username",
            RepositoryCollaboratorArgs.builder()
                .repository(ownerRepo)
                .username(username)
                .permission(permission.toGitHubPermission())
                .build()
        )
    }
}

/** Merges policies across all groups, resolving conflicts to the highest permission. */
suspend fun resolveEffectiveAccess(assetId: String, db: StackDb): Map<String, GitHubRepoPermission> {
    val policies = db.loadPoliciesForAsset(assetId)
    val effective = mutableMapOf<String, GitHubRepoPermission>()
    for (policy in policies) {
        val permission = GitHubRepoPermission.valueOf(policy.permission)
        val members = db.loadMembersForGroup(policy.groupId)
        for (member in members) {
            val username = db.loadExternalIdentity(member.userId, "github") ?: continue
            val current = effective[username]
            if (current == null || permission > current) {
                effective[username] = permission
            }
        }
    }
    return effective
}

enum class GitHubRepoPermission {
    READ, TRIAGE, WRITE, MAINTAIN, ADMIN;

    fun toGitHubPermission(): String = when (this) {
        READ -> "pull"
        TRIAGE -> "triage"
        WRITE -> "push"
        MAINTAIN -> "maintain"
        ADMIN -> "admin"
    }
}
