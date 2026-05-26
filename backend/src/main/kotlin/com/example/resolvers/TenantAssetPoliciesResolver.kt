package com.example.resolvers

import com.example.resolvers.resolverbases.TenantAssetResolvers
import com.example.services.TenantAssetPolicyEntity
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.TenantAsset
import viaduct.api.grts.TenantAssetPolicy
import viaduct.api.grts.TenantPermission

@Resolver(objectValueFragment = "fragment _ on TenantAsset { id }")
class TenantAssetPoliciesResolver(
    private val viaAccessService: ViaAccessService
) : TenantAssetResolvers.Policies() {
    override suspend fun resolve(ctx: Context): List<TenantAssetPolicy> {
        val tenantAssetId = ctx.getObjectValue().getId().internalID
        return viaAccessService.getTenantAssetPolicies(ctx.authenticatedClient, tenantAssetId)
            .map { it.toGrt(ctx) }
    }
}

internal fun TenantAssetPolicyEntity.toGrt(ctx: viaduct.api.context.ExecutionContext): TenantAssetPolicy =
    TenantAssetPolicy.Builder(ctx)
        .id(ctx.globalIDFor(TenantAssetPolicy.Reflection, id))
        .permission(TenantPermission.valueOf(permission))
        .createdAt(created_at)
        .build()
