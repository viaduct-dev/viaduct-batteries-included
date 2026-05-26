package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaProjectPolicy

@Resolver
class CreateAsanaProjectPolicyResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.CreateAsanaProjectPolicy() {
    override suspend fun resolve(ctx: Context): AsanaProjectPolicy {
        ctx.requireTenantPermission(authService, "asana", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        val entity = viaAccessService.createAsanaProjectPolicy(
            ctx.authenticatedClient,
            input.assetId.internalID,
            input.groupId.internalID,
            input.permission.name
        )
        return entity.toGrt(ctx)
    }
}
