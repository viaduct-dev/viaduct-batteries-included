package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.GitHubRepoAsset

@Resolver
class GitHubRepoAssetQueryResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.GithubRepoAsset() {
    override suspend fun resolve(ctx: Context): GitHubRepoAsset? {
        val assetId = ctx.arguments.id.internalID
        val (asset, repo) = viaAccessService.getGitHubRepoAssetById(ctx.authenticatedClient, assetId) ?: return null
        return repo.toGrt(ctx, asset)
    }
}
