package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AccessRequest

@Resolver
class PendingAccessRequestsResolver(
    private val authService: ViaAccessAuthorizationService,
) : QueryResolvers.PendingAccessRequests() {
    override suspend fun resolve(ctx: Context): List<AccessRequest> {
        val tenantName = ctx.arguments.tenantName

        // EDITOR/OWNER can see all pending requests for this tenant.
        // REQUESTER (or plain authenticated user) gets only their own requests.
        // RLS enforces the same boundary at the DB level as a safety net.
        val isEditor = try {
            ctx.requireTenantPermission(authService, tenantName, TenantPermission.EDITOR)
            true
        } catch (_: Exception) {
            false
        }

        val requests = ctx.authenticatedClient.getPendingAccessRequests(tenantName)
        val visible = if (isEditor) requests else requests.filter { it.requested_by == ctx.userId }

        return visible.map { it.toGrt(ctx) }
    }
}
