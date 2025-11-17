package com.viaduct.resolvers

import com.viaduct.resolvers.resolverbases.QueryResolvers
import com.viaduct.services.BlogPostService
import viaduct.api.Resolver
import viaduct.api.grts.BlogPost

/**
 * Resolver for the blogPostsByGroup query.
 * Returns all blog posts for a specific group that the user can access.
 */
@Resolver
class BlogPostsByGroupQueryResolver(
    private val blogPostService: BlogPostService
) : QueryResolvers.BlogPostsByGroup() {
    override suspend fun resolve(ctx: Context): List<BlogPost> {
        // Extract the internal group ID from the GlobalID using .internalID
        val groupId = ctx.arguments.groupId.internalID

        // Get blog posts using the service
        val postEntities = blogPostService.getBlogPostsByGroup(ctx.authenticatedClient, groupId)

        // Map entities to GraphQL BlogPost objects
        return postEntities.map { entity ->
            BlogPost.Builder(ctx)
                .id(ctx.globalIDFor(BlogPost.Reflection, entity.id))
                .groupId(entity.group_id)
                .userId(entity.user_id)
                .title(entity.title)
                .slug(entity.slug)
                .content(entity.content)
                .published(entity.published)
                .createdAt(entity.created_at)
                .updatedAt(entity.updated_at)
                .build()
        }
    }
}
