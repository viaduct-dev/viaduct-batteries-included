package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.sync.GitHubOrgClient
import viaduct.api.resolver.Resolver
import viaduct.api.grts.ProviderUser

@Resolver
class SearchProviderUsersQueryResolver(
    private val githubOrgClient: GitHubOrgClient?
) : QueryResolvers.SearchProviderUsers() {
    override suspend fun resolve(ctx: Context): List<ProviderUser> {
        val client = githubOrgClient ?: return emptyList()
        return client.searchMembers(ctx.arguments.query).map { user ->
            ProviderUser.Builder(ctx)
                .externalUserId(user.id.toString())
                .externalUsername(user.login)
                .email(user.email)
                .build()
        }
    }
}
