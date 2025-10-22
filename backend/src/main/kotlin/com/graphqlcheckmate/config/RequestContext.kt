package com.graphqlcheckmate.config

import com.graphqlcheckmate.AuthenticatedSupabaseClient
import com.graphqlcheckmate.GraphQLRequestContext

/**
 * Request-scoped context containing authentication and client information.
 *
 * This data class provides type-safe access to request-specific data
 * instead of forcing callers to use scope.get<T>() everywhere.
 *
 * Each GraphQL request gets its own RequestContext instance that is
 * automatically created by Koin and cleaned up by Ktor's request lifecycle.
 */
data class RequestContext(
    /**
     * The GraphQL request context containing user authentication info.
     */
    val graphQLContext: GraphQLRequestContext,

    /**
     * The authenticated Supabase client configured for the current user.
     */
    val authenticatedClient: AuthenticatedSupabaseClient
)
