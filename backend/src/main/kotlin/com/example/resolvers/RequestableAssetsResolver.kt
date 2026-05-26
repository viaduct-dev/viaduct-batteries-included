package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import viaduct.api.resolver.Resolver
import viaduct.api.grts.RequestableAsset

@Resolver
class RequestableAssetsResolver : QueryResolvers.RequestableAssets() {
    override suspend fun resolve(ctx: Context): List<RequestableAsset> {
        val tenantName = ctx.arguments.tenantName
        val query = ctx.arguments.query

        val assets = ctx.authenticatedClient.getRequestableAssets(tenantName, query)

        return assets.map { asset ->
            val available = availablePermissionsFor(asset.asset_type)
            RequestableAsset.Builder(ctx)
                .id(asset.id)
                .tenantName(asset.tenant_name)
                .assetType(asset.asset_type)
                .name(asset.name)
                .externalId(asset.external_id)
                .availablePermissions(available)
                .build()
        }
    }
}
