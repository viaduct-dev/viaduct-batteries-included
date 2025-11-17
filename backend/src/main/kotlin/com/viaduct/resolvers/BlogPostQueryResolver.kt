package com.viaduct.resolvers

import com.viaduct.resolvers.resolverbases.QueryResolvers
import com.viaduct.services.BlogPostService
import viaduct.api.Resolver
import viaduct.api.grts.BlogPost

/**
 * Resolver for the blogPost query.
 * Returns a specific blog post by ID.
 */
@Resolver
class BlogPostQueryResolver(
    private val blogPostService: BlogPostService
) : QueryResolvers.BlogPost() {
    override suspend fun resolve(ctx: Context): BlogPost? {
        // Extract the internal post ID from the GlobalID using .internalID
        val postId = ctx.arguments.id.internalID

        // Get the blog post using the service
        val entity = blogPostService.getBlogPostById(ctx.authenticatedClient, postId)
            ?: return null

        // Map entity to GraphQL BlogPost object
        return BlogPost.Builder(ctx)
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
