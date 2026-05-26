package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.AsanaPortfolioAssetEntity
import com.example.services.AssetEntity
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaPortfolioAsset

@Resolver
class AsanaPortfolioAssetsQueryResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.AsanaPortfolioAssets() {
    override suspend fun resolve(ctx: Context): List<AsanaPortfolioAsset> {
        return viaAccessService.getAsanaPortfolioAssets(ctx.authenticatedClient)
            .map { (asset, portfolio) -> portfolio.toGrt(ctx, asset) }
    }
}

internal fun AsanaPortfolioAssetEntity.toGrt(ctx: viaduct.api.context.ExecutionContext, asset: AssetEntity): AsanaPortfolioAsset =
    AsanaPortfolioAsset.Builder(ctx)
        .id(ctx.globalIDFor(AsanaPortfolioAsset.Reflection, asset.id))
        .name(asset.name)
        .gid(gid)
        .createdAt(asset.created_at)
        .build()
