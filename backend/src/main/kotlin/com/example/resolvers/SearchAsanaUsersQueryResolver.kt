package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.sync.AsanaWorkspaceClient
import viaduct.api.resolver.Resolver
import viaduct.api.grts.AsanaProviderUser

@Resolver
class SearchAsanaUsersQueryResolver(
    private val asanaClient: AsanaWorkspaceClient?
) : QueryResolvers.SearchAsanaUsers() {
    override suspend fun resolve(ctx: Context): List<AsanaProviderUser> {
        val client = asanaClient ?: return emptyList()
        return client.searchUsers(ctx.arguments.query).map { user ->
            AsanaProviderUser.Builder(ctx)
                .externalUserId(user.gid)
                .externalUsername(user.name)
                .email(user.email)
                .build()
        }
    }
}
