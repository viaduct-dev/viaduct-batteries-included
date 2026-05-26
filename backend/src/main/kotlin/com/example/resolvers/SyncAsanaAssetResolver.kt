package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.SyncJob

@Resolver
class SyncAsanaAssetResolver(
    private val viaAccessService: ViaAccessService
) : MutationResolvers.SyncAsanaAsset() {
    override suspend fun resolve(ctx: Context): SyncJob {
        val assetId = ctx.arguments.assetId
        val asset = viaAccessService.getAsanaProjectAssetById(ctx.authenticatedClient, assetId)?.first
            ?: viaAccessService.getAsanaPortfolioAssets(ctx.authenticatedClient)
                .firstOrNull { it.first.id == assetId }?.first
            ?: error("Asset not found: $assetId")
        val entity = viaAccessService.createSyncJob(
            ctx.authenticatedClient, assetId, asset.asset_type, asset.tenant_name, "RECONCILE_ASSET"
        )
        return entity.toGrt(ctx)
    }
}
