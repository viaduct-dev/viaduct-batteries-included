package com.viaduct.resolvers

import com.viaduct.resolvers.resolverbases.QueryResolvers
import com.viaduct.services.BlogPostService
import viaduct.api.Resolver
import viaduct.api.grts.BlogPost

/**
 * Resolver for the blogPostBySlug query.
 * Returns a blog post by its slug within a specific group.
 */
@Resolver
class BlogPostBySlugQueryResolver(
    private val blogPostService: BlogPostService
) : QueryResolvers.BlogPostBySlug() {
    override suspend fun resolve(ctx: Context): BlogPost? {
        // Extract the internal group ID from the GlobalID using .internalID
        val groupId = ctx.arguments.groupId.internalID
        val slug = ctx.arguments.slug

        // Get the blog post using the service
        val entity = blogPostService.getBlogPostBySlug(ctx.authenticatedClient, groupId, slug)
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
