package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.UserService
import viaduct.api.Resolver

@Resolver
class SetUserAdminResolver(
    private val userService: UserService
) : MutationResolvers.SetUserAdmin() {
    override suspend fun resolve(ctx: Context): Boolean {
        val input = ctx.arguments.input
        userService.setUserAdmin(ctx.authenticatedClient, input.userId, input.isAdmin)
        return true
    }
}
