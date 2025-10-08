package com.graphqlcheckmate.resolvers

import viaduct.api.grts.ChecklistItem
import com.graphqlcheckmate.resolvers.resolverbases.QueryResolvers
import com.graphqlcheckmate.services.ChecklistItemService
import viaduct.api.Resolver

@Resolver
class ChecklistItemsQueryResolver(
    private val checklistItemService: ChecklistItemService
) : QueryResolvers.ChecklistItems() {
    override suspend fun resolve(ctx: Context): List<ChecklistItem> {
        val entities = checklistItemService.getChecklistItems(ctx.requestContext)

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
