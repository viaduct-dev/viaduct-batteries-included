package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver

@Resolver
class DeleteGitHubRepoPolicyResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.DeleteGitHubRepoPolicy() {
    override suspend fun resolve(ctx: Context): Boolean {
        ctx.requireTenantPermission(authService, "github", TenantPermission.EDITOR)

        val policyId = ctx.arguments.input.policyId.internalID

        // Look up asset before deletion so we can enqueue reconcile
        val policy = ctx.authenticatedClient.getGitHubRepoPolicyById(policyId)
        val result = viaAccessService.deleteGitHubRepoPolicy(ctx.authenticatedClient, policyId)

        if (policy != null) {
            val asset = ctx.authenticatedClient.getAssetById(policy.asset_id)
            if (asset != null) {
                ctx.authenticatedClient.createSyncJob(asset.id, asset.asset_type, asset.tenant_name, "RECONCILE_ASSET")
            }
        }

        return result
    }
}
