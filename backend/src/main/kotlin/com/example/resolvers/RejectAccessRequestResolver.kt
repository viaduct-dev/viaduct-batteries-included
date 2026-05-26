package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AccessRequest

@Resolver
class RejectAccessRequestResolver(
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.RejectAccessRequest() {
    override suspend fun resolve(ctx: Context): AccessRequest {
        val requestId = ctx.arguments.id.internalID
        val note = ctx.arguments.note

        val request = ctx.authenticatedClient.getAccessRequestById(requestId)
            ?: error("Access request not found: $requestId")

        if (request.status != "PENDING") {
            error("Access request is not in PENDING state (current: ${request.status})")
        }

        ctx.requireTenantPermission(authService, request.tenant_name, TenantPermission.EDITOR)

        val updated = ctx.authenticatedClient.transitionAccessRequestFromPending(
            id = requestId,
            status = "REJECTED",
            reviewedBy = ctx.userId,
            reviewerNote = note,
        ) ?: error("Access request was already processed by another reviewer")

        ctx.authenticatedClient.insertAuditEvent(
            actorId = ctx.userId,
            eventType = "ACCESS_REQUEST_REJECTED",
            targetType = "AccessRequest",
            targetId = requestId,
            after = """{"status":"REJECTED","reviewer_note":${if (note != null) "\"$note\"" else "null"}}""",
        )

        return updated.toGrt(ctx)
    }
}
