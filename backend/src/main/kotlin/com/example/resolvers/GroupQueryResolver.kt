package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.GroupService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Group

/**
 * Resolver for the group query.
 * Returns a specific group by ID if the user is a member.
 */
@Resolver
class GroupQueryResolver(
    private val groupService: GroupService
) : QueryResolvers.Group() {
    override suspend fun resolve(ctx: Context): Group? {
        // Use Viaduct's internalID property to get the UUID
        val groupId = ctx.arguments.id.internalID

        val groupEntity = groupService.getGroupById(ctx.authenticatedClient, groupId) ?: return null

        return Group.Builder(ctx)
            .id(ctx.arguments.id)
            .name(groupEntity.name)
            .description(groupEntity.description)
            .createdBy(groupEntity.created_by)
            .status(viaduct.api.grts.GroupStatus.valueOf(groupEntity.status))
            .createdAt(groupEntity.created_at)
            .updatedAt(groupEntity.updated_at)
            .build()
    }
}
