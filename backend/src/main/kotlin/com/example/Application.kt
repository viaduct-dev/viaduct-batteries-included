package com.example

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.example.config.DelegatingTenantCodeInjector
import com.example.config.KoinTenantCodeInjector
import com.example.models.GraphQLRequest
import com.example.plugins.GraphQLAuthentication
import com.example.plugins.cachedRequestBody
import com.example.plugins.isPublicOperation
import com.example.plugins.isIntrospection
import com.example.plugins.requestContext
import com.example.services.AuthService
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.cors.routing.CORS
import org.koin.core.Koin
import org.slf4j.event.Level
import viaduct.service.api.ExecutionInput as ViaductExecutionInput
import viaduct.service.api.SchemaId
import viaduct.service.api.Viaduct
import java.util.Base64

private val logger = org.slf4j.LoggerFactory.getLogger("Application")

/**
 * Extract the project reference from a Supabase JWT key.
 * Supabase JWTs contain a "ref" claim with the project reference.
 * Returns null if the key is invalid or doesn't contain a ref.
 */
fun extractProjectRefFromKey(key: String): String? {
    return try {
        // JWT format: header.payload.signature
        val parts = key.split(".")
        if (parts.size != 3) {
            logger.warn("JWT key does not have 3 parts, got ${parts.size}")
            return null
        }

        // Decode the payload (second part), handling URL-safe base64
        val payload = parts[1]
        val paddedPayload = when (payload.length % 4) {
            2 -> "$payload=="
            3 -> "$payload="
            else -> payload
        }
        val decoded = Base64.getUrlDecoder().decode(paddedPayload)
        val json = String(decoded)
        logger.info("JWT payload: $json")

        // Simple JSON parsing for "ref" field
        val refMatch = Regex(""""ref"\s*:\s*"([^"]+)"""").find(json)
        if (refMatch == null) {
            logger.warn("No 'ref' field found in JWT payload")
        }
        refMatch?.groupValues?.get(1)
    } catch (e: Exception) {
        logger.warn("Failed to extract project ref from key: ${e.message}")
        null
    }
}

/**
 * Derive the Supabase URL from project ID, explicit URL, or anon key.
 * For hosted Supabase, the URL format is https://{project-id}.supabase.co
 */
fun deriveSupabaseUrl(explicitUrl: String?, projectId: String?, anonKey: String?): String {
    // If explicit URL is provided, use it
    if (!explicitUrl.isNullOrBlank()) {
        return explicitUrl
    }

    // If project ID is provided, construct the URL
    if (!projectId.isNullOrBlank()) {
        val derivedUrl = "https://$projectId.supabase.co"
        logger.info("Derived Supabase URL from project ID: $derivedUrl")
        return derivedUrl
    }

    // Try to derive from the anon key (legacy JWT keys only)
    if (anonKey != null) {
        val projectRef = extractProjectRefFromKey(anonKey)
        if (projectRef != null) {
            val derivedUrl = "https://$projectRef.supabase.co"
            logger.info("Derived Supabase URL from anon key: $derivedUrl")
            return derivedUrl
        }
    }

    // Fall back to local development URL
    return "http://127.0.0.1:54321"
}

/**
 * Configure the Ktor application with plugins and GraphQL routing.
 *
 * The pre-compiled [viaduct] instance, [cracInjector], and standalone [koin]
 * container are all created by the entry point in [CracMain] before the Ktor
 * server starts. Koin is managed externally (not as a Ktor plugin) so that
 * singletons survive CRaC checkpoint/restore independently of the server.
 */
fun Application.configureApplication(
    supabaseUrl: String,
    supabaseKey: String,
    configurationComplete: Boolean,
    viaduct: Viaduct,
    cracInjector: DelegatingTenantCodeInjector,
    koin: Koin
) {
    // Create object mapper for JSON serialization
    val objectMapper = jacksonObjectMapper()

    // Install CallLogging for structured request/response logging
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            // Log GraphQL and health endpoints
            call.request.path().startsWith("/graphql") || call.request.path().startsWith("/health")
        }
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"]
            val path = call.request.path()
            "$httpMethod $path - $status - $userAgent"
        }
    }

    // Install ContentNegotiation for automatic JSON serialization
    install(ContentNegotiation) {
        jackson {
            // Use the same ObjectMapper configuration as the manual one
            // This ensures consistent JSON serialization throughout the app
        }
    }

    // Install CORS plugin EARLY - must be before auth plugin to handle error responses
    // CORS headers need to be added to ALL responses, including 401 errors from auth
    if (pluginOrNull(CORS) == null) {
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader("X-User-Id")

            // Use environment-based configuration for security
            // In production, set ALLOWED_ORIGINS to comma-separated list of allowed origins
            // Supports: full URLs (https://example.com), hostnames (example.com), or host:port
            val allowedOrigins = System.getenv("ALLOWED_ORIGINS")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: listOf("http://localhost:5173", "http://127.0.0.1:5173")

            allowedOrigins.forEach { origin ->
                try {
                    // Handle different formats:
                    // - Full URL: https://example.com or http://localhost:5173
                    // - Hostname only: example.onrender.com (from Render's fromService)
                    // - Host:port: example.com:443
                    // - Render service name only: viaduct-frontend (append .onrender.com)
                    val expandedOrigin = when {
                        origin.startsWith("http://") || origin.startsWith("https://") -> origin
                        origin.contains(".") -> origin  // Already has domain
                        origin.contains(":") -> origin  // host:port format
                        else -> "$origin.onrender.com"  // Render service name, append domain
                    }
                    val normalizedOrigin = when {
                        expandedOrigin.startsWith("http://") || expandedOrigin.startsWith("https://") -> expandedOrigin
                        expandedOrigin.contains(":") -> "https://$expandedOrigin"  // host:port format
                        else -> "https://$expandedOrigin"  // hostname only, assume HTTPS
                    }

                    val uri = java.net.URI(normalizedOrigin)
                    val host = if (uri.port != -1 && uri.port != 443 && uri.port != 80) {
                        "${uri.host}:${uri.port}"
                    } else {
                        uri.host
                    }
                    val scheme = uri.scheme
                    allowHost(host, schemes = listOf(scheme))
                    logger.info("CORS: Allowing origin $scheme://$host")
                } catch (e: Exception) {
                    logger.warn("CORS: Failed to parse origin '$origin': ${e.message}")
                }
            }
        }
    }

    val httpClient = koin.getOrNull<HttpClient>()

    // Get services from external Koin for the auth plugin and routing
    val authService = koin.get<AuthService>()
    val supabaseService = koin.get<SupabaseService>()

    // Install GraphQL authentication plugin
    // Services are injected from the external Koin container
    install(GraphQLAuthentication) {
        this.objectMapper = objectMapper
        this.authService = authService
        this.supabaseService = supabaseService
        this.httpClient = httpClient ?: error("HttpClient not found in Koin")
    }

    // Each (schemaName, scopeSet) pair corresponds to one GraphQL endpoint.
    val TENANT_SCHEMAS = listOf(
        Triple("default", "/graphql",        SchemaId.Scoped("default", setOf("default", "public"))),
        Triple("github",  "/graphql/github",  SchemaId.Scoped("github",  setOf("default", "github", "public"))),
        Triple("asana",   "/graphql/asana",   SchemaId.Scoped("asana",   setOf("default", "asana",  "public"))),
        Triple("admin",   "/graphql/admin",   SchemaId.Scoped("admin",   setOf("default", "github", "asana", "admin", "public")))
    )

    suspend fun executeGraphQL(call: io.ktor.server.application.ApplicationCall, fixedSchemaId: SchemaId?) {
        val requestBody = call.cachedRequestBody ?: call.receiveText()
        val request = objectMapper.readValue(requestBody, GraphQLRequest::class.java)

        val schemaId: SchemaId
        val requestContext: Any?

        when {
            call.isIntrospection -> {
                schemaId = fixedSchemaId ?: SchemaId.Scoped("default", setOf("default", "public"))
                requestContext = null
            }
            call.isPublicOperation -> {
                schemaId = SchemaId.Scoped("public", setOf("public"))
                requestContext = null
            }
            else -> {
                val requestContextWrapper = call.requestContext
                schemaId = fixedSchemaId ?: when (authService.getSchemaId(requestContextWrapper.graphQLContext)) {
                    "admin" -> SchemaId.Scoped("admin", setOf("default", "github", "asana", "admin", "public"))
                    else    -> SchemaId.Scoped("default", setOf("default", "public"))
                }
                requestContext = requestContextWrapper
            }
        }

        val executionInput = ViaductExecutionInput.create(
            operationText = request.query,
            variables = request.variables,
            requestContext = requestContext
        )
        val result = viaduct.execute(executionInput, schemaId)
        call.respond(HttpStatusCode.OK, result.toSpecification())
    }

    routing {
        // Default schema endpoint — groups, members, TenantAsset governance
        post("/graphql") { executeGraphQL(call, null) }

        // Per-tenant endpoints — each serves its own schema subset
        post("/graphql/github") {
            executeGraphQL(call, SchemaId.Scoped("github", setOf("default", "github", "public")))
        }
        post("/graphql/asana") {
            executeGraphQL(call, SchemaId.Scoped("asana", setOf("default", "asana", "public")))
        }
        post("/graphql/admin") {
            executeGraphQL(call, SchemaId.Scoped("admin", setOf("default", "github", "asana", "admin", "public")))
        }

        // Returns the schemas the calling user has access to, for the GraphiQL dropdown.
        // Always includes "default". Adds tenant schemas based on TenantAssetPolicy grants.
        // Adds "admin" if the user has isAdmin=true.
        get("/api/schemas") {
            val authHeader = call.request.headers["Authorization"]
            val accessToken = authHeader?.removePrefix("Bearer ")?.trim()
            if (accessToken == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Authorization header required"))
                return@get
            }
            try {
                val graphQLContext = authService.createRequestContext(accessToken)
                val client = supabaseService.createAuthenticatedClient(accessToken, httpClient ?: error("no http client"))
                val userTenants = client.getUserTenantNames(graphQLContext.userId)
                val schemas = buildList {
                    add(mapOf("name" to "default", "url" to "/graphql",        "label" to "Default"))
                    if ("github" in userTenants)  add(mapOf("name" to "github", "url" to "/graphql/github", "label" to "GitHub"))
                    if ("asana"  in userTenants)  add(mapOf("name" to "asana",  "url" to "/graphql/asana",  "label" to "Asana"))
                    if (graphQLContext.isAdmin)    add(mapOf("name" to "admin",  "url" to "/graphql/admin",  "label" to "Admin"))
                }
                call.respond(HttpStatusCode.OK, schemas)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
            }
        }

        get("/login") {
            val html = this::class.java.classLoader.getResource("login.html")?.readText()
                ?: error("login.html not found in resources")
            call.respondText(html, ContentType.Text.Html)
        }

        get("/graphiql") {
            val html = this::class.java.classLoader.getResource("graphiql.html")?.readText()
                ?: error("graphiql.html not found in resources")
            call.respondText(html, ContentType.Text.Html)
        }

        // Token handoff: frontend can't write to this origin's localStorage directly.
        // This endpoint accepts a token + endpoint via query params, stores the token
        // in localStorage via a small inline script, then redirects to GraphiQL.
        get("/graphiql-auth") {
            val token = call.request.queryParameters["token"] ?: ""
            val endpoint = call.request.queryParameters["endpoint"] ?: "/graphql"
            val html = """
                <!DOCTYPE html><html><head><meta charset="utf-8"></head><body>
                <script>
                  if (${token.isNotBlank()}) {
                    localStorage.setItem('viaaccess_token', ${com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(token)});
                  }
                  window.location.replace('/graphiql?endpoint=${java.net.URLEncoder.encode(endpoint, "UTF-8")}');
                </script>
                </body></html>
            """.trimIndent()
            call.respondText(html, ContentType.Text.Html)
        }

        // Stub for future Okta SAML callback — Supabase will redirect here after SSO handshake.
        // When Okta is configured, implement: exchange the Supabase SSO code for a session,
        // store the token, and redirect to /graphiql.
        get("/auth/callback") {
            call.respondText("SSO callback not yet configured.", status = HttpStatusCode.NotImplemented)
        }

        get("/health") {
            call.respondText("OK")
        }

        // Setup status endpoint - shows configuration status
        get("/setup") {
            val status = mapOf(
                "configured" to configurationComplete,
                "supabaseUrl" to supabaseUrl,
                "supabaseUrlSource" to if (System.getenv("SUPABASE_URL") != null) "environment" else "derived from project ID or anon key",
                "supabaseAnonKey" to (System.getenv("SUPABASE_ANON_KEY") != null),
                "supabaseServiceRoleKey" to (System.getenv("SUPABASE_SERVICE_ROLE_KEY") != null),
                "allowedOrigins" to (System.getenv("ALLOWED_ORIGINS") ?: "localhost defaults"),
                "message" to if (configurationComplete) {
                    "All required configuration is set. The API is ready to use."
                } else {
                    "Missing required configuration. Set SUPABASE_ANON_KEY to enable GraphQL queries."
                },
                "docs" to "https://supabase.com/dashboard → Settings → API → 'legacy anon, service_role API Keys'"
            )
            call.respond(HttpStatusCode.OK, status)
        }
    }
}
