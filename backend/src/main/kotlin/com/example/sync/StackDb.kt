package com.example.sync

/**
 * Data access interface used by stack programs.
 * Kept separate from AuthenticatedSupabaseClient so stack programs are testable
 * without a running Supabase instance.
 */
interface StackDb {
    suspend fun loadPoliciesForAsset(assetId: String): List<PolicyRow>
    suspend fun loadMembersForGroup(groupId: String): List<MemberRow>
    suspend fun loadExternalIdentity(personId: String, provider: String): String?
}

data class PolicyRow(
    val groupId: String,
    val permission: String,
)

data class MemberRow(
    val personId: String,
)
