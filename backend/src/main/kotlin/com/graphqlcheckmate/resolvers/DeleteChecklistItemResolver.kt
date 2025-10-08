package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.ChecklistItemService
import viaduct.api.Resolver

@Resolver
class DeleteChecklistItemResolver(
    private val checklistItemService: ChecklistItemService
) : MutationResolvers.DeleteChecklistItem() {
    override suspend fun resolve(ctx: Context): Boolean {
        val input = ctx.arguments.input
        val internalId = input.id.internalID
        return checklistItemService.deleteChecklistItem(ctx.requestContext, internalId)
    }
}
