package com.example.resolvers

import com.example.resolvers.resolverbases.TenantAssetPolicyResolvers
import com.example.services.GroupService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Group
import viaduct.api.grts.GroupStatus

@Resolver(objectValueFragment = "fragment _ on TenantAssetPolicy { groupId }")
class TenantAssetPolicyGroupResolver(
    private val groupService: GroupService
) : TenantAssetPolicyResolvers.Group() {
    override suspend fun resolve(ctx: Context): Group {
        val groupId = ctx.getObjectValue().getGroupId()
        val entity = groupService.getGroupById(ctx.authenticatedClient, groupId)
            ?: error("Group not found: $groupId")
        return Group.Builder(ctx)
            .id(ctx.globalIDFor(Group.Reflection, entity.id))
            .name(entity.name)
            .description(entity.description)
            .createdBy(entity.created_by)
            .status(GroupStatus.valueOf(entity.status))
            .createdAt(entity.created_at)
            .updatedAt(entity.updated_at)
            .build()
    }
}
