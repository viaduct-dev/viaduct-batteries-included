package com.example

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.example.config.DelegatingTenantCodeInjector
import com.example.config.KoinTenantCodeInjector
import com.example.config.appModule
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import org.koin.logger.slf4jLogger
import viaduct.api.bootstrap.ViaductTenantAPIBootstrapper
import viaduct.service.SchemaScopeInfo
import viaduct.service.runtime.SchemaConfiguration
import viaduct.service.runtime.StandardViaduct

/**
 * Integration test for GraphQL authentication with Supabase.
 *
 * This test requires a running local Supabase instance.
 * Run `supabase start` before executing this test.
 */
class GraphQLAuthenticationIntegrationTest : FunSpec({
    val objectMapper = jacksonObjectMapper()

    // Test credentials
    val testEmail = "test-${System.currentTimeMillis()}@example.com"
    val testPassword = "testPassword123!"

    // Supabase configuration from environment or defaults
    val supabaseUrl = System.getenv("SUPABASE_URL") ?: "http://127.0.0.1:54321"
    val supabaseAnonKey = System.getenv("SUPABASE_ANON_KEY")
        ?: "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH"

    // Initialize Viaduct and Koin (mirrors CracMain Phase 1)
    val cracInjector = DelegatingTenantCodeInjector()

    val scopes = listOf(
        SchemaScopeInfo("public",  setOf("public")),
        SchemaScopeInfo("default", setOf("default", "public")),
        SchemaScopeInfo("github",  setOf("default", "github", "public")),
        SchemaScopeInfo("asana",   setOf("default", "asana",  "public")),
        SchemaScopeInfo("admin",   setOf("default", "github", "asana", "admin", "public"))
    ).map { SchemaConfiguration.ScopeConfig(it.schemaId.id, it.scopesToApply ?: emptySet()) }

    val viaduct = StandardViaduct.Builder()
        .withTenantAPIBootstrapperBuilder(
            ViaductTenantAPIBootstrapper.Builder()
                .tenantPackagePrefix("com.example")
                .tenantCodeInjector(cracInjector)
        )
        .withSchemaConfiguration(SchemaConfiguration.fromResources(scopes = scopes.toSet()))
        .build()

    val koin = koinApplication {
        slf4jLogger()
        modules(appModule(supabaseUrl, supabaseAnonKey))
    }.koin.also { cracInjector.delegate = KoinTenantCodeInjector(it) }

    // Create a Supabase client for test user authentication
    val supabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseAnonKey
    ) {
        install(Auth)
    }

    var accessToken: String? = null

    val supabaseServiceKey = System.getenv("SUPABASE_SERVICE_ROLE_KEY")
        ?: "sb_secret_N7UND0UgjKTVK-Uodkm0Hg_xSvEMPvz"

    beforeSpec {
        // Create and authenticate a test user, then promote to admin so createGroup passes.
        // createGroup requires TenantAsset(default): EDITOR; admin satisfies that constraint.
        runBlocking {
            try {
                supabaseClient.auth.signUpWith(Email) {
                    email = testEmail
                    password = testPassword
                }
                supabaseClient.auth.signInWith(Email) {
                    email = testEmail
                    password = testPassword
                }
                accessToken = supabaseClient.auth.currentAccessTokenOrNull()
                println("Test user authenticated: $testEmail")
                println("Access token obtained: ${accessToken?.take(20)}...")
            } catch (e: Exception) {
                println("Failed to create test user: ${e.message}")
                println("Attempting to sign in with existing user...")
                try {
                    supabaseClient.auth.signInWith(Email) {
                        email = testEmail
                        password = testPassword
                    }
                    accessToken = supabaseClient.auth.currentAccessTokenOrNull()
                    println("Signed in with existing user: $testEmail")
                } catch (signInError: Exception) {
                    println("Failed to sign in: ${signInError.message}")
                    throw Exception("Could not authenticate test user", signInError)
                }
            }

            // Grant TenantAsset(default): EDITOR to the test user via service role.
            // createGroup requires has_tenant_permission('default', 'EDITOR') — admin alone is not enough.
            // We do this by creating a group, adding the user to it, and granting EDITOR on the default tenant asset.
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                try {
                    val adminHttp = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO)
                    val adminHeaders = fun io.ktor.client.request.HttpRequestBuilder.() {
                        header("Authorization", "Bearer $supabaseServiceKey")
                        header("apikey", supabaseServiceKey)
                        header("Prefer", "return=representation")
                        contentType(ContentType.Application.Json)
                    }

                    // Create a bootstrap group for this test user
                    val groupResp = adminHttp.post("$supabaseUrl/rest/v1/groups") {
                        adminHeaders()
                        setBody("""{"name":"test-editor-group","created_by":"$userId"}""")
                    }
                    val groupBody = groupResp.bodyAsText()
                    val groupId = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                        .readTree(groupBody)[0]["id"].asText()

                    // Add user to group
                    adminHttp.post("$supabaseUrl/rest/v1/group_members") {
                        adminHeaders()
                        setBody("""{"group_id":"$groupId","user_id":"$userId"}""")
                    }

                    // Look up the default tenant_asset id
                    val taResp = adminHttp.get("$supabaseUrl/rest/v1/tenant_assets?tenant_name=eq.default&select=id") {
                        header("Authorization", "Bearer $supabaseServiceKey")
                        header("apikey", supabaseServiceKey)
                    }
                    val tenantAssetId = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                        .readTree(taResp.bodyAsText())[0]["id"].asText()

                    // Grant EDITOR on default tenant to the group
                    adminHttp.post("$supabaseUrl/rest/v1/tenant_asset_policies") {
                        adminHeaders()
                        setBody("""{"tenant_asset_id":"$tenantAssetId","group_id":"$groupId","permission":"EDITOR"}""")
                    }

                    println("Test user granted TenantAsset(default): EDITOR via group $groupId")
                } catch (e: Exception) {
                    println("Warning: could not grant test user EDITOR: ${e.message}")
                }
            }
        }
    }

    // Helper function to create a test client with the application configured
    fun testWithApp(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            application {
                configureApplication(
                    supabaseUrl = supabaseUrl,
                    supabaseKey = supabaseAnonKey,
                    configurationComplete = true,
                    viaduct = viaduct,
                    cracInjector = cracInjector,
                    koin = koin
                )
            }
            block()
        }
    }

    test("GraphQL request without authentication should return 401") {
        testWithApp {
            val response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "query": "{ groups { id name } }"
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.Unauthorized
            val body = response.bodyAsText()
            body shouldContain "Authorization header required"
        }
    }

    test("GraphQL request with invalid token should return 401") {
        testWithApp {
            val response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer invalid-token-12345")
                setBody("""
                    {
                        "query": "{ groups { id name } }"
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.Unauthorized
            val body = response.bodyAsText()
            body shouldContain "JWT token"
        }
    }

    test("GraphQL request with valid token should succeed") {
        accessToken shouldNotBe null

        testWithApp {
            val response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                setBody("""
                    {
                        "query": "{ groups { id name description } }"
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            println("GraphQL Response: $body")

            // Should have a data field (might be empty array, but should succeed)
            body shouldContainJsonKey "data"
            body shouldContainJsonKey "data.groups"
        }
    }

    test("GraphQL introspection with valid token should work") {
        accessToken shouldNotBe null

        testWithApp {
            val response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                setBody("""
                    {
                        "query": "{ __schema { queryType { name } mutationType { name } } }"
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()

            body shouldContainJsonKey "data.__schema.queryType.name"
            body shouldContainJsonKey "data.__schema.mutationType.name"
            body shouldContain "Query"
            body shouldContain "Mutation"
        }
    }

    test("Create group with valid token should succeed") {
        accessToken shouldNotBe null

        testWithApp {
            // Create a checkbox group
            val groupResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                setBody("""
                    {
                        "query": "mutation CreateGroup(${'$'}name: String!, ${'$'}description: String) { createGroup(input: { name: ${'$'}name, description: ${'$'}description }) { id name description createdBy createdAt } }",
                        "variables": {
                            "name": "Test Group from Integration Test",
                            "description": "Testing group creation with authentication"
                        }
                    }
                """.trimIndent())
            }

            val groupBody = groupResponse.bodyAsText()
            println("Create group response: $groupBody")

            groupResponse.status shouldBe HttpStatusCode.OK
            groupBody shouldContainJsonKey "data.createGroup"
            groupBody shouldContainJsonKey "data.createGroup.id"
            groupBody shouldContain "Test Group from Integration Test"
        }
    }

    afterSpec {
        // Cleanup: sign out
        runBlocking {
            try {
                supabaseClient.auth.signOut()
                println("Test user signed out")
            } catch (e: Exception) {
                println("Failed to sign out: ${e.message}")
            }
        }
    }
})
