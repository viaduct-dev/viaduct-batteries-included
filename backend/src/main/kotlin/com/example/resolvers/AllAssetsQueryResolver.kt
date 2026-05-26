package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AdminAssetView

@Resolver
class AllAssetsQueryResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.AllAssets() {
    override suspend fun resolve(ctx: Context): List<AdminAssetView> {
        val client = ctx.authenticatedClient
        val results = mutableListOf<AdminAssetView>()

        viaAccessService.getGitHubRepoAssets(client).forEach { (asset, _) ->
            results += AdminAssetView.Builder(ctx)
                .id(asset.id)
                .assetType(asset.asset_type)
                .externalId(asset.external_id)
                .tenantName(asset.tenant_name)
                .name(asset.name)
                .build()
        }
        viaAccessService.getGitHubTeamAssets(client).forEach { (asset, _) ->
            results += AdminAssetView.Builder(ctx)
                .id(asset.id)
                .assetType(asset.asset_type)
                .externalId(asset.external_id)
                .tenantName(asset.tenant_name)
                .name(asset.name)
                .build()
        }
        viaAccessService.getAsanaProjectAssets(client).forEach { (asset, _) ->
            results += AdminAssetView.Builder(ctx)
                .id(asset.id)
                .assetType(asset.asset_type)
                .externalId(asset.external_id)
                .tenantName(asset.tenant_name)
                .name(asset.name)
                .build()
        }
        viaAccessService.getAsanaPortfolioAssets(client).forEach { (asset, _) ->
            results += AdminAssetView.Builder(ctx)
                .id(asset.id)
                .assetType(asset.asset_type)
                .externalId(asset.external_id)
                .tenantName(asset.tenant_name)
                .name(asset.name)
                .build()
        }
        return results
    }
}
