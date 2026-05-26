package com.viaduct.sync

/**
 * In-memory StackDb for unit tests. Controls policy data without a running Supabase instance.
 */
class FakeStackDb : StackDb {
    data class FakePolicy(val assetId: String, val groupId: String, val permission: String)
    data class FakeMember(val groupId: String, val userId: String)
    data class FakeIdentity(val userId: String, val provider: String, val username: String)

    val policies = mutableListOf<FakePolicy>()
    val members = mutableListOf<FakeMember>()
    val identities = mutableListOf<FakeIdentity>()

    fun policy(assetId: String, groupId: String, permission: String) {
        policies += FakePolicy(assetId, groupId, permission)
    }

    fun member(groupId: String, userId: String) {
        members += FakeMember(groupId, userId)
    }

    fun githubIdentity(userId: String, username: String) {
        identities += FakeIdentity(userId, "github", username)
    }

    override suspend fun loadPoliciesForAsset(assetId: String): List<PolicyRow> =
        policies.filter { it.assetId == assetId }.map { PolicyRow(it.groupId, it.permission) }

    override suspend fun loadMembersForGroup(groupId: String): List<MemberRow> =
        members.filter { it.groupId == groupId }.map { MemberRow(it.userId) }

    override suspend fun loadExternalIdentity(userId: String, provider: String): String? =
        identities.firstOrNull { it.userId == userId && it.provider == provider }?.username
}
