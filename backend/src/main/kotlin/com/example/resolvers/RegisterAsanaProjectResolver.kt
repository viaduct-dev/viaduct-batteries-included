package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaProjectAsset

@Resolver
class RegisterAsanaProjectResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.RegisterAsanaProject() {
    override suspend fun resolve(ctx: Context): AsanaProjectAsset {
        ctx.requireTenantPermission(authService, "asana", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        val (asset, project) = viaAccessService.createAsanaProjectAsset(
            ctx.authenticatedClient, input.gid, input.name
        )
        return project.toGrt(ctx, asset)
    }
}
