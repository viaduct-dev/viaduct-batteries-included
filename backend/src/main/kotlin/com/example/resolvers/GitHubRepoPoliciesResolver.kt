package com.example.resolvers

import com.example.resolvers.resolverbases.GitHubRepoAssetResolvers
import com.example.services.GitHubRepoPolicyEntity
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.GitHubRepoPolicy
import viaduct.api.grts.GitHubRepoPermission
import viaduct.api.grts.PolicySyncStatus

@Resolver(objectValueFragment = "fragment _ on GitHubRepoAsset { id }")
class GitHubRepoPoliciesResolver(
    private val viaAccessService: ViaAccessService
) : GitHubRepoAssetResolvers.Policies() {
    override suspend fun resolve(ctx: Context): List<GitHubRepoPolicy> {
        val assetId = ctx.getObjectValue().getId().internalID
        return viaAccessService.getGitHubRepoPoliciesByAsset(ctx.authenticatedClient, assetId)
            .map { it.toGrt(ctx) }
    }
}

internal fun GitHubRepoPolicyEntity.toGrt(ctx: viaduct.api.context.ExecutionContext): GitHubRepoPolicy =
    GitHubRepoPolicy.Builder(ctx)
        .id(ctx.globalIDFor(GitHubRepoPolicy.Reflection, id))
        .groupId(group_id)
        .assetId(asset_id)
        .permission(GitHubRepoPermission.valueOf(permission))
        .syncStatus(PolicySyncStatus.valueOf(sync_status))
        .build()
