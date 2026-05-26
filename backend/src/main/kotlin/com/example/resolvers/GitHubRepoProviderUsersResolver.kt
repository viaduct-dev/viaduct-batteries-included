package com.example.resolvers

import com.example.resolvers.resolverbases.GitHubRepoAssetResolvers
import com.example.services.UserService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.ProviderUserView

@Resolver(objectValueFragment = "fragment _ on GitHubRepoAsset { id }")
class GitHubRepoProviderUsersResolver(
    private val viaAccessService: ViaAccessService,
    private val userService: UserService
) : GitHubRepoAssetResolvers.ProviderUsers() {
    override suspend fun resolve(ctx: Context): List<ProviderUserView> {
        val assetId = ctx.getObjectValue().getId().internalID
        val policies = viaAccessService.getGitHubRepoPoliciesByAsset(ctx.authenticatedClient, assetId)
        val groupIds = policies.map { it.group_id }.distinct()
        val identities = viaAccessService.getProviderUsersForGroups(ctx.authenticatedClient, groupIds, "github")
        val users = userService.getAllUsers(ctx.authenticatedClient).associateBy { it.id }
        return identities.map { identity ->
            val linkedUser = users[identity.user_id]
            ProviderUserView.Builder(ctx)
                .provider(identity.provider)
                .externalUserId(identity.external_user_id)
                .externalUsername(identity.external_username)
                .linkedUserId(identity.user_id)
                .linkedUserEmail(linkedUser?.email)
                .verified(identity.verified_at != null)
                .build()
        }
    }
}
