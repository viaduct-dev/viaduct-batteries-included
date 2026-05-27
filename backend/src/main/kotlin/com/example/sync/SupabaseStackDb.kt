package com.example.sync

import com.example.AuthenticatedSupabaseClient

class SupabaseStackDb(private val client: AuthenticatedSupabaseClient) : StackDb {
    override suspend fun loadPoliciesForAsset(assetId: String): List<PolicyRow> =
        client.getGitHubRepoPoliciesByAsset(assetId).map { PolicyRow(it.group_id, it.permission) }

    override suspend fun loadMembersForGroup(groupId: String): List<MemberRow> =
        client.getGroupMembers(groupId).map { MemberRow(it.person_id) }

    override suspend fun loadExternalIdentity(personId: String, provider: String): String? =
        client.getExternalIdentitiesForPerson(personId)
            .firstOrNull { it.provider == provider && it.verified_at != null }
            ?.external_username
}
