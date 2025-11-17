package com.viaduct.services

import com.viaduct.AuthenticatedSupabaseClient
import com.viaduct.BlogPostEntity
import com.viaduct.SupabaseService

/**
 * Service for managing blog posts.
 * Handles CRUD operations for blog posts within groups.
 */
open class BlogPostService(
    internal val supabaseService: SupabaseService
) {
    /**
     * Get all blog posts for a specific group.
     */
    suspend fun getBlogPostsByGroup(
        authenticatedClient: AuthenticatedSupabaseClient,
        groupId: String
    ): List<BlogPostEntity> {
        return authenticatedClient.getBlogPostsByGroup(groupId)
    }

    /**
     * Get a specific blog post by ID.
     */
    suspend fun getBlogPostById(
        authenticatedClient: AuthenticatedSupabaseClient,
        id: String
    ): BlogPostEntity? {
        return authenticatedClient.getBlogPostById(id)
    }

    /**
     * Get a blog post by its slug within a specific group.
     */
    suspend fun getBlogPostBySlug(
        authenticatedClient: AuthenticatedSupabaseClient,
        groupId: String,
        slug: String
    ): BlogPostEntity? {
        return authenticatedClient.getBlogPostBySlug(groupId, slug)
    }

    /**
     * Get all blog posts created by a specific user.
     */
    suspend fun getMyBlogPosts(
        authenticatedClient: AuthenticatedSupabaseClient,
        userId: String
    ): List<BlogPostEntity> {
        return authenticatedClient.getMyBlogPosts(userId)
    }

    /**
     * Create a new blog post.
     */
    suspend fun createBlogPost(
        authenticatedClient: AuthenticatedSupabaseClient,
        groupId: String,
        userId: String,
        title: String,
        slug: String,
        content: String,
        published: Boolean = false
    ): BlogPostEntity {
        return authenticatedClient.createBlogPost(
            groupId = groupId,
            userId = userId,
            title = title,
            slug = slug,
            content = content,
            published = published
        )
    }

    /**
     * Update an existing blog post.
     */
    suspend fun updateBlogPost(
        authenticatedClient: AuthenticatedSupabaseClient,
        id: String,
        title: String? = null,
        slug: String? = null,
        content: String? = null,
        published: Boolean? = null
    ): BlogPostEntity {
        return authenticatedClient.updateBlogPost(
            id = id,
            title = title,
            slug = slug,
            content = content,
            published = published
        )
    }

    /**
     * Delete a blog post.
     */
    suspend fun deleteBlogPost(
        authenticatedClient: AuthenticatedSupabaseClient,
        id: String
    ): Boolean {
        return authenticatedClient.deleteBlogPost(id)
    }
}
