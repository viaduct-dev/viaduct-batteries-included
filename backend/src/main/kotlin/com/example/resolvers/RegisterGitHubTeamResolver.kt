package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.GitHubTeamAsset

@Resolver
class RegisterGitHubTeamResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.RegisterGitHubTeam() {
    override suspend fun resolve(ctx: Context): GitHubTeamAsset {
        ctx.requireTenantPermission(authService, "github", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        val (asset, team) = viaAccessService.createGitHubTeamAsset(
            ctx.authenticatedClient, input.org, input.slug, input.name
        )
        return team.toGrt(ctx, asset)
    }
}
