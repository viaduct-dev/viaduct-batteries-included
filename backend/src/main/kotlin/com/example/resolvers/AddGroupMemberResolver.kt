package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.GroupService
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.GroupMember

@Resolver
class AddGroupMemberResolver(
    private val groupService: GroupService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.AddGroupMember() {
    override suspend fun resolve(ctx: Context): GroupMember {
        ctx.requireTenantPermission(authService, "default", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        val groupId = input.groupId.internalID

        val memberEntity = groupService.addGroupMember(
            authenticatedClient = ctx.authenticatedClient,
            groupId = groupId,
            personId = input.personId
        )

        val affectedAssets = authService.getAssetsAffectedByGroup(ctx.authenticatedClient, groupId)
        for (asset in affectedAssets) {
            ctx.authenticatedClient.createSyncJob(asset.assetId, asset.assetType, asset.tenantName, "RECONCILE_ASSET")
        }

        return GroupMember.Builder(ctx)
            .id(memberEntity.id)
            .groupId(memberEntity.group_id)
            .personId(memberEntity.person_id)
            .joinedAt(memberEntity.joined_at)
            .build()
    }
}
