package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.SyncJob

@Resolver
class ImportAsanaAssetResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.ImportAsanaAsset() {
    override suspend fun resolve(ctx: Context): SyncJob {
        ctx.requireTenantPermission(authService, "asana", TenantPermission.EDITOR)
        val assetId = ctx.arguments.assetId
        val asset = viaAccessService.getAsanaProjectAssetById(ctx.authenticatedClient, assetId)?.first
            ?: viaAccessService.getAsanaPortfolioAssets(ctx.authenticatedClient)
                .firstOrNull { it.first.id == assetId }?.first
            ?: error("Asset not found: $assetId")
        val entity = viaAccessService.createSyncJob(
            ctx.authenticatedClient, assetId, asset.asset_type, asset.tenant_name, "IMPORT_ASSET"
        )
        return entity.toGrt(ctx)
    }
}
