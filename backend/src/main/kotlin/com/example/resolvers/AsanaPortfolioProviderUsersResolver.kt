package com.example.resolvers

import com.example.resolvers.resolverbases.AsanaPortfolioAssetResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.ProviderUserView

@Resolver(objectValueFragment = "fragment _ on AsanaPortfolioAsset { id }")
class AsanaPortfolioProviderUsersResolver(
    private val viaAccessService: ViaAccessService,
) : AsanaPortfolioAssetResolvers.ProviderUsers() {
    override suspend fun resolve(ctx: Context): List<ProviderUserView> {
        val assetId = ctx.getObjectValue().getId().internalID
        val policies = viaAccessService.getAsanaPortfolioPoliciesByAsset(ctx.authenticatedClient, assetId)
        val groupIds = policies.map { it.group_id }.distinct()
        return buildProviderUserViews(ctx, ctx.authenticatedClient, viaAccessService, groupIds, "asana")
    }
}
