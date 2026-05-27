package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.GroupService
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver

@Resolver
class RemoveGroupMemberResolver(
    private val groupService: GroupService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.RemoveGroupMember() {
    override suspend fun resolve(ctx: Context): Boolean {
        ctx.requireTenantPermission(authService, "default", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        val groupId = input.groupId.internalID

        val affectedAssets = authService.getAssetsAffectedByGroup(ctx.authenticatedClient, groupId)

        val result = groupService.removeGroupMember(
            authenticatedClient = ctx.authenticatedClient,
            groupId = groupId,
            personId = input.personId
        )

        for (asset in affectedAssets) {
            ctx.authenticatedClient.createSyncJob(asset.assetId, asset.assetType, asset.tenantName, "RECONCILE_ASSET")
        }

        return result
    }
}
