package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.GroupService
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Group

@Resolver
class CreateGroupResolver(
    private val groupService: GroupService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.CreateGroup() {
    override suspend fun resolve(ctx: Context): Group {
        ctx.requireTenantPermission(authService, "default", TenantPermission.EDITOR)
        val input = ctx.arguments.input
        val userId = ctx.userId

        val groupEntity = groupService.createGroup(
            authenticatedClient = ctx.authenticatedClient,
            name = input.name,
            description = input.description,
            createdBy = userId
        )

        return Group.Builder(ctx)
            .id(ctx.globalIDFor(Group.Reflection, groupEntity.id))
            .name(groupEntity.name)
            .description(groupEntity.description)
            .createdBy(groupEntity.created_by)
            .status(viaduct.api.grts.GroupStatus.ACTIVE)
            .createdAt(groupEntity.created_at)
            .updatedAt(groupEntity.updated_at)
            .build()
    }
}
