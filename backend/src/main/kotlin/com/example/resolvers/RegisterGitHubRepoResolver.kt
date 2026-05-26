package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.GitHubRepoAsset

@Resolver
class RegisterGitHubRepoResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.RegisterGitHubRepo() {
    override suspend fun resolve(ctx: Context): GitHubRepoAsset {
        ctx.requireTenantPermission(authService, "github", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        val (asset, repo) = viaAccessService.createGitHubRepoAsset(
            ctx.authenticatedClient, input.owner, input.repo, input.name
        )
        return repo.toGrt(ctx, asset)
    }
}
