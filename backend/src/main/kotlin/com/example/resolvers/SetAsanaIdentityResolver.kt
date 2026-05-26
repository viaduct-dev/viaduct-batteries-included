package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.ExternalIdentity

@Resolver
class SetAsanaIdentityResolver(
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.SetAsanaIdentity() {
    override suspend fun resolve(ctx: Context): ExternalIdentity {
        ctx.requireTenantPermission(authService, "asana", TenantPermission.EDITOR)

        val input = ctx.arguments.input
        val entity = viaAccessService.upsertExternalIdentity(
            ctx.authenticatedClient,
            userId = input.userId,
            provider = "asana",
            externalUserId = input.externalUserId,
            externalUsername = input.externalUsername,
            verified = true
        )
        return entity.toGrt(ctx)
    }
}
