package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.UserService
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import com.example.sync.GitHubOrgClient
import viaduct.api.resolver.Resolver
import viaduct.api.grts.IdentityImportSummary

@Resolver
class ImportProviderIdentitiesResolver(
    private val githubOrgClient: GitHubOrgClient?,
    private val userService: UserService,
    private val viaAccessService: ViaAccessService,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.ImportProviderIdentities() {
    override suspend fun resolve(ctx: Context): IdentityImportSummary {
        ctx.requireTenantPermission(authService, "github", TenantPermission.EDITOR)

        val client = githubOrgClient ?: return IdentityImportSummary.Builder(ctx)
            .imported(0).alreadyMapped(0).unmatched(0).unmatchedUsernames(emptyList()).build()

        val orgMembers = githubOrgClient.listOrgMembers()
        val viaAccessUsers = userService.getAllUsers(ctx.authenticatedClient)
        val usersByEmail = viaAccessUsers.associateBy { it.email.lowercase() }

        val existingIdentities = viaAccessService.getExternalIdentitiesByProvider(ctx.authenticatedClient, "github")
            .associateBy { it.external_user_id }

        var imported = 0
        var alreadyMapped = 0
        val unmatchedUsernames = mutableListOf<String>()

        for (member in orgMembers) {
            val alreadyExists = existingIdentities[member.id.toString()]
            if (alreadyExists != null) {
                alreadyMapped++
                continue
            }
            val matchedUser = member.email?.let { usersByEmail[it.lowercase()] }
            if (matchedUser != null) {
                viaAccessService.upsertExternalIdentity(
                    ctx.authenticatedClient,
                    userId = matchedUser.id,
                    provider = "github",
                    externalUserId = member.id.toString(),
                    externalUsername = member.login,
                    verified = true,
                )
                imported++
            } else {
                unmatchedUsernames += member.login
            }
        }

        return IdentityImportSummary.Builder(ctx)
            .imported(imported)
            .alreadyMapped(alreadyMapped)
            .unmatched(unmatchedUsernames.size)
            .unmatchedUsernames(unmatchedUsernames)
            .build()
    }
}
