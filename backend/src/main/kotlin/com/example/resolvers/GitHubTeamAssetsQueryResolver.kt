package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.AssetEntity
import com.example.services.GitHubTeamAssetEntity
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.GitHubTeamAsset

@Resolver
class GitHubTeamAssetsQueryResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.GithubTeamAssets() {
    override suspend fun resolve(ctx: Context): List<GitHubTeamAsset> {
        return viaAccessService.getGitHubTeamAssets(ctx.authenticatedClient)
            .map { (asset, team) -> team.toGrt(ctx, asset) }
    }
}

internal fun GitHubTeamAssetEntity.toGrt(ctx: viaduct.api.context.ExecutionContext, asset: AssetEntity): GitHubTeamAsset =
    GitHubTeamAsset.Builder(ctx)
        .id(ctx.globalIDFor(GitHubTeamAsset.Reflection, asset.id))
        .name(asset.name)
        .org(org)
        .slug(slug)
        .createdAt(asset.created_at)
        .build()
