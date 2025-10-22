package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.QueryResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.CheckboxGroup

/**
 * Resolver for the checkboxGroup query.
 * Returns a specific checkbox group by ID if the user is a member.
 */
@Resolver
class CheckboxGroupQueryResolver(
    private val groupService: GroupService
) : QueryResolvers.CheckboxGroup() {
    override suspend fun resolve(ctx: Context): CheckboxGroup? {
        // Use Viaduct's internalID property to get the UUID
        val groupId = ctx.arguments.id.internalID

        val groupEntity = groupService.getGroupById(ctx.authenticatedClient, groupId) ?: return null

        return CheckboxGroup.Builder(ctx)
            .id(ctx.arguments.id)  // Reuse the GlobalID from arguments instead of regenerating
            .name(groupEntity.name)
            .description(groupEntity.description)
            .ownerId(groupEntity.owner_id)
            .createdAt(groupEntity.created_at)
            .updatedAt(groupEntity.updated_at)
            .build()
    }
}
