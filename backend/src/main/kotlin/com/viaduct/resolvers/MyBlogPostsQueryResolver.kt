package com.viaduct.resolvers

import com.viaduct.resolvers.resolverbases.QueryResolvers
import com.viaduct.services.BlogPostService
import viaduct.api.Resolver
import viaduct.api.grts.BlogPost

/**
 * Resolver for the myBlogPosts query.
 * Returns all blog posts created by the current user.
 */
@Resolver
class MyBlogPostsQueryResolver(
    private val blogPostService: BlogPostService
) : QueryResolvers.MyBlogPosts() {
    override suspend fun resolve(ctx: Context): List<BlogPost> {
        // Get the current user ID from the request context
        val userId = ctx.userId

        // Get blog posts using the service
        val postEntities = blogPostService.getMyBlogPosts(ctx.authenticatedClient, userId)

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
