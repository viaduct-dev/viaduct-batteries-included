package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.TenantAsset

@Resolver
class TenantAssetQueryResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.TenantAsset() {
    override suspend fun resolve(ctx: Context): TenantAsset? {
        val tenantName = ctx.arguments.tenantName
        return viaAccessService.getTenantAssetByName(ctx.authenticatedClient, tenantName)?.toGrt(ctx)
    }
}
