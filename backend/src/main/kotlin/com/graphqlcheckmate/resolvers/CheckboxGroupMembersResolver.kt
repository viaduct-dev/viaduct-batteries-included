package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.CheckboxGroupResolvers
import com.graphqlcheckmate.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.GroupMember

/**
 * Field resolver for CheckboxGroup.members.
 * Returns all members of the checkbox group.
 */
@Resolver(objectValueFragment = "fragment _ on CheckboxGroup { id }")
class CheckboxGroupMembersResolver(
    private val groupService: GroupService
) : CheckboxGroupResolvers.Members() {
    override suspend fun resolve(ctx: Context): List<GroupMember> {
        // Access parent CheckboxGroup via objectValue
        val groupId = ctx.objectValue.getId().internalID

        val memberEntities = groupService.getGroupMembers(ctx.authenticatedClient, groupId)

        return memberEntities.map { entity ->
            GroupMember.Builder(ctx)
                .id(entity.id)
                .groupId(entity.group_id)
                .userId(entity.user_id)
                .joinedAt(entity.joined_at)
                .build()
        }
    }
}
