package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AdminPolicyView

@Resolver
class GroupPoliciesQueryResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.GroupPolicies() {
    override suspend fun resolve(ctx: Context): List<AdminPolicyView> {
        val groupId = ctx.arguments.groupId
        val rows = viaAccessService.getPolicySummaryForGroup(ctx.authenticatedClient, groupId)
        return rows.map { row ->
            AdminPolicyView.Builder(ctx)
                .id("${groupId}:${row.external_id}")
                .groupId(groupId)
                .groupName("")
                .assetType(row.asset_type)
                .assetExternalId(row.external_id)
                .assetName(row.asset_name)
                .tenantName(row.asset_type.lowercase().substringBefore("_"))
                .permission(row.permission)
                .syncStatus(row.sync_status)
                .build()
        }
    }
}
