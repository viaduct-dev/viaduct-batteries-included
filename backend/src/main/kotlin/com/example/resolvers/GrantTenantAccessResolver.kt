package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.TenantAssetPolicy
import viaduct.api.grts.TenantPermission

@Resolver
class GrantTenantAccessResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.GrantTenantAccess() {
    override suspend fun resolve(ctx: Context): TenantAssetPolicy {
        val input = ctx.arguments.input
        val tenantAssetId = input.tenantAssetId.internalID
        val groupId = input.groupId.internalID
        val permission = input.permission.name

        // Resolve the tenant asset to get the tenant name, then enforce OWNER permission
        val tenantAsset = viaAccessService.getTenantAssetById(ctx.authenticatedClient, tenantAssetId)
            ?: throw IllegalArgumentException("Tenant asset not found: $tenantAssetId")
        ctx.requireTenantPermission(authService, tenantAsset.tenant_name, com.example.services.TenantPermission.OWNER)

        val entity = viaAccessService.createTenantAssetPolicy(
            ctx.authenticatedClient, tenantAssetId, groupId, permission
        )
        return TenantAssetPolicy.Builder(ctx)
            .id(ctx.globalIDFor(TenantAssetPolicy.Reflection, entity.id))
            .tenantAssetId(entity.tenant_asset_id)
            .groupId(entity.group_id)
            .permission(TenantPermission.valueOf(entity.permission))
            .createdAt(entity.created_at)
            .build()
    }
}
