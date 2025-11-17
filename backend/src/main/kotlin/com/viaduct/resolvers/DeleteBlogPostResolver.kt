package com.viaduct.resolvers

import com.viaduct.resolvers.resolverbases.MutationResolvers
import com.viaduct.services.BlogPostService
import viaduct.api.Resolver

/**
 * Resolver for the deleteBlogPost mutation.
 * Deletes a blog post.
 */
@Resolver
class DeleteBlogPostResolver(
    private val blogPostService: BlogPostService
) : MutationResolvers.DeleteBlogPost() {
    override suspend fun resolve(ctx: Context): Boolean {
        val input = ctx.arguments.input

        // Extract internal post ID from GlobalID using .internalID
        val postId = input.id.internalID

        // Delete the blog post using the service
        return blogPostService.deleteBlogPost(ctx.authenticatedClient, postId)
    }
}
