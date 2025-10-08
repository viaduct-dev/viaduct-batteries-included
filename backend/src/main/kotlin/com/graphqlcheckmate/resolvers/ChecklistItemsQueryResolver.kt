package com.graphqlcheckmate.resolvers

import viaduct.api.grts.ChecklistItem
import com.graphqlcheckmate.AuthenticatedSupabaseClient
import com.graphqlcheckmate.SupabaseService
import com.graphqlcheckmate.resolvers.resolverbases.QueryResolvers
import viaduct.api.Resolver

@Resolver
class ChecklistItemsQueryResolver(
    private val supabaseService: SupabaseService
) : QueryResolvers.ChecklistItems() {
    override suspend fun resolve(ctx: Context): List<ChecklistItem> {
        // Get authenticated client from request context
        val authenticatedClient = supabaseService.getAuthenticatedClient(ctx.requestContext)

        // Use the authenticated client - RLS will automatically filter by user
        val entities = authenticatedClient.getChecklistItems()

        return entities.map { entity ->
            ChecklistItem.Builder(ctx)
                .id(ctx.globalIDFor(ChecklistItem.Reflection, entity.id))
                .title(entity.title)
                .completed(entity.completed)
                .userId(entity.user_id)
                .createdAt(entity.created_at)
                .updatedAt(entity.updated_at)
                .build()
        }
    }
}
