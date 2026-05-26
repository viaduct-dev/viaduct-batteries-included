package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaPortfolioAsset

@Resolver
class RegisterAsanaPortfolioResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.RegisterAsanaPortfolio() {
    override suspend fun resolve(ctx: Context): AsanaPortfolioAsset {
        ctx.requireTenantPermission(authService, "asana", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        val (asset, portfolio) = viaAccessService.createAsanaPortfolioAsset(
            ctx.authenticatedClient, input.gid, input.name
        )
        return portfolio.toGrt(ctx, asset)
    }
}
