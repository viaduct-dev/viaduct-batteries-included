package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaPortfolioPolicy

@Resolver
class CreateAsanaPortfolioPolicyResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.CreateAsanaPortfolioPolicy() {
    override suspend fun resolve(ctx: Context): AsanaPortfolioPolicy {
        ctx.requireTenantPermission(authService, "asana", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        val entity = viaAccessService.createAsanaPortfolioPolicy(
            ctx.authenticatedClient,
            input.assetId.internalID,
            input.groupId.internalID,
            input.permission.name
        )
        return entity.toGrt(ctx)
    }
}
