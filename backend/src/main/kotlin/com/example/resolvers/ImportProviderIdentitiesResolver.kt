package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import com.example.sync.GitHubOrgClient
import viaduct.api.resolver.Resolver
import viaduct.api.grts.IdentityImportSummary

@Resolver
class ImportProviderIdentitiesResolver(
    private val githubOrgClient: GitHubOrgClient?,
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.ImportProviderIdentities() {
    override suspend fun resolve(ctx: Context): IdentityImportSummary {
        ctx.requireTenantPermission(authService, "github", TenantPermission.EDITOR)

        val client = githubOrgClient ?: return IdentityImportSummary.Builder(ctx)
            .imported(0).alreadyMapped(0).unmatched(0).unmatchedUsernames(emptyList()).build()

        val orgMembers = client.listOrgMembers()
        val existingByExternalId = viaAccessService.getExternalIdentitiesByProvider(ctx.authenticatedClient, "github")
            .associateBy { it.external_user_id }

        var imported = 0
        var alreadyMapped = 0

        for (member in orgMembers) {
            if (existingByExternalId.containsKey(member.id.toString())) {
                alreadyMapped++
                continue
            }
            // Every org member becomes a person, regardless of whether they have a ViaAccess login.
            // If they later sign in via OAuth the trigger links their auth account to this person.
            val person = viaAccessService.getOrCreatePerson(
                ctx.authenticatedClient,
                displayName = member.login,
                email = member.email,
            )
            viaAccessService.upsertExternalIdentity(
                ctx.authenticatedClient,
                personId = person.id,
                provider = "github",
                externalUserId = member.id.toString(),
                externalUsername = member.login,
                verified = true,
            )
            imported++
        }

        return IdentityImportSummary.Builder(ctx)
            .imported(imported)
            .alreadyMapped(alreadyMapped)
            .unmatched(0)
            .unmatchedUsernames(emptyList())
            .build()
    }
}
