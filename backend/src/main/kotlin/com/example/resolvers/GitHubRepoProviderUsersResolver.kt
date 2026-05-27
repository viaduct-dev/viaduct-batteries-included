package com.example.resolvers

import com.example.resolvers.resolverbases.GitHubRepoAssetResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.ProviderUserView

@Resolver(objectValueFragment = "fragment _ on GitHubRepoAsset { id }")
class GitHubRepoProviderUsersResolver(
    private val viaAccessService: ViaAccessService,
) : GitHubRepoAssetResolvers.ProviderUsers() {
    override suspend fun resolve(ctx: Context): List<ProviderUserView> {
        val assetId = ctx.getObjectValue().getId().internalID
        val policies = viaAccessService.getGitHubRepoPoliciesByAsset(ctx.authenticatedClient, assetId)
        val groupIds = policies.map { it.group_id }.distinct()
        return buildProviderUserViews(ctx, ctx.authenticatedClient, viaAccessService, groupIds, "github")
    }
}
