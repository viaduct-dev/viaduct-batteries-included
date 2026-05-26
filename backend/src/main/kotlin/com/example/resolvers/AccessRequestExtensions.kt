package com.example.resolvers

import com.example.services.AccessRequestEntity
import viaduct.api.grts.AccessRequest
import viaduct.api.grts.AccessRequestStatus

internal fun AccessRequestEntity.toGrt(ctx: viaduct.api.context.ExecutionContext): AccessRequest =
    AccessRequest.Builder(ctx)
        .id(ctx.globalIDFor(AccessRequest.Reflection, id))
        .tenantName(tenant_name)
        .assetId(asset_id)
        .groupId(group_id)
        .requestedPermission(requested_permission)
        .status(AccessRequestStatus.valueOf(status))
        .requestedBy(requested_by)
        .reviewedBy(reviewed_by)
        .requestedAt(requested_at)
        .reviewedAt(reviewed_at)
        .reviewerNote(reviewer_note)
        .build()

internal fun availablePermissionsFor(assetType: String): List<String> = when (assetType) {
    "GITHUB_REPO"     -> listOf("READ", "WRITE", "ADMIN")
    "GITHUB_TEAM"     -> listOf("MEMBER", "MAINTAINER")
    "ASANA_PROJECT"   -> listOf("MEMBER", "EDITOR", "COMMENTER")
    "ASANA_PORTFOLIO" -> listOf("MEMBER", "EDITOR")
    else              -> listOf("VIEWER", "EDITOR", "OWNER")
}
