package com.graphqlcheckmate.plugins

import com.fasterxml.jackson.databind.ObjectMapper
import com.graphqlcheckmate.config.RequestContext
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import org.koin.ktor.plugin.scope

/**
 * Ktor plugin for GraphQL authentication
 *
 * This plugin intercepts requests and:
 * 1. Extracts authentication from the request scope (via Koin)
 * 2. Stores the RequestContext in request attributes for route handlers
 * 3. Returns 401 Unauthorized if authentication fails
 */
val GraphQLAuthentication = createApplicationPlugin(
    name = "GraphQLAuthentication",
    createConfiguration = ::GraphQLAuthenticationConfiguration
) {
    val objectMapper = pluginConfig.objectMapper

    onCall { call ->
        // Only apply authentication to GraphQL endpoints
        if (!call.request.local.uri.startsWith("/graphql")) {
            return@onCall
        }

        try {
            // Get RequestContext from Koin's request scope
            // The requestScope factory automatically:
            // 1. Extracts the token from ApplicationCall headers
            // 2. Verifies the token with AuthService
            // 3. Creates GraphQLRequestContext, AuthenticatedSupabaseClient, and RequestContext
            val requestContext = call.scope.get<RequestContext>()

            // Store in call attributes for route handlers to access
            call.attributes.put(RequestContextKey, requestContext)

        } catch (e: Exception) {
            // Authentication failed - return 401 with appropriate error message
            val rootCause = generateSequence(e as Throwable) { it.cause }.last()
            val errorMessage = when {
                rootCause is IllegalArgumentException && rootCause.message?.contains("Authorization header required") == true ->
                    rootCause.message
                else ->
                    "Invalid or expired token: ${rootCause.message ?: e.message}"
            }

            call.respond(
                HttpStatusCode.Unauthorized,
                objectMapper.writeValueAsString(mapOf("error" to errorMessage))
            )
        }
    }
}

/**
 * Configuration for GraphQL authentication plugin
 */
class GraphQLAuthenticationConfiguration {
    var objectMapper: ObjectMapper = ObjectMapper()
}

/**
 * Attribute key for storing RequestContext in the call
 */
val RequestContextKey = AttributeKey<RequestContext>("RequestContext")

/**
 * Extension to get RequestContext from the call attributes
 */
val ApplicationCall.requestContext: RequestContext
    get() = attributes[RequestContextKey]
