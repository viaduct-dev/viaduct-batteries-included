package com.viaduct

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.viaduct.config.KoinTenantCodeInjector
import com.viaduct.config.RequestContext
import com.viaduct.config.appModule
import com.viaduct.models.GraphQLRequest
import com.viaduct.plugins.GraphQLAuthentication
import com.viaduct.plugins.cachedRequestBody
import com.viaduct.plugins.isPublicOperation
import com.viaduct.plugins.requestContext
import com.viaduct.services.AuthService
import com.viaduct.services.GroupService
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
import org.slf4j.event.Level
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.scope
import org.koin.ktor.ext.get
import org.koin.ktor.ext.getKoin
import org.koin.logger.slf4jLogger
import viaduct.service.BasicViaductFactory
import viaduct.service.TenantRegistrationInfo
import viaduct.service.SchemaRegistrationInfo
import viaduct.service.SchemaScopeInfo
import viaduct.service.api.ExecutionInput as ViaductExecutionInput
import viaduct.service.api.SchemaId
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
 * Configure the Ktor application with GraphQL and authentication
 */
fun Application.module() {
    // Support both old and new env var names for backwards compatibility
    val supabaseKey = System.getenv("SUPABASE_PUBLISHABLE_KEY")
        ?: System.getenv("SUPABASE_ANON_KEY")
    val supabaseProjectId = System.getenv("SUPABASE_PROJECT_ID")
    val supabaseUrl = deriveSupabaseUrl(System.getenv("SUPABASE_URL"), supabaseProjectId, supabaseKey)

    // Graceful handling of missing configuration
    val configurationComplete = supabaseKey != null

    if (!configurationComplete) {
        logger.warn("=" .repeat(60))
        logger.warn("SUPABASE_PUBLISHABLE_KEY is not set!")
        logger.warn("The server will start but GraphQL queries will fail.")
        logger.warn("Set SUPABASE_PUBLISHABLE_KEY environment variable to enable full functionality.")
        logger.warn("Get your key from: https://supabase.com/dashboard → Settings → API")
        logger.warn("=" .repeat(60))
    }

    configureApplication(supabaseUrl, supabaseKey ?: "NOT_CONFIGURED", configurationComplete)
}

/**
 * Configure the application with the given Supabase credentials
 */
fun Application.configureApplication(supabaseUrl: String, supabaseKey: String, configurationComplete: Boolean = true) {
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

    // Install Koin plugin for dependency injection (Koin 4.x pattern)
    install(Koin) {
        slf4jLogger()
        modules(appModule(supabaseUrl, supabaseKey))
    }

    // Register shutdown hook to close HttpClient properly
    monitor.subscribe(ApplicationStopped) {
        val httpClient = getKoin().getOrNull<HttpClient>()
        httpClient?.close()
    }

    // Install GraphQL authentication plugin
    // This handles extracting and validating auth tokens as a cross-cutting concern
    install(GraphQLAuthentication) {
        this.objectMapper = objectMapper
    }

    // Get the Koin instance from the application context
    val koin = getKoin()

    // Use Koin-based dependency injector for Viaduct resolvers
    val koinInjector = KoinTenantCodeInjector(koin)

    // Get services from Koin for application configuration
    // Note: These are singletons needed at application startup for Viaduct configuration
    val authService = koin.get<AuthService>()
    val groupService = koin.get<GroupService>()
    val supabaseService = koin.get<SupabaseService>()

    // Build Viaduct service using BasicViaductFactory
    val viaduct = BasicViaductFactory.create(
        schemaRegistrationInfo = SchemaRegistrationInfo(
            scopes = listOf(
                SchemaScopeInfo("public", setOf("public")),
                SchemaScopeInfo("default", setOf("default", "public")),
                SchemaScopeInfo("admin", setOf("default", "admin", "public"))
            )
        ),
        tenantRegistrationInfo = TenantRegistrationInfo(
            tenantPackagePrefix = "com.viaduct",
            tenantCodeInjector = koinInjector
        )
    )

    // Install CORS plugin (with idempotency check for test compatibility)
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

    routing {
        post("/graphql") {
            // Get request body - either from cache (if auth plugin already read it) or fresh
            val requestBody = call.cachedRequestBody ?: call.receiveText()
            val request = objectMapper.readValue(requestBody, GraphQLRequest::class.java)

            // Check if this is a public operation (no auth required)
            val schemaId: SchemaId
            val requestContext: Any?

            if (call.isPublicOperation) {
                // Public operations use the "public" schema and no request context
                schemaId = SchemaId.Scoped("public", setOf("public"))
                requestContext = null
            } else {
                // Get RequestContext - authentication is already handled by the plugin
                val requestContextWrapper = call.requestContext

                // Use AuthService to determine schema ID
                val schemaIdStr = authService.getSchemaId(requestContextWrapper.graphQLContext)
                schemaId = when (schemaIdStr) {
                    "admin" -> SchemaId.Scoped("admin", setOf("default", "admin", "public"))
                    else -> SchemaId.Scoped("default", setOf("default", "public"))
                }
                requestContext = requestContextWrapper
            }

            // Build Viaduct ExecutionInput
            val executionInput = ViaductExecutionInput.create(
                operationText = request.query,
                variables = request.variables,
                requestContext = requestContext
            )

            // Execute GraphQL query
            val result = viaduct.execute(executionInput, schemaId)

            // Ktor's ContentNegotiation automatically serializes the response to JSON
            call.respond(HttpStatusCode.OK, result.toSpecification())
        }

        get("/graphiql") {
            val html = this::class.java.classLoader.getResource("graphiql.html")?.readText()
                ?: error("graphiql.html not found in resources")
            call.respondText(html, ContentType.Text.Html)
        }

        get("/health") {
            call.respondText("OK")
        }

        // Setup status endpoint - shows configuration status
        get("/setup") {
            val status = mapOf(
                "configured" to configurationComplete,
                "supabaseUrl" to supabaseUrl,
                "supabaseUrlSource" to if (System.getenv("SUPABASE_URL") != null) "environment" else "derived from publishable key",
                "supabasePublishableKey" to (System.getenv("SUPABASE_PUBLISHABLE_KEY") != null || System.getenv("SUPABASE_ANON_KEY") != null),
                "supabaseSecretKey" to (System.getenv("SUPABASE_SECRET_KEY") != null || System.getenv("SUPABASE_SERVICE_ROLE_KEY") != null),
                "allowedOrigins" to (System.getenv("ALLOWED_ORIGINS") ?: "localhost defaults"),
                "message" to if (configurationComplete) {
                    "All required configuration is set. The API is ready to use."
                } else {
                    "Missing required configuration. Set SUPABASE_PUBLISHABLE_KEY to enable GraphQL queries."
                },
                "docs" to "https://supabase.com/dashboard → Settings → API"
            )
            call.respond(HttpStatusCode.OK, status)
        }
    }
}

// Entry point is now EngineMain configured via application.conf
