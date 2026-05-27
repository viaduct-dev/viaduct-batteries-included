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

        ctx.requireTenantPermission(authService, request.tenant_name, TenantPermission.EDITOR)

        // Allow replaying an already-APPROVED request so that a partial failure
        // (policy write or sync enqueue threw after the CAS committed) can be
        // retried without reverting the status.  Any other terminal status is a
        // hard error.
        if (request.status != "PENDING" && request.status != "APPROVED") {
            error("Access request is not approvable (current status: ${request.status})")
        }

        val asset = ctx.authenticatedClient.getAssetById(request.asset_id)
            ?: error("Asset not found: ${request.asset_id}")

        // CAS: atomically move PENDING → APPROVED. Returns null if another reviewer
        // already won the race (or we are replaying an APPROVED request).
        val updated = if (request.status == "PENDING") {
            ctx.authenticatedClient.transitionAccessRequestFromPending(
                id = requestId,
                status = "APPROVED",
                reviewedBy = ctx.userId,
                reviewerNote = note,
            ) ?: error("Access request was already processed by another reviewer")
        } else {
            request  // replay: CAS already committed, re-run idempotent steps below
        }

        // These three steps are all idempotent — safe to re-run on retry.
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
