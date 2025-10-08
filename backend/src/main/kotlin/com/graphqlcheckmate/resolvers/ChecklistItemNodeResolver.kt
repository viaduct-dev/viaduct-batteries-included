package com.graphqlcheckmate.resolvers

import viaduct.api.grts.ChecklistItem
import com.graphqlcheckmate.AuthenticatedSupabaseClient
import com.graphqlcheckmate.SupabaseService
import com.graphqlcheckmate.resolvers.NodeResolvers
import viaduct.api.Resolver

@Resolver
class ChecklistItemNodeResolver(
    private val supabaseService: SupabaseService
) : NodeResolvers.ChecklistItem() {
    override suspend fun resolve(ctx: Context): ChecklistItem {
        // Get authenticated client from request context
        val authenticatedClient = supabaseService.getAuthenticatedClient(ctx.requestContext)

        val globalId = ctx.id
        val internalId = globalId.internalID
        val entity = authenticatedClient.getChecklistItemById(internalId)
            ?: throw IllegalArgumentException("ChecklistItem not found: $internalId")

        return ChecklistItem.Builder(ctx)
            .id(globalId)
            .title(entity.title)
            .completed(entity.completed)
            .userId(entity.user_id)
            .createdAt(entity.created_at)
            .updatedAt(entity.updated_at)
            .build()
    }
}
