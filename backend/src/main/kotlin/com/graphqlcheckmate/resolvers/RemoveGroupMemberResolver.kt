package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.config.RequestContext
import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import java.util.Base64

/**
 * Resolver for the removeGroupMember mutation.
 * Removes a user from a checkbox group.
 * The group owner or the member themselves can remove the membership (enforced by RLS).
 */
@Resolver
class RemoveGroupMemberResolver(
    private val groupService: GroupService
) : MutationResolvers.RemoveGroupMember() {
    override suspend fun resolve(ctx: Context): Boolean {
        val input = ctx.arguments.input
        // Decode the GlobalID to get the internal UUID
        val decoded = String(Base64.getDecoder().decode(input.groupId))
        val groupId = decoded.substringAfter(":")

        val requestContext = ctx.requestContext as RequestContext
        return groupService.removeGroupMember(
            requestContext = requestContext,
            groupId = groupId,
            userId = input.userId
        )
    }
}
