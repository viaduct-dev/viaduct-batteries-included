package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.AssetEntity
import com.example.services.GitHubRepoAssetEntity
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.GitHubRepoAsset
import viaduct.api.grts.PolicySyncStatus

@Resolver
class GitHubRepoAssetsQueryResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.GithubRepoAssets() {
    override suspend fun resolve(ctx: Context): List<GitHubRepoAsset> {
        return viaAccessService.getGitHubRepoAssets(ctx.authenticatedClient)
            .map { (asset, repo) -> repo.toGrt(ctx, asset) }
    }
}

internal fun GitHubRepoAssetEntity.toGrt(ctx: viaduct.api.context.ExecutionContext, asset: AssetEntity): GitHubRepoAsset =
    GitHubRepoAsset.Builder(ctx)
        .id(ctx.globalIDFor(GitHubRepoAsset.Reflection, asset.id))
        .name(asset.name)
        .owner(owner)
        .repo(repo)
        .createdAt(asset.created_at)
        .build()
