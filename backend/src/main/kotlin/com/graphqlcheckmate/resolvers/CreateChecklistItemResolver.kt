package com.graphqlcheckmate.resolvers

import viaduct.api.grts.ChecklistItem
import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.ChecklistItemService
import viaduct.api.Resolver

@Resolver
class CreateChecklistItemResolver(
    private val checklistItemService: ChecklistItemService
) : MutationResolvers.CreateChecklistItem() {
    override suspend fun resolve(ctx: Context): ChecklistItem {
        val input = ctx.arguments.input
        val entity = checklistItemService.createChecklistItem(ctx.requestContext, input.title, input.userId)

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
