package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AccessRequest

@Resolver
class CancelAccessRequestResolver(
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.CancelAccessRequest() {
    override suspend fun resolve(ctx: Context): AccessRequest {
        val requestId = ctx.arguments.id.internalID

        val request = ctx.authenticatedClient.getAccessRequestById(requestId)
            ?: error("Access request not found: $requestId")

        if (request.status != "PENDING") {
            error("Access request is not in PENDING state (current: ${request.status})")
        }

        // Cancellation is allowed by the requester themselves or a tenant EDITOR/OWNER
        val isOwnRequest = request.requested_by == ctx.userId
        if (!isOwnRequest) {
            ctx.requireTenantPermission(authService, request.tenant_name, TenantPermission.EDITOR)
        }

        val updated = ctx.authenticatedClient.transitionAccessRequestFromPending(
            id = requestId,
            status = "CANCELED",
            reviewedBy = ctx.userId,
        ) ?: error("Access request was already processed by another reviewer")

        ctx.authenticatedClient.insertAuditEvent(
            actorId = ctx.userId,
            eventType = "ACCESS_REQUEST_CANCELED",
            targetType = "AccessRequest",
            targetId = requestId,
            after = """{"status":"CANCELED"}""",
        )

        return updated.toGrt(ctx)
    }
}
