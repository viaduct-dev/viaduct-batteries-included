package com.graphqlcheckmate

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.graphqlcheckmate.resolvers.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.cors.routing.CORS
import viaduct.service.BasicViaductFactory
import viaduct.service.SchemaRegistrationInfo
import viaduct.service.SchemaScopeInfo
import viaduct.service.TenantRegistrationInfo
import viaduct.service.api.ExecutionInput as ViaductExecutionInput
import viaduct.service.api.spi.TenantCodeInjector
import javax.inject.Provider

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
    val supabaseService = SupabaseService(supabaseUrl, supabaseKey)

    // Create custom TenantCodeInjector to provide resolver dependencies
    val customInjector = object : TenantCodeInjector {
        override fun <T> getProvider(clazz: Class<T>): Provider<T> {
            return Provider {
                @Suppress("UNCHECKED_CAST")
                when (clazz) {
                    ChecklistItemsQueryResolver::class.java -> ChecklistItemsQueryResolver(supabaseService) as T
                    ChecklistItemNodeResolver::class.java -> ChecklistItemNodeResolver(supabaseService) as T
                    CreateChecklistItemResolver::class.java -> CreateChecklistItemResolver(supabaseService) as T
                    UpdateChecklistItemResolver::class.java -> UpdateChecklistItemResolver(supabaseService) as T
                    DeleteChecklistItemResolver::class.java -> DeleteChecklistItemResolver(supabaseService) as T
                    else -> error("Unknown resolver class: ${clazz.name}")
                }
            }
        }
    }

    // Build Viaduct service using BasicViaductFactory
    val viaduct = BasicViaductFactory.create(
        schemaRegistrationInfo = SchemaRegistrationInfo(
            scopes = listOf(SchemaScopeInfo("default", setOf("default")))
        ),
        tenantRegistrationInfo = TenantRegistrationInfo(
            tenantPackagePrefix = "com.graphqlcheckmate",
            tenantCodeInjector = customInjector
        )
    )

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
                        // First, verify the token with Supabase Auth
                        val userInfo = supabaseService.verifyToken(accessToken)

                        // Create a serializable request context
                        // Resolvers will create authenticated clients on-demand from this context
                        GraphQLRequestContext(
                            userId = userInfo.id,
                            accessToken = accessToken
                        )
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

                // Build Viaduct ExecutionInput
                val executionInput = ViaductExecutionInput.create(
                    schemaId = "default",
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
