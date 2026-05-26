package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.UserService
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import com.example.sync.AsanaWorkspaceClient
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaIdentityImportSummary

@Resolver
class ImportAsanaIdentitiesResolver(
    private val asanaClient: AsanaWorkspaceClient?,
    private val userService: UserService,
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.ImportAsanaIdentities() {
    override suspend fun resolve(ctx: Context): AsanaIdentityImportSummary {
        ctx.requireTenantPermission(authService, "asana", TenantPermission.EDITOR)

        val client = asanaClient ?: return AsanaIdentityImportSummary.Builder(ctx)
            .imported(0).alreadyMapped(0).unmatched(0).unmatchedUsernames(emptyList()).build()

        val workspaceUsers = client.listWorkspaceUsers()
        val viaAccessUsers = userService.getAllUsers(ctx.authenticatedClient)
        val usersByEmail = viaAccessUsers.associateBy { it.email.lowercase() }

        val existingIdentities = viaAccessService.getExternalIdentitiesByProvider(ctx.authenticatedClient, "asana")
            .associateBy { it.external_user_id }

        var imported = 0
        var alreadyMapped = 0
        val unmatchedUsernames = mutableListOf<String>()

        for (user in workspaceUsers) {
            val alreadyExists = existingIdentities[user.gid]
            if (alreadyExists != null) {
                alreadyMapped++
                continue
            }
            val matchedUser = user.email?.let { usersByEmail[it.lowercase()] }
            if (matchedUser != null) {
                viaAccessService.upsertExternalIdentity(
                    ctx.authenticatedClient,
                    userId = matchedUser.id,
                    provider = "asana",
                    externalUserId = user.gid,
                    externalUsername = user.name,
                    verified = true,
                )
                imported++
            } else {
                unmatchedUsernames += user.name
            }
        }

        return AsanaIdentityImportSummary.Builder(ctx)
            .imported(imported)
            .alreadyMapped(alreadyMapped)
            .unmatched(unmatchedUsernames.size)
            .unmatchedUsernames(unmatchedUsernames)
            .build()
    }
}
