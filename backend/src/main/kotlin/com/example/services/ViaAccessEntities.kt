package com.example.services

import kotlinx.serialization.Serializable

@Serializable
data class AssetEntity(
    val id: String,
    val asset_type: String,
    val tenant_name: String,
    val external_id: String,
    val name: String,
    val requestable: Boolean = false,
    val created_at: String
)

@Serializable
data class PolicyEntity(
    val id: String,
    val policy_type: String,
    val group_id: String,
    val created_at: String
)

@Serializable
data class TenantAssetEntity(
    val id: String,
    val tenant_name: String,
    val created_at: String
)

@Serializable
data class TenantAssetPolicyEntity(
    val id: String,
    val tenant_asset_id: String,
    val group_id: String,
    val permission: String,
    val created_at: String
)

@Serializable
data class GitHubRepoAssetEntity(
    val id: String,
    val owner: String,
    val repo: String
)

@Serializable
data class GitHubTeamAssetEntity(
    val id: String,
    val org: String,
    val slug: String
)

@Serializable
data class GitHubRepoPolicyEntity(
    val id: String,
    val asset_id: String,
    val group_id: String,
    val permission: String,
    val sync_status: String
)

@Serializable
data class GitHubTeamPolicyEntity(
    val id: String,
    val asset_id: String,
    val group_id: String,
    val permission: String,
    val sync_status: String
)

@Serializable
data class AsanaProjectAssetEntity(
    val id: String,
    val gid: String
)

@Serializable
data class AsanaPortfolioAssetEntity(
    val id: String,
    val gid: String
)

@Serializable
data class AsanaProjectPolicyEntity(
    val id: String,
    val asset_id: String,
    val group_id: String,
    val permission: String,
    val sync_status: String
)

@Serializable
data class AsanaPortfolioPolicyEntity(
    val id: String,
    val asset_id: String,
    val group_id: String,
    val permission: String,
    val sync_status: String
)

@Serializable
data class SyncJobEntity(
    val id: String,
    val asset_id: String,
    val asset_type: String,
    val tenant_name: String,
    val action: String,
    val status: String,
    val attempt_count: Int,
    val next_retry_at: String? = null,
    val last_error: String? = null,
    val plan_summary: String? = null,
    val created_at: String,
    val completed_at: String? = null
)

@Serializable
data class PersonEntity(
    val id: String,
    val display_name: String? = null,
    val email: String? = null,
    val auth_user_id: String? = null,
    val created_at: String
)

@Serializable
data class ExternalIdentityEntity(
    val id: String,
    val person_id: String,
    val provider: String,
    val external_user_id: String,
    val external_username: String,
    val verified_at: String? = null
)

@Serializable
data class AccessRequestEntity(
    val id: String,
    val tenant_name: String,
    val asset_id: String,
    val group_id: String,
    val requested_permission: String,
    val status: String,
    val requested_by: String,
    val reviewed_by: String? = null,
    val requested_at: String,
    val reviewed_at: String? = null,
    val reviewer_note: String? = null
)

// Join entity for cross-tenant policy summary queries
@Serializable
data class PolicySummaryRow(
    val asset_type: String,
    val asset_name: String,
    val external_id: String,
    val permission: String,
    val sync_status: String
)
