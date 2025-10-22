package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.config.RequestContext
import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.CheckboxGroup

/**
 * Resolver for the createCheckboxGroup mutation.
 * Creates a new checkbox group with the authenticated user as the owner.
 * The owner is automatically added as a member via database trigger.
 */
@Resolver
class CreateCheckboxGroupResolver(
    private val groupService: GroupService
) : MutationResolvers.CreateCheckboxGroup() {
    override suspend fun resolve(ctx: Context): CheckboxGroup {
        val input = ctx.arguments.input
        val requestContext = ctx.requestContext as RequestContext
        val userId = requestContext.graphQLContext.userId

        val groupEntity = groupService.createGroup(
            requestContext = requestContext,
            name = input.name,
            description = input.description,
            ownerId = userId
        )

        return CheckboxGroup.Builder(ctx)
            .id(ctx.globalIDFor(CheckboxGroup.Reflection, groupEntity.id))
            .name(groupEntity.name)
            .description(groupEntity.description)
            .ownerId(groupEntity.owner_id)
            .createdAt(groupEntity.created_at)
            .updatedAt(groupEntity.updated_at)
            .build()
    }
}
