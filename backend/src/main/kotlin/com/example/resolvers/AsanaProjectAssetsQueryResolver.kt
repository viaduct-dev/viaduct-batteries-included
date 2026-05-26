package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.AsanaProjectAssetEntity
import com.example.services.AssetEntity
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaProjectAsset

@Resolver
class AsanaProjectAssetsQueryResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.AsanaProjectAssets() {
    override suspend fun resolve(ctx: Context): List<AsanaProjectAsset> {
        return viaAccessService.getAsanaProjectAssets(ctx.authenticatedClient)
            .map { (asset, project) -> project.toGrt(ctx, asset) }
    }
}

internal fun AsanaProjectAssetEntity.toGrt(ctx: viaduct.api.context.ExecutionContext, asset: AssetEntity): AsanaProjectAsset =
    AsanaProjectAsset.Builder(ctx)
        .id(ctx.globalIDFor(AsanaProjectAsset.Reflection, asset.id))
        .name(asset.name)
        .gid(gid)
        .createdAt(asset.created_at)
        .build()
