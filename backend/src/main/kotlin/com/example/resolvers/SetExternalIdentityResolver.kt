package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.ExternalIdentity

@Resolver
class SetExternalIdentityResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.SetExternalIdentity() {
    override suspend fun resolve(ctx: Context): ExternalIdentity {
        ctx.requireTenantPermission(authService, "github", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        val entity = viaAccessService.upsertExternalIdentity(
            ctx.authenticatedClient,
            personId = input.personId,
            provider = "github",
            externalUserId = input.externalUserId,
            externalUsername = input.externalUsername,
            verified = true
        )
        return entity.toGrt(ctx)
    }
}
