package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver

@Resolver
class SetAssetRequestableResolver(
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.SetAssetRequestable() {
    override suspend fun resolve(ctx: Context): Boolean {
        val assetId = ctx.arguments.assetId
        val requestable = ctx.arguments.requestable

        val asset = ctx.authenticatedClient.getAssetById(assetId)
            ?: error("Asset not found: $assetId")

        ctx.requireTenantPermission(authService, asset.tenant_name, TenantPermission.EDITOR)
        ctx.authenticatedClient.setAssetRequestable(assetId, requestable)
        return requestable
    }
}
