package com.example.resolvers

import com.example.resolvers.resolverbases.AsanaProjectAssetResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.ProviderUserView

@Resolver(objectValueFragment = "fragment _ on AsanaProjectAsset { id }")
class AsanaProjectProviderUsersResolver(
    private val viaAccessService: ViaAccessService,
) : AsanaProjectAssetResolvers.ProviderUsers() {
    override suspend fun resolve(ctx: Context): List<ProviderUserView> {
        val assetId = ctx.getObjectValue().getId().internalID
        val policies = viaAccessService.getAsanaProjectPoliciesByAsset(ctx.authenticatedClient, assetId)
        val groupIds = policies.map { it.group_id }.distinct()
        return buildProviderUserViews(ctx, ctx.authenticatedClient, viaAccessService, groupIds, "asana")
    }
}
