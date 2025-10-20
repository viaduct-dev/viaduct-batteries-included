package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.GroupMember
import java.util.Base64

/**
 * Resolver for the addGroupMember mutation.
 * Adds a user to a checkbox group.
 * Only the group owner can add members (enforced by RLS).
 */
@Resolver
class AddGroupMemberResolver(
    private val groupService: GroupService
) : MutationResolvers.AddGroupMember() {
    override suspend fun resolve(ctx: Context): GroupMember {
        val input = ctx.arguments.input
        // Decode the GlobalID to get the internal UUID
        val decoded = String(Base64.getDecoder().decode(input.groupId))
        val groupId = decoded.substringAfter(":")

        val client = groupService.supabaseService.getAuthenticatedClient(ctx.requestContext)
        val memberEntity = groupService.addGroupMember(
            client = client,
            groupId = groupId,
            userId = input.userId
        )

        return GroupMember.Builder(ctx)
            .id(memberEntity.id)
            .groupId(memberEntity.group_id)
            .userId(memberEntity.user_id)
            .joinedAt(memberEntity.joined_at)
            .build()
    }
}
