package com.viaduct.resolvers

import com.viaduct.resolvers.resolverbases.MutationResolvers
import com.viaduct.services.BlogPostService
import viaduct.api.Resolver
import viaduct.api.grts.BlogPost

/**
 * Resolver for the updateBlogPost mutation.
 * Updates an existing blog post.
 */
@Resolver
class UpdateBlogPostResolver(
    private val blogPostService: BlogPostService
) : MutationResolvers.UpdateBlogPost() {
    override suspend fun resolve(ctx: Context): BlogPost {
        val input = ctx.arguments.input

        // Extract internal post ID from GlobalID using .internalID
        val postId = input.id.internalID

        // Update the blog post using the service
        val entity = blogPostService.updateBlogPost(
            authenticatedClient = ctx.authenticatedClient,
            id = postId,
            title = input.title,
            slug = input.slug,
            content = input.content,
            published = input.published
        )

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
