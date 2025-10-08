package com.graphqlcheckmate

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.graphqlcheckmate.config.KoinTenantCodeInjector
import com.graphqlcheckmate.config.appModule
import com.graphqlcheckmate.services.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.cors.routing.CORS
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import viaduct.service.BasicViaductFactory
import viaduct.service.SchemaRegistrationInfo
import viaduct.service.SchemaScopeInfo
import viaduct.service.TenantRegistrationInfo
import viaduct.service.api.ExecutionInput as ViaductExecutionInput

/**
 * Configure the Ktor application with GraphQL and authentication
 */
fun Application.module() {
    val supabaseUrl = System.getenv("SUPABASE_URL") ?: "http://127.0.0.1:54321"
    val supabaseKey = System.getenv("SUPABASE_ANON_KEY") ?: error("SUPABASE_ANON_KEY must be set")

    configureApplication(supabaseUrl, supabaseKey)
}

/**
 * Configure the application with the given Supabase credentials
 */
fun Application.configureApplication(supabaseUrl: String, supabaseKey: String) {
    // Initialize Koin for dependency injection
    val koinApp = try {
        startKoin {
            modules(appModule(supabaseUrl, supabaseKey))
        }
    } catch (e: Exception) {
        // Koin already started (e.g., in tests), stop and restart
        stopKoin()
        startKoin {
            modules(appModule(supabaseUrl, supabaseKey))
        }
    }

    val koin = koinApp.koin

    // Use Koin-based dependency injector for Viaduct resolvers
    val koinInjector = KoinTenantCodeInjector(koin)

    // Build Viaduct service using BasicViaductFactory
    // Register two schemas: one with default scope, one with admin scope
    val viaduct = BasicViaductFactory.create(
        schemaRegistrationInfo = SchemaRegistrationInfo(
            scopes = listOf(
                SchemaScopeInfo("default", setOf("default")),
                SchemaScopeInfo("admin", setOf("default", "admin"))
            )
        ),
        tenantRegistrationInfo = TenantRegistrationInfo(
            tenantPackagePrefix = "com.graphqlcheckmate",
            tenantCodeInjector = koinInjector
        )
    )

    // Get AuthService from Koin
    val authService = koin.get<AuthService>()

    val objectMapper: ObjectMapper = jacksonObjectMapper()

    // Install CORS plugin (with idempotency check for test compatibility)
    try {
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader("X-User-Id")
            anyHost()
        }
    } catch (e: io.ktor.server.application.DuplicateApplicationPluginException) {
        // Plugin already installed - this is expected in test scenarios
    }

    routing {
        post("/graphql") {
                val requestBody = call.receiveText()
                val request = objectMapper.readValue(requestBody, Map::class.java)

                @Suppress("UNCHECKED_CAST")
                val query = request["query"] as? String ?: ""
                @Suppress("UNCHECKED_CAST")
                val variables = request["variables"] as? Map<String, Any?> ?: emptyMap()

                // Extract authentication token from Authorization header
                val authHeader = call.request.headers["Authorization"]
                val accessToken = authHeader?.removePrefix("Bearer ")?.trim()

                val requestContext: GraphQLRequestContext = if (accessToken != null && accessToken.isNotEmpty()) {
                    try {
                        // Use AuthService to create request context from token
                        authService.createRequestContext(accessToken)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            objectMapper.writeValueAsString(mapOf("error" to "Invalid or expired token: ${e.message}"))
                        )
                        return@post
                    }
                } else {
                    // No token provided - return unauthorized
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        objectMapper.writeValueAsString(mapOf("error" to "Authorization header required"))
                    )
                    return@post
                }

                // Use AuthService to determine schema ID
                val schemaId = authService.getSchemaId(requestContext)

                // Build Viaduct ExecutionInput
                val executionInput = ViaductExecutionInput.create(
                    schemaId = schemaId,
                    operationText = query,
                    variables = variables,
                    requestContext = requestContext
                )

                // Execute GraphQL query
                val result = viaduct.execute(executionInput)

                call.respond(HttpStatusCode.OK, objectMapper.writeValueAsString(result.toSpecification()))
            }

            get("/graphiql") {
                call.respondText(graphiQLHtml(), ContentType.Text.Html)
            }

        get("/health") {
            call.respondText("OK")
        }
    }
}

// Entry point is now EngineMain configured via application.conf

private fun graphiQLHtml() = """
<!DOCTYPE html>
<html>
<head>
    <title>GraphiQL</title>
    <link rel="stylesheet" href="https://unpkg.com/graphiql/graphiql.min.css" />
</head>
<body style="margin: 0;">
    <div id="graphiql" style="height: 100vh;"></div>
    <script crossorigin src="https://unpkg.com/react/umd/react.production.min.js"></script>
    <script crossorigin src="https://unpkg.com/react-dom/umd/react-dom.production.min.js"></script>
    <script crossorigin src="https://unpkg.com/graphiql/graphiql.min.js"></script>
    <script>
        const fetcher = GraphiQL.createFetcher({ url: '/graphql' });
        ReactDOM.render(
            React.createElement(GraphiQL, { fetcher: fetcher }),
            document.getElementById('graphiql'),
        );
    </script>
</body>
</html>
""".trimIndent()
