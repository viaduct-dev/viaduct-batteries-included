package com.example.resolvers

import com.example.resolvers.resolverbases.GitHubTeamAssetResolvers
import com.example.services.GitHubTeamPolicyEntity
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.GitHubTeamPolicy
import viaduct.api.grts.GitHubTeamPermission
import viaduct.api.grts.PolicySyncStatus

@Resolver(objectValueFragment = "fragment _ on GitHubTeamAsset { id }")
class GitHubTeamPoliciesResolver(
    private val viaAccessService: ViaAccessService
) : GitHubTeamAssetResolvers.Policies() {
    override suspend fun resolve(ctx: Context): List<GitHubTeamPolicy> {
        val assetId = ctx.getObjectValue().getId().internalID
        return viaAccessService.getGitHubTeamPoliciesByAsset(ctx.authenticatedClient, assetId)
            .map { it.toGrt(ctx) }
    }
}

internal fun GitHubTeamPolicyEntity.toGrt(ctx: viaduct.api.context.ExecutionContext): GitHubTeamPolicy =
    GitHubTeamPolicy.Builder(ctx)
        .id(ctx.globalIDFor(GitHubTeamPolicy.Reflection, id))
        .groupId(group_id)
        .assetId(asset_id)
        .permission(GitHubTeamPermission.valueOf(permission))
        .syncStatus(PolicySyncStatus.valueOf(sync_status))
        .build()
