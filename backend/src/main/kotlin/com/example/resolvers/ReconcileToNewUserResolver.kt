package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.ProviderUserView

@Resolver
class ReconcileToNewUserResolver(
    private val viaAccessService: ViaAccessService
) : MutationResolvers.ReconcileToNewUser() {
    override suspend fun resolve(ctx: Context): ProviderUserView {
        val input = ctx.arguments.input
        val (personId, identity) = viaAccessService.inviteAndLinkUser(
            ctx.authenticatedClient,
            email = input.email,
            provider = input.provider,
            externalUserId = input.externalUserId,
            externalUsername = input.externalUsername
        )
        return ProviderUserView.Builder(ctx)
            .provider(identity.provider)
            .externalUserId(identity.external_user_id)
            .externalUsername(identity.external_username)
            .linkedUserId(personId)
            .linkedUserEmail(input.email)
            .verified(false)
            .build()
    }
}
