package com.example.resolvers

import com.example.resolvers.resolverbases.AsanaPortfolioAssetResolvers
import com.example.services.AsanaPortfolioPolicyEntity
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaPermission
import viaduct.api.grts.AsanaPortfolioPolicy
import viaduct.api.grts.PolicySyncStatus

@Resolver(objectValueFragment = "fragment _ on AsanaPortfolioAsset { id }")
class AsanaPortfolioPoliciesResolver(
    private val viaAccessService: ViaAccessService
) : AsanaPortfolioAssetResolvers.Policies() {
    override suspend fun resolve(ctx: Context): List<AsanaPortfolioPolicy> {
        val assetId = ctx.getObjectValue().getId().internalID
        return viaAccessService.getAsanaPortfolioPoliciesByAsset(ctx.authenticatedClient, assetId)
            .map { it.toGrt(ctx) }
    }
}

internal fun AsanaPortfolioPolicyEntity.toGrt(ctx: viaduct.api.context.ExecutionContext): AsanaPortfolioPolicy =
    AsanaPortfolioPolicy.Builder(ctx)
        .id(ctx.globalIDFor(AsanaPortfolioPolicy.Reflection, id))
        .groupId(group_id)
        .assetId(asset_id)
        .permission(AsanaPermission.valueOf(permission))
        .syncStatus(PolicySyncStatus.valueOf(sync_status))
        .build()
