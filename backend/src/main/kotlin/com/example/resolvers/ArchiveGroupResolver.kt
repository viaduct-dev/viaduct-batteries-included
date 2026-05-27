package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.GroupService
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Group
import viaduct.api.grts.GroupStatus

@Resolver
class ArchiveGroupResolver(
    private val groupService: GroupService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.ArchiveGroup() {
    override suspend fun resolve(ctx: Context): Group {
        ctx.requireTenantPermission(authService, "default", TenantPermission.EDITOR)

        val groupId = ctx.arguments.input.groupId.internalID

        // Mark as DELETING and kick off reconcile jobs. The group stays DELETING until
        // all reconcile jobs complete and the sync worker advances it to ARCHIVED.
        val groupEntity = groupService.updateGroupStatus(ctx.authenticatedClient, groupId, "DELETING")

        val affectedAssets = authService.getAssetsAffectedByGroup(ctx.authenticatedClient, groupId)
        for (asset in affectedAssets) {
            ctx.authenticatedClient.createSyncJob(asset.assetId, asset.assetType, asset.tenantName, "RECONCILE_ASSET")
        }

        return Group.Builder(ctx)
            .id(ctx.globalIDFor(Group.Reflection, groupEntity.id))
            .name(groupEntity.name)
            .description(groupEntity.description)
            .createdBy(groupEntity.created_by)
            .status(GroupStatus.DELETING)
            .createdAt(groupEntity.created_at)
            .updatedAt(groupEntity.updated_at)
            .build()
    }
}
