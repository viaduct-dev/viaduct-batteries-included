package com.viaduct.resolvers

import com.viaduct.resolvers.resolverbases.BlogPostResolvers
import com.viaduct.services.UserService
import viaduct.api.Resolver
import viaduct.api.grts.User

/**
 * Field resolver for BlogPost.author.
 * Returns the user who created the blog post.
 */
@Resolver(objectValueFragment = "fragment _ on BlogPost { userId }")
class BlogPostAuthorResolver(
    private val userService: UserService
) : BlogPostResolvers.Author() {
    override suspend fun resolve(ctx: Context): User {
        // Access parent BlogPost via objectValue
        val userId = ctx.objectValue.getUserId()

        // Get the user entity
        val userEntity = userService.getUserById(ctx.authenticatedClient, userId)
            ?: throw IllegalStateException("User not found: $userId")

        // Map entity to GraphQL User object
        return User.Builder(ctx)
            .id(userId)
            .email(userEntity.email)
            .isAdmin(userEntity.raw_app_meta_data?.get("is_admin")?.toString()?.toBoolean() ?: false)
            .createdAt(userEntity.created_at)
            .build()
    }
}
