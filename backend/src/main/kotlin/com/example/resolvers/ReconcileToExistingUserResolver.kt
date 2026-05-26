package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.UserService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.ProviderUserView

@Resolver
class ReconcileToExistingUserResolver(
    private val viaAccessService: ViaAccessService,
    private val userService: UserService
) : MutationResolvers.ReconcileToExistingUser() {
    override suspend fun resolve(ctx: Context): ProviderUserView {
        val input = ctx.arguments.input
        val identity = viaAccessService.upsertExternalIdentity(
            ctx.authenticatedClient,
            userId = input.userId,
            provider = input.provider,
            externalUserId = input.externalUserId,
            externalUsername = input.externalUsername
        )
        val user = userService.getUserById(ctx.authenticatedClient, input.userId)
        return ProviderUserView.Builder(ctx)
            .provider(identity.provider)
            .externalUserId(identity.external_user_id)
            .externalUsername(identity.external_username)
            .linkedUserId(identity.user_id)
            .linkedUserEmail(user?.email)
            .verified(identity.verified_at != null)
            .build()
    }
}
