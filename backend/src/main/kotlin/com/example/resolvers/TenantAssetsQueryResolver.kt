package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.TenantAsset

@Resolver
class TenantAssetsQueryResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.TenantAssets() {
    override suspend fun resolve(ctx: Context): List<TenantAsset> {
        return viaAccessService.getTenantAssets(ctx.authenticatedClient).map { it.toGrt(ctx) }
    }
}

internal fun com.example.services.TenantAssetEntity.toGrt(ctx: viaduct.api.context.ExecutionContext): TenantAsset =
    TenantAsset.Builder(ctx)
        .id(ctx.globalIDFor(TenantAsset.Reflection, id))
        .tenantName(tenant_name)
        .createdAt(created_at)
        .build()
