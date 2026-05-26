package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.GroupService
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver

/**
 * Resolver for the removeGroupMember mutation.
 * Removes a user from a checkbox group.
 * The group owner or the member themselves can remove the membership (enforced by RLS).
 */
@Resolver
class RemoveGroupMemberResolver(
    private val groupService: GroupService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.RemoveGroupMember() {
    override suspend fun resolve(ctx: Context): Boolean {
        ctx.requireTenantPermission(authService, "default", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        // Use Viaduct's internalID property to get the UUID
        val groupId = input.groupId.internalID

        // Snapshot affected assets before removing the member
        val affectedAssets = authService.getAssetsAffectedByGroup(ctx.authenticatedClient, groupId)

        val result = groupService.removeGroupMember(
            authenticatedClient = ctx.authenticatedClient,
            groupId = groupId,
            userId = input.userId
        )

        // Enqueue a reconcile job for every asset affected by this group's policies
        for (asset in affectedAssets) {
            ctx.authenticatedClient.createSyncJob(asset.assetId, asset.assetType, asset.tenantName, "RECONCILE_ASSET")
        }

        return result
    }
}
