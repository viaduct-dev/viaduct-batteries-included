package com.viaduct.plugins

import com.fasterxml.jackson.databind.ObjectMapper
import com.viaduct.config.RequestContext
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import org.koin.ktor.plugin.scope

/**
 * Public operations that don't require authentication.
 * These include auth mutations and config queries.
 */
private val PUBLIC_OPERATIONS = setOf(
    "signIn", "signUp", "refreshToken", "supabaseConfig"
)

/**
 * Check if a GraphQL request is for a public operation.
 * Parses the request body to extract mutation/query names.
 */
private fun isPublicOperation(requestBody: String, objectMapper: ObjectMapper): Boolean {
    try {
        val json = objectMapper.readTree(requestBody)
        val query = json.get("query")?.asText() ?: return false

        // Check if the query contains any public operation names as the primary operation
        return PUBLIC_OPERATIONS.any { op ->
            // Match mutation { signIn or query { supabaseConfig patterns
            query.contains(Regex("""(mutation|query)\s*[^{]*\{\s*$op\b"""))
        }
    } catch (e: Exception) {
        return false
    }
}

/**
 * Ktor plugin for GraphQL authentication
 *
 * This plugin intercepts requests and:
 * 1. Checks if the request is for a public operation (signIn, signUp, etc.)
 * 2. If public, marks the request as unauthenticated and allows it through
 * 3. Otherwise, extracts authentication from the request scope (via Koin)
 * 4. Stores the RequestContext in request attributes for route handlers
 * 5. Returns 401 Unauthorized if authentication fails
 */
val GraphQLAuthentication = createApplicationPlugin(
    name = "GraphQLAuthentication",
    createConfiguration = ::GraphQLAuthenticationConfiguration
) {
    val objectMapper = pluginConfig.objectMapper

    onCall { call ->
        // Only apply authentication to GraphQL endpoints, but skip OPTIONS (CORS preflight)
        if (!call.request.local.uri.startsWith("/graphql") || call.request.local.method == HttpMethod.Options) {
            return@onCall
        }

        // Read the request body to check if it's a public operation
        // Note: We need to save it for later since receiveText() consumes the body
        val requestBody = call.receiveText()
        call.attributes.put(RequestBodyKey, requestBody)

        // Check if this is a public operation (no auth required)
        if (isPublicOperation(requestBody, objectMapper)) {
            call.attributes.put(IsPublicOperationKey, true)
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

        } catch (e: IllegalArgumentException) {
            // Expected authentication failures (missing token, invalid format, etc.)
            // These are client errors that should return 401 Unauthorized
            call.respond(
                HttpStatusCode.Unauthorized,
                objectMapper.writeValueAsString(mapOf("error" to (e.message ?: "Authentication failed")))
            )
        } catch (e: Exception) {
            // Authentication-related errors (invalid token, token verification failures)
            // Koin may wrap exceptions, so check the root cause for authentication-specific messages
            val rootCause = generateSequence(e as Throwable) { it.cause }.last()
            val rootMessage = rootCause.message ?: e.message

            // Log for monitoring
            call.application.log.warn("Authentication failed: $rootMessage", e)

            // Return user-friendly error message based on the root cause
            val errorMessage = when {
                rootMessage?.contains("Authorization header required", ignoreCase = true) == true ->
                    "Authorization header required"
                rootMessage?.contains("token", ignoreCase = true) == true ->
                    "Invalid or expired token"
                rootMessage?.contains("auth", ignoreCase = true) == true ->
                    "Authentication failed"
                else ->
                    "Invalid or expired token"
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
 * Attribute key for storing the request body (consumed by receiveText)
 */
val RequestBodyKey = AttributeKey<String>("RequestBody")

/**
 * Attribute key to mark a request as a public operation
 */
val IsPublicOperationKey = AttributeKey<Boolean>("IsPublicOperation")

/**
 * Extension to get RequestContext from the call attributes
 */
val ApplicationCall.requestContext: RequestContext
    get() = attributes[RequestContextKey]

/**
 * Extension to get the cached request body
 */
val ApplicationCall.cachedRequestBody: String?
    get() = attributes.getOrNull(RequestBodyKey)

/**
 * Extension to check if this is a public operation
 */
val ApplicationCall.isPublicOperation: Boolean
    get() = attributes.getOrNull(IsPublicOperationKey) ?: false
