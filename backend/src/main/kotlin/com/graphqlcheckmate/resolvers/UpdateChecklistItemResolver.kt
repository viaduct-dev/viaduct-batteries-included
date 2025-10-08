package com.graphqlcheckmate.resolvers

import viaduct.api.grts.ChecklistItem
import com.graphqlcheckmate.AuthenticatedSupabaseClient
import com.graphqlcheckmate.SupabaseService
import viaduct.api.Resolver
import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers

@Resolver
class UpdateChecklistItemResolver(
    private val supabaseService: SupabaseService
) : MutationResolvers.UpdateChecklistItem() {
    override suspend fun resolve(ctx: Context): ChecklistItem {
        // Get authenticated client from request context
        val authenticatedClient = supabaseService.getAuthenticatedClient(ctx.requestContext)

        val input = ctx.arguments.input
        val internalId = input.id.internalID
        val entity = authenticatedClient.updateChecklistItem(internalId, input.completed)

        return ChecklistItem.Builder(ctx)
            .id(ctx.globalIDFor(ChecklistItem.Reflection, entity.id))
            .title(entity.title)
            .completed(entity.completed)
            .userId(entity.user_id)
            .createdAt(entity.created_at)
            .updatedAt(entity.updated_at)
            .build()
    }
}
