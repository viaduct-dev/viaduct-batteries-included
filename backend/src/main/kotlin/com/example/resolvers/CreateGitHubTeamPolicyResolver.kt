package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.GitHubTeamPolicy

@Resolver
class CreateGitHubTeamPolicyResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.CreateGitHubTeamPolicy() {
    override suspend fun resolve(ctx: Context): GitHubTeamPolicy {
        ctx.requireTenantPermission(authService, "github", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        val entity = viaAccessService.createGitHubTeamPolicy(
            ctx.authenticatedClient,
            input.assetId.internalID,
            input.groupId.internalID,
            input.permission.name
        )
        return entity.toGrt(ctx)
    }
}
