package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaProjectAsset

@Resolver
class AsanaProjectAssetQueryResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.AsanaProjectAsset() {
    override suspend fun resolve(ctx: Context): AsanaProjectAsset? {
        val assetId = ctx.arguments.id.internalID
        val (asset, project) = viaAccessService.getAsanaProjectAssetById(ctx.authenticatedClient, assetId) ?: return null
        return project.toGrt(ctx, asset)
    }
}
