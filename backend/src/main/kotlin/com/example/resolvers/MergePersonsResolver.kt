package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver

@Resolver
class MergePersonsResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.MergePersons() {
    override suspend fun resolve(ctx: Context): Boolean {
        ctx.requireTenantPermission(authService, "default", TenantPermission.EDITOR)
        val targetId = ctx.arguments.targetPersonId
        val sourceId = ctx.arguments.sourcePersonId
        require(targetId != sourceId) { "Cannot merge a person with themselves" }
        return viaAccessService.mergePersons(ctx.authenticatedClient, targetId, sourceId)
    }
}
