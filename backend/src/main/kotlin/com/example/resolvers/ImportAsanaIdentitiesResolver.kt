package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import com.example.sync.AsanaWorkspaceClient
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaIdentityImportSummary

@Resolver
class ImportAsanaIdentitiesResolver(
    private val asanaClient: AsanaWorkspaceClient?,
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.ImportAsanaIdentities() {
    override suspend fun resolve(ctx: Context): AsanaIdentityImportSummary {
        ctx.requireTenantPermission(authService, "asana", TenantPermission.EDITOR)

        val client = asanaClient ?: return AsanaIdentityImportSummary.Builder(ctx)
            .imported(0).alreadyMapped(0).unmatched(0).unmatchedUsernames(emptyList()).build()

        val workspaceUsers = client.listWorkspaceUsers()
        val existingByExternalId = viaAccessService.getExternalIdentitiesByProvider(ctx.authenticatedClient, "asana")
            .associateBy { it.external_user_id }

        var imported = 0
        var alreadyMapped = 0

        for (user in workspaceUsers) {
            if (existingByExternalId.containsKey(user.gid)) {
                alreadyMapped++
                continue
            }
            val person = viaAccessService.getOrCreatePerson(
                ctx.authenticatedClient,
                displayName = user.name,
                email = user.email,
            )
            viaAccessService.upsertExternalIdentity(
                ctx.authenticatedClient,
                personId = person.id,
                provider = "asana",
                externalUserId = user.gid,
                externalUsername = user.name,
                verified = true,
            )
            imported++
        }

        return AsanaIdentityImportSummary.Builder(ctx)
            .imported(imported)
            .alreadyMapped(alreadyMapped)
            .unmatched(0)
            .unmatchedUsernames(emptyList())
            .build()
    }
}
