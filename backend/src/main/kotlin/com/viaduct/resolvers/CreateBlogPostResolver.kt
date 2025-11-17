package com.viaduct.resolvers

import com.viaduct.resolvers.resolverbases.MutationResolvers
import com.viaduct.services.BlogPostService
import viaduct.api.Resolver
import viaduct.api.grts.BlogPost

/**
 * Resolver for the createBlogPost mutation.
 * Creates a new blog post in a group.
 */
@Resolver
class CreateBlogPostResolver(
    private val blogPostService: BlogPostService
) : MutationResolvers.CreateBlogPost() {
    override suspend fun resolve(ctx: Context): BlogPost {
        val input = ctx.arguments.input

        // Extract internal group ID from GlobalID using .internalID
        val groupId = input.groupId.internalID

        // Get the current user ID from the request context
        val userId = ctx.userId

        // Create the blog post using the service
        val entity = blogPostService.createBlogPost(
            authenticatedClient = ctx.authenticatedClient,
            groupId = groupId,
            userId = userId,
            title = input.title,
            slug = input.slug,
            content = input.content,
            published = input.published ?: false
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
