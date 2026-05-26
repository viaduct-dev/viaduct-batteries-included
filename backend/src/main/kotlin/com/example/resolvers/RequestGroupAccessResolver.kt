package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AccessRequest
import viaduct.api.grts.AccessRequestStatus

@Resolver
class RequestGroupAccessResolver(
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.RequestGroupAccess() {
    override suspend fun resolve(ctx: Context): AccessRequest {
        val input = ctx.arguments.input
        val assetId = input.assetId
        val groupId = input.groupId.internalID

        val asset = ctx.authenticatedClient.getAssetById(assetId)
            ?: error("Asset not found: $assetId")

        if (!asset.requestable) {
            error("Asset is not available for self-service access requests")
        }

        // Require at least REQUESTER on this asset's tenant
        ctx.requireTenantPermission(authService, asset.tenant_name, TenantPermission.REQUESTER)

        val available = availablePermissionsFor(asset.asset_type)
        if (input.requestedPermission !in available) {
            error("Invalid permission '${input.requestedPermission}' for asset type ${asset.asset_type}. Valid: $available")
        }

        val entity = ctx.authenticatedClient.createAccessRequest(
            tenantName = asset.tenant_name,
            assetId = assetId,
            groupId = groupId,
            requestedPermission = input.requestedPermission,
            requestedBy = ctx.userId,
        )

        ctx.authenticatedClient.insertAuditEvent(
            actorId = ctx.userId,
            eventType = "ACCESS_REQUEST_CREATED",
            targetType = "AccessRequest",
            targetId = entity.id,
            after = """{"status":"PENDING","requested_permission":"${input.requestedPermission}"}""",
        )

        return entity.toGrt(ctx)
    }
}
