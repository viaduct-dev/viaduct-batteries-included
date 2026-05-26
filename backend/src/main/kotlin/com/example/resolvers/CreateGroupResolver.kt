package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.GroupService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Group

/**
 * Resolver for the createGroup mutation.
 * Any authenticated user may create their own group — no tenant permission required.
 * Managing other groups' membership requires default-tenant EDITOR (enforced in
 * AddGroupMemberResolver / RemoveGroupMemberResolver).
 */
@Resolver
class CreateGroupResolver(
    private val groupService: GroupService,
) : MutationResolvers.CreateGroup() {
    override suspend fun resolve(ctx: Context): Group {
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
