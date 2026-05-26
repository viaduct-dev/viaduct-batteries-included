package com.example.resolvers

import com.example.resolvers.resolverbases.AsanaProjectAssetResolvers
import com.example.services.AsanaProjectPolicyEntity
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaPermission
import viaduct.api.grts.AsanaProjectPolicy
import viaduct.api.grts.PolicySyncStatus

@Resolver(objectValueFragment = "fragment _ on AsanaProjectAsset { id }")
class AsanaProjectPoliciesResolver(
    private val viaAccessService: ViaAccessService
) : AsanaProjectAssetResolvers.Policies() {
    override suspend fun resolve(ctx: Context): List<AsanaProjectPolicy> {
        val assetId = ctx.getObjectValue().getId().internalID
        return viaAccessService.getAsanaProjectPoliciesByAsset(ctx.authenticatedClient, assetId)
            .map { it.toGrt(ctx) }
    }
}

internal fun AsanaProjectPolicyEntity.toGrt(ctx: viaduct.api.context.ExecutionContext): AsanaProjectPolicy =
    AsanaProjectPolicy.Builder(ctx)
        .id(ctx.globalIDFor(AsanaProjectPolicy.Reflection, id))
        .groupId(group_id)
        .assetId(asset_id)
        .permission(AsanaPermission.valueOf(permission))
        .syncStatus(PolicySyncStatus.valueOf(sync_status))
        .build()
