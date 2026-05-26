package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver

@Resolver
class RevokeTenantAccessResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.RevokeTenantAccess() {
    override suspend fun resolve(ctx: Context): Boolean {
        val policyId = ctx.arguments.input.tenantAssetPolicyId.internalID

        // Resolve the policy -> tenant asset -> tenant name, then enforce OWNER permission
        val policy = ctx.authenticatedClient.getTenantAssetPolicyById(policyId)
            ?: throw IllegalArgumentException("Tenant asset policy not found: $policyId")
        val tenantAsset = viaAccessService.getTenantAssetById(ctx.authenticatedClient, policy.tenant_asset_id)
            ?: throw IllegalArgumentException("Tenant asset not found: ${policy.tenant_asset_id}")
        ctx.requireTenantPermission(authService, tenantAsset.tenant_name, com.example.services.TenantPermission.OWNER)

        return viaAccessService.deleteTenantAssetPolicy(ctx.authenticatedClient, policyId)
    }
}
