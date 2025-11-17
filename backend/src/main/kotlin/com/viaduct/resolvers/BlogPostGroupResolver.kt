package com.viaduct.resolvers

import com.viaduct.resolvers.resolverbases.BlogPostResolvers
import com.viaduct.services.GroupService
import viaduct.api.Resolver
import viaduct.api.grts.Group

/**
 * Field resolver for BlogPost.group.
 * Returns the group (blog) that the post belongs to.
 */
@Resolver(objectValueFragment = "fragment _ on BlogPost { groupId }")
class BlogPostGroupResolver(
    private val groupService: GroupService
) : BlogPostResolvers.Group() {
    override suspend fun resolve(ctx: Context): Group {
        // Access parent BlogPost via objectValue
        val groupId = ctx.objectValue.getGroupId()

        // Get the group entity
        val groupEntity = groupService.getGroupById(ctx.authenticatedClient, groupId)
            ?: throw IllegalStateException("Group not found: $groupId")

        // Map entity to GraphQL Group object
        return Group.Builder(ctx)
            .id(ctx.globalIDFor(Group.Reflection, groupEntity.id))
            .name(groupEntity.name)
            .description(groupEntity.description)
            .ownerId(groupEntity.owner_id)
            .createdAt(groupEntity.created_at)
            .updatedAt(groupEntity.updated_at)
            .build()
    }
}
