package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.ExternalIdentityEntity
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.ExternalIdentity

@Resolver
class MyExternalIdentitiesResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.MyExternalIdentities() {
    override suspend fun resolve(ctx: Context): List<ExternalIdentity> {
        // Find the person linked to the current auth user, then return their identities
        val persons = ctx.authenticatedClient.getPersonsByAuthUserId(listOf(ctx.userId))
        val person = persons.firstOrNull() ?: return emptyList()
        return viaAccessService.getExternalIdentitiesForPerson(ctx.authenticatedClient, person.id)
            .map { it.toGrt(ctx) }
    }
}

internal fun ExternalIdentityEntity.toGrt(ctx: viaduct.api.context.ExecutionContext): ExternalIdentity =
    ExternalIdentity.Builder(ctx)
        .id(ctx.globalIDFor(ExternalIdentity.Reflection, id))
        .personId(person_id)
        .provider(provider)
        .externalUserId(external_user_id)
        .externalUsername(external_username)
        .verifiedAt(verified_at)
        .build()
