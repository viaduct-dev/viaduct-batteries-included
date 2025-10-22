package com.graphqlcheckmate.resolvers

import com.graphqlcheckmate.config.RequestContext
import com.graphqlcheckmate.resolvers.resolverbases.MutationResolvers
import com.graphqlcheckmate.services.UserService
import viaduct.api.Resolver

@Resolver
class DeleteUserResolver(
    private val userService: UserService
) : MutationResolvers.DeleteUser() {
    override suspend fun resolve(ctx: Context): Boolean {
        val input = ctx.arguments.input
        val requestContext = ctx.requestContext as RequestContext
        return userService.deleteUser(requestContext, input.userId)
    }
}
