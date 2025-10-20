package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.QueryResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.CheckboxGroup
import java.util.Base64

/**
 * Resolver for the checkboxGroup query.
 * Returns a specific checkbox group by ID if the user is a member.
 */
@Resolver
class CheckboxGroupQueryResolver(
    private val groupService: GroupService
) : QueryResolvers.CheckboxGroup() {
    override suspend fun resolve(ctx: Context): CheckboxGroup? {
        // Decode the GlobalID to get the internal UUID
        val globalIdString = ctx.arguments.id
        val decoded = String(Base64.getDecoder().decode(globalIdString))
        val groupId = decoded.substringAfter(":")

        val client = groupService.supabaseService.getAuthenticatedClient(ctx.requestContext)
        val groupEntity = groupService.getGroupById(client, groupId) ?: return null

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
