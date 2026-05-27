package com.example.resolvers

import com.example.AuthenticatedSupabaseClient
import com.example.services.ViaAccessService
import viaduct.api.context.ExecutionContext
import viaduct.api.grts.ProviderUserView

internal suspend fun buildProviderUserViews(
    ctx: ExecutionContext,
    client: AuthenticatedSupabaseClient,
    viaAccessService: ViaAccessService,
    groupIds: List<String>,
    provider: String,
): List<ProviderUserView> {
    val identities = viaAccessService.getProviderUsersForGroups(client, groupIds, provider)
    val personIds = identities.map { it.person_id }.distinct()
    val personsById = client.getPersonsByIds(personIds).associateBy { it.id }

    return identities.map { identity ->
        val person = personsById[identity.person_id]
        ProviderUserView.Builder(ctx)
            .provider(identity.provider)
            .externalUserId(identity.external_user_id)
            .externalUsername(identity.external_username)
            .linkedUserId(identity.person_id)
            .linkedUserEmail(person?.email)
            .verified(identity.verified_at != null)
            .build()
    }
}
