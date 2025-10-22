package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.UserService
import viaduct.api.Resolver

@Resolver
class DeleteUserResolver(
    private val userService: UserService
) : MutationResolvers.DeleteUser() {
    override suspend fun resolve(ctx: Context): Boolean {
        val input = ctx.arguments.input
        return userService.deleteUser(ctx.authenticatedClient, input.userId)
    }
}
