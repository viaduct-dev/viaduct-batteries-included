package com.graphqlcheckmate

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.graphqlcheckmate.config.KoinTenantCodeInjector
import com.graphqlcheckmate.config.RequestContext
import com.graphqlcheckmate.config.appModule
import com.graphqlcheckmate.plugins.GraphQLAuthentication
import com.graphqlcheckmate.plugins.requestContext
import com.graphqlcheckmate.policy.GroupMembershipCheckerFactory
import com.graphqlcheckmate.services.AuthService
import com.graphqlcheckmate.services.GroupService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.cors.routing.CORS
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.scope
import org.koin.ktor.ext.get
import org.koin.ktor.ext.getKoin
import org.koin.logger.slf4jLogger
import viaduct.api.bootstrap.ViaductTenantAPIBootstrapper
import viaduct.service.runtime.SchemaRegistryConfiguration
import viaduct.service.runtime.StandardViaduct
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
    // Create object mapper for JSON serialization
    val objectMapper = jacksonObjectMapper()

    // Install Koin plugin for dependency injection (Koin 4.x pattern)
    install(Koin) {
        slf4jLogger()
        modules(appModule(supabaseUrl, supabaseKey))
    }

    // Install GraphQL authentication plugin
    // This handles extracting and validating auth tokens as a cross-cutting concern
    install(GraphQLAuthentication) {
        this.objectMapper = objectMapper
    }

    // Get the Koin instance from the application context
    val koinInstance = getKoin()

    // Use Koin-based dependency injector for Viaduct resolvers
    val koinInjector = KoinTenantCodeInjector(koinInstance)

    // Get services from Koin for application configuration
    // Note: These are singletons needed at application startup for Viaduct configuration
    val authService = get<AuthService>()
    val groupService = get<GroupService>()
    val supabaseService = get<SupabaseService>()

    // Build Viaduct service using StandardViaduct.Builder
    // Register CheckerExecutorFactory for policy checks
    val viaduct = StandardViaduct.Builder()
        .withTenantAPIBootstrapperBuilder(
            ViaductTenantAPIBootstrapper.Builder()
                .tenantPackagePrefix("com.graphqlcheckmate")
                .tenantCodeInjector(koinInjector)
        )
        .withSchemaRegistryConfiguration(
            SchemaRegistryConfiguration.fromResources(
                scopes = setOf(
                    SchemaRegistryConfiguration.ScopeConfig("default", setOf("default")),
                    SchemaRegistryConfiguration.ScopeConfig("admin", setOf("default", "admin"))
                ),
                fullSchemaIds = listOf("default")
            )
        )
        .withCheckerExecutorFactoryCreator { schema ->
            GroupMembershipCheckerFactory(schema, groupService)
        }
        .build()

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
    } catch (e: io.ktor.server.application.DuplicatePluginException) {
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

            // Get RequestContext - authentication is already handled by the plugin
            val requestContextWrapper = call.requestContext

            // Use AuthService to determine schema ID
            val schemaId = authService.getSchemaId(requestContextWrapper.graphQLContext)

            // Build Viaduct ExecutionInput - pass the RequestContext wrapper
            // Viaduct will pass this to all resolvers and policy executors
            val executionInput = ViaductExecutionInput.create(
                schemaId = schemaId,
                operationText = query,
                variables = variables,
                requestContext = requestContextWrapper // Pass the typed wrapper!
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
