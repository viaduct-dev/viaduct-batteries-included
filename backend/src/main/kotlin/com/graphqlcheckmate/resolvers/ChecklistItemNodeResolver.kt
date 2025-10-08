package com.graphqlcheckmate.resolvers

import viaduct.api.grts.ChecklistItem
import com.graphqlcheckmate.resolvers.NodeResolvers
import com.graphqlcheckmate.services.ChecklistItemService
import viaduct.api.Resolver

@Resolver
class ChecklistItemNodeResolver(
    private val checklistItemService: ChecklistItemService
) : NodeResolvers.ChecklistItem() {
    override suspend fun resolve(ctx: Context): ChecklistItem {
        val globalId = ctx.id
        val internalId = globalId.internalID
        val entity = checklistItemService.getChecklistItemById(ctx.requestContext, internalId)
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
