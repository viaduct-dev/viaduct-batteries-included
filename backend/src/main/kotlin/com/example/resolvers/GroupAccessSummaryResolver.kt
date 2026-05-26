package com.example.resolvers

import com.example.resolvers.resolverbases.GroupResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.PolicySummaryEntry
import viaduct.api.grts.PolicySyncStatus

@Resolver(objectValueFragment = "fragment _ on Group { id }")
class GroupAccessSummaryResolver(
    private val viaAccessService: ViaAccessService
) : GroupResolvers.AccessSummary() {
    override suspend fun resolve(ctx: Context): List<PolicySummaryEntry> {
        val groupId = ctx.getObjectValue().getId().internalID
        val rows = viaAccessService.getPolicySummaryForGroup(ctx.authenticatedClient, groupId)
        return rows.map { row ->
            PolicySummaryEntry.Builder(ctx)
                .assetType(row.asset_type)
                .assetName(row.asset_name)
                .externalId(row.external_id)
                .permission(row.permission)
                .syncStatus(PolicySyncStatus.valueOf(row.sync_status))
                .build()
        }
    }
}
