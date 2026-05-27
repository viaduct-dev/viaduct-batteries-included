package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AccessRequest

@Resolver
class ApproveAccessRequestResolver(
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.ApproveAccessRequest() {
    override suspend fun resolve(ctx: Context): AccessRequest {
        val requestId = ctx.arguments.id.internalID
        val note = ctx.arguments.note

        val request = ctx.authenticatedClient.getAccessRequestById(requestId)
            ?: error("Access request not found: $requestId")

        if (request.status != "PENDING") {
            error("Access request is not in PENDING state (current: ${request.status})")
        }

        ctx.requireTenantPermission(authService, request.tenant_name, TenantPermission.EDITOR)

        val asset = ctx.authenticatedClient.getAssetById(request.asset_id)
            ?: error("Asset not found: ${request.asset_id}")

        // CAS first — guards against double-approve. Only write the live policy if we win.
        val updated = ctx.authenticatedClient.transitionAccessRequestFromPending(
            id = requestId,
            status = "APPROVED",
            reviewedBy = ctx.userId,
            reviewerNote = note,
        ) ?: error("Access request was already processed by another reviewer")

        // Policy upsert is idempotent — safe to retry if enqueue or later steps fail.
        when (asset.asset_type) {
            "GITHUB_REPO"     -> ctx.authenticatedClient.upsertGitHubRepoPolicy(request.asset_id, request.group_id, request.requested_permission)
            "GITHUB_TEAM"     -> ctx.authenticatedClient.upsertGitHubTeamPolicy(request.asset_id, request.group_id, request.requested_permission)
            "ASANA_PROJECT"   -> ctx.authenticatedClient.upsertAsanaProjectPolicy(request.asset_id, request.group_id, request.requested_permission)
            "ASANA_PORTFOLIO" -> ctx.authenticatedClient.upsertAsanaPortfolioPolicy(request.asset_id, request.group_id, request.requested_permission)
            else -> error("Unsupported asset type: ${asset.asset_type}")
        }

        ctx.authenticatedClient.createSyncJob(request.asset_id, asset.asset_type, asset.tenant_name, "RECONCILE_ASSET")

        ctx.authenticatedClient.insertAuditEvent(
            actorId = ctx.userId,
            eventType = "ACCESS_REQUEST_APPROVED",
            targetType = "AccessRequest",
            targetId = requestId,
            after = """{"status":"APPROVED","reviewer_note":${if (note != null) "\"$note\"" else "null"}}""",
        )

        return updated.toGrt(ctx)
    }
}
