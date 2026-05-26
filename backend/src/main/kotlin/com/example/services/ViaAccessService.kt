package com.example.services

import com.example.AuthenticatedSupabaseClient

open class ViaAccessService(
    internal val supabaseService: com.example.SupabaseService
) {
    open suspend fun getTenantAssets(client: AuthenticatedSupabaseClient): List<TenantAssetEntity> =
        client.getTenantAssets()

    open suspend fun getTenantAssetByName(client: AuthenticatedSupabaseClient, tenantName: String): TenantAssetEntity? =
        client.getTenantAssetByName(tenantName)

    open suspend fun getTenantAssetById(client: AuthenticatedSupabaseClient, id: String): TenantAssetEntity? =
        client.getTenantAssetById(id)

    open suspend fun getTenantAssetPolicies(client: AuthenticatedSupabaseClient, tenantAssetId: String): List<TenantAssetPolicyEntity> =
        client.getTenantAssetPolicies(tenantAssetId)

    open suspend fun createTenantAssetPolicy(
        client: AuthenticatedSupabaseClient,
        tenantAssetId: String,
        groupId: String,
        permission: String
    ): TenantAssetPolicyEntity = client.createTenantAssetPolicy(tenantAssetId, groupId, permission)

    open suspend fun deleteTenantAssetPolicy(client: AuthenticatedSupabaseClient, id: String): Boolean =
        client.deleteTenantAssetPolicy(id)

    open suspend fun getSyncJob(client: AuthenticatedSupabaseClient, id: String): SyncJobEntity? =
        client.getSyncJobById(id)

    open suspend fun createSyncJob(
        client: AuthenticatedSupabaseClient,
        assetId: String,
        assetType: String,
        tenantName: String,
        action: String
    ): SyncJobEntity = client.createSyncJob(assetId, assetType, tenantName, action)

    open suspend fun getExternalIdentitiesForUser(client: AuthenticatedSupabaseClient, userId: String): List<ExternalIdentityEntity> =
        client.getExternalIdentitiesForUser(userId)

    open suspend fun upsertExternalIdentity(
        client: AuthenticatedSupabaseClient,
        userId: String,
        provider: String,
        externalUserId: String,
        externalUsername: String
    ): ExternalIdentityEntity = client.upsertExternalIdentity(userId, provider, externalUserId, externalUsername)

    open suspend fun getPolicySummaryForGroup(client: AuthenticatedSupabaseClient, groupId: String): List<PolicySummaryRow> =
        client.getPolicySummaryForGroup(groupId)

    open suspend fun getGitHubRepoAssets(client: AuthenticatedSupabaseClient): List<Pair<AssetEntity, GitHubRepoAssetEntity>> =
        client.getGitHubRepoAssets()

    open suspend fun getGitHubRepoAssetById(client: AuthenticatedSupabaseClient, id: String): Pair<AssetEntity, GitHubRepoAssetEntity>? =
        client.getGitHubRepoAssetById(id)

    open suspend fun createGitHubRepoAsset(
        client: AuthenticatedSupabaseClient,
        owner: String,
        repo: String,
        name: String
    ): Pair<AssetEntity, GitHubRepoAssetEntity> = client.createGitHubRepoAsset(owner, repo, name)

    open suspend fun getGitHubTeamAssets(client: AuthenticatedSupabaseClient): List<Pair<AssetEntity, GitHubTeamAssetEntity>> =
        client.getGitHubTeamAssets()

    open suspend fun createGitHubTeamAsset(
        client: AuthenticatedSupabaseClient,
        org: String,
        slug: String,
        name: String
    ): Pair<AssetEntity, GitHubTeamAssetEntity> = client.createGitHubTeamAsset(org, slug, name)

    open suspend fun getGitHubRepoPoliciesByAsset(client: AuthenticatedSupabaseClient, assetId: String): List<GitHubRepoPolicyEntity> =
        client.getGitHubRepoPoliciesByAsset(assetId)

    open suspend fun createGitHubRepoPolicy(
        client: AuthenticatedSupabaseClient,
        assetId: String,
        groupId: String,
        permission: String
    ): GitHubRepoPolicyEntity = client.createGitHubRepoPolicy(assetId, groupId, permission)

    open suspend fun deleteGitHubRepoPolicy(client: AuthenticatedSupabaseClient, id: String): Boolean =
        client.deleteGitHubRepoPolicy(id)

    open suspend fun getGitHubTeamPoliciesByAsset(client: AuthenticatedSupabaseClient, assetId: String): List<GitHubTeamPolicyEntity> =
        client.getGitHubTeamPoliciesByAsset(assetId)

    open suspend fun createGitHubTeamPolicy(
        client: AuthenticatedSupabaseClient,
        assetId: String,
        groupId: String,
        permission: String
    ): GitHubTeamPolicyEntity = client.createGitHubTeamPolicy(assetId, groupId, permission)

    open suspend fun deleteGitHubTeamPolicy(client: AuthenticatedSupabaseClient, id: String): Boolean =
        client.deleteGitHubTeamPolicy(id)

    open suspend fun getAsanaProjectAssets(client: AuthenticatedSupabaseClient): List<Pair<AssetEntity, AsanaProjectAssetEntity>> =
        client.getAsanaProjectAssets()

    open suspend fun getAsanaProjectAssetById(client: AuthenticatedSupabaseClient, id: String): Pair<AssetEntity, AsanaProjectAssetEntity>? =
        client.getAsanaProjectAssetById(id)

    open suspend fun createAsanaProjectAsset(
        client: AuthenticatedSupabaseClient,
        gid: String,
        name: String
    ): Pair<AssetEntity, AsanaProjectAssetEntity> = client.createAsanaProjectAsset(gid, name)

    open suspend fun getAsanaPortfolioAssets(client: AuthenticatedSupabaseClient): List<Pair<AssetEntity, AsanaPortfolioAssetEntity>> =
        client.getAsanaPortfolioAssets()

    open suspend fun createAsanaPortfolioAsset(
        client: AuthenticatedSupabaseClient,
        gid: String,
        name: String
    ): Pair<AssetEntity, AsanaPortfolioAssetEntity> = client.createAsanaPortfolioAsset(gid, name)

    open suspend fun getAsanaProjectPoliciesByAsset(client: AuthenticatedSupabaseClient, assetId: String): List<AsanaProjectPolicyEntity> =
        client.getAsanaProjectPoliciesByAsset(assetId)

    open suspend fun createAsanaProjectPolicy(
        client: AuthenticatedSupabaseClient,
        assetId: String,
        groupId: String,
        permission: String
    ): AsanaProjectPolicyEntity = client.createAsanaProjectPolicy(assetId, groupId, permission)

    open suspend fun deleteAsanaProjectPolicy(client: AuthenticatedSupabaseClient, id: String): Boolean =
        client.deleteAsanaProjectPolicy(id)

    open suspend fun getAsanaPortfolioPoliciesByAsset(client: AuthenticatedSupabaseClient, assetId: String): List<AsanaPortfolioPolicyEntity> =
        client.getAsanaPortfolioPoliciesByAsset(assetId)

    open suspend fun createAsanaPortfolioPolicy(
        client: AuthenticatedSupabaseClient,
        assetId: String,
        groupId: String,
        permission: String
    ): AsanaPortfolioPolicyEntity = client.createAsanaPortfolioPolicy(assetId, groupId, permission)

    open suspend fun deleteAsanaPortfolioPolicy(client: AuthenticatedSupabaseClient, id: String): Boolean =
        client.deleteAsanaPortfolioPolicy(id)

    open suspend fun getExternalIdentitiesByProvider(client: AuthenticatedSupabaseClient, provider: String): List<ExternalIdentityEntity> =
        client.getExternalIdentitiesByProviderWithUsers(provider)

    open suspend fun getProviderUsersForGroups(
        client: AuthenticatedSupabaseClient,
        groupIds: List<String>,
        provider: String
    ): List<ExternalIdentityEntity> {
        val members = client.getGroupMembersForGroups(groupIds)
        val userIds = members.map { it.user_id }.distinct()
        return client.getExternalIdentitiesForUsersAndProvider(userIds, provider)
    }

    open suspend fun inviteAndLinkUser(
        client: AuthenticatedSupabaseClient,
        email: String,
        provider: String,
        externalUserId: String,
        externalUsername: String
    ): Pair<String, ExternalIdentityEntity> {
        val userId = client.inviteUserByEmail(email, supabaseService.serviceRoleKey)
        val identity = client.upsertExternalIdentity(userId, provider, externalUserId, externalUsername)
        return userId to identity
    }
}
