package com.example.resolvers

import com.example.resolvers.resolverbases.TenantAssetPolicyResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.TenantAsset

@Resolver(objectValueFragment = "fragment _ on TenantAssetPolicy { tenantAssetId }")
class TenantAssetPolicyTenantAssetResolver(
    private val viaAccessService: ViaAccessService
) : TenantAssetPolicyResolvers.TenantAsset() {
    override suspend fun resolve(ctx: Context): TenantAsset {
        val tenantAssetId = ctx.getObjectValue().getTenantAssetId()
        val entity = viaAccessService.getTenantAssetById(ctx.authenticatedClient, tenantAssetId)
            ?: error("TenantAsset not found: $tenantAssetId")
        return entity.toGrt(ctx)
    }
}
