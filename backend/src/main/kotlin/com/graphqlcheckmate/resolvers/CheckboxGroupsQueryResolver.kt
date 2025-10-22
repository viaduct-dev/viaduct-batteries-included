package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.QueryResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.CheckboxGroup

/**
 * Resolver for the checkboxGroups query.
 * Returns all checkbox groups that the authenticated user is a member of.
 */
@Resolver
class CheckboxGroupsQueryResolver(
    private val groupService: GroupService
) : QueryResolvers.CheckboxGroups() {
    override suspend fun resolve(ctx: Context): List<CheckboxGroup> {
        // Use extension property - no need to know about RequestContext!
        val groupEntities = groupService.getUserGroups(ctx.authenticatedClient)

        return groupEntities.map { entity ->
            CheckboxGroup.Builder(ctx)
                .id(ctx.globalIDFor(CheckboxGroup.Reflection, entity.id))
                .name(entity.name)
                .description(entity.description)
                .ownerId(entity.owner_id)
                .createdAt(entity.created_at)
                .updatedAt(entity.updated_at)
                .build()
        }
    }
}
