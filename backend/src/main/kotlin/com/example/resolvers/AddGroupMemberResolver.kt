package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.GroupService
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.GroupMember

/**
 * Resolver for the addGroupMember mutation.
 * Adds a user to a checkbox group.
 * Only the group owner can add members (enforced by RLS).
 */
@Resolver
class AddGroupMemberResolver(
    private val groupService: GroupService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.AddGroupMember() {
    override suspend fun resolve(ctx: Context): GroupMember {
        ctx.requireTenantPermission(authService, "default", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        // Use Viaduct's internalID property to get the UUID
        val groupId = input.groupId.internalID

        val memberEntity = groupService.addGroupMember(
            authenticatedClient = ctx.authenticatedClient,
            groupId = groupId,
            userId = input.userId
        )

        // Enqueue a reconcile job for every asset affected by this group's policies
        val affectedAssets = authService.getAssetsAffectedByGroup(ctx.authenticatedClient, groupId)
        for (asset in affectedAssets) {
            ctx.authenticatedClient.createSyncJob(asset.assetId, asset.assetType, asset.tenantName, "RECONCILE_ASSET")
        }

        return GroupMember.Builder(ctx)
            .id(memberEntity.id)
            .groupId(memberEntity.group_id)
            .userId(memberEntity.user_id)
            .joinedAt(memberEntity.joined_at)
            .build()
    }
}
