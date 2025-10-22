package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.config.RequestContext
import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.UserService
import viaduct.api.Resolver

@Resolver
class SetUserAdminResolver(
    private val userService: UserService
) : MutationResolvers.SetUserAdmin() {
    override suspend fun resolve(ctx: Context): Boolean {
        val input = ctx.arguments.input
        val requestContext = ctx.requestContext as RequestContext
        userService.setUserAdmin(requestContext, input.userId, input.isAdmin)
        return true
    }
}
