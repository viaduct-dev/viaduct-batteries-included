package com.graphqlcheckmate

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking

/**
 * Integration test for CheckboxGroup resolvers.
 *
 * Tests:
 * - Creating checkbox groups
 * - Querying groups (all and by ID)
 * - Adding/removing members
 * - Field resolvers (members, checklistItems)
 *
 * Requires running local Supabase instance: `supabase start`
 */
class CheckboxGroupResolversIntegrationTest : FunSpec({
    val objectMapper = jacksonObjectMapper()

    // Test credentials
    val owner1Email = "owner1-${System.currentTimeMillis()}@example.com"
    val owner2Email = "owner2-${System.currentTimeMillis()}@example.com"
    val member1Email = "member1-${System.currentTimeMillis()}@example.com"
    val testPassword = "testPassword123!"

    // Supabase configuration
    val supabaseUrl = System.getenv("SUPABASE_URL") ?: "http://127.0.0.1:54321"
    val supabaseAnonKey = System.getenv("SUPABASE_ANON_KEY")
        ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"

    // Create Supabase clients for different test users
    val owner1Client = createSupabaseClient(supabaseUrl, supabaseAnonKey) { install(Auth) }
    val owner2Client = createSupabaseClient(supabaseUrl, supabaseAnonKey) { install(Auth) }
    val member1Client = createSupabaseClient(supabaseUrl, supabaseAnonKey) { install(Auth) }

    var owner1Token: String? = null
    var owner2Token: String? = null
    var member1Token: String? = null
    var owner1Id: String? = null
    var owner2Id: String? = null
    var member1Id: String? = null

    beforeSpec {
        runBlocking {
            // Create and authenticate owner1
            try {
                owner1Client.auth.signUpWith(Email) {
                    email = owner1Email
                    password = testPassword
                }
                owner1Client.auth.signInWith(Email) {
                    email = owner1Email
                    password = testPassword
                }
                owner1Token = owner1Client.auth.currentAccessTokenOrNull()
                owner1Id = owner1Client.auth.currentUserOrNull()?.id
                println("Owner1 authenticated: $owner1Email (ID: $owner1Id)")
            } catch (e: Exception) {
                owner1Client.auth.signInWith(Email) {
                    email = owner1Email
                    password = testPassword
                }
                owner1Token = owner1Client.auth.currentAccessTokenOrNull()
                owner1Id = owner1Client.auth.currentUserOrNull()?.id
            }

            // Create and authenticate owner2
            try {
                owner2Client.auth.signUpWith(Email) {
                    email = owner2Email
                    password = testPassword
                }
                owner2Client.auth.signInWith(Email) {
                    email = owner2Email
                    password = testPassword
                }
                owner2Token = owner2Client.auth.currentAccessTokenOrNull()
                owner2Id = owner2Client.auth.currentUserOrNull()?.id
                println("Owner2 authenticated: $owner2Email (ID: $owner2Id)")
            } catch (e: Exception) {
                owner2Client.auth.signInWith(Email) {
                    email = owner2Email
                    password = testPassword
                }
                owner2Token = owner2Client.auth.currentAccessTokenOrNull()
                owner2Id = owner2Client.auth.currentUserOrNull()?.id
            }

            // Create and authenticate member1
            try {
                member1Client.auth.signUpWith(Email) {
                    email = member1Email
                    password = testPassword
                }
                member1Client.auth.signInWith(Email) {
                    email = member1Email
                    password = testPassword
                }
                member1Token = member1Client.auth.currentAccessTokenOrNull()
                member1Id = member1Client.auth.currentUserOrNull()?.id
                println("Member1 authenticated: $member1Email (ID: $member1Id)")
            } catch (e: Exception) {
                member1Client.auth.signInWith(Email) {
                    email = member1Email
                    password = testPassword
                }
                member1Token = member1Client.auth.currentAccessTokenOrNull()
                member1Id = member1Client.auth.currentUserOrNull()?.id
            }
        }
    }

    fun testWithApp(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            application {
                configureApplication(supabaseUrl, supabaseAnonKey)
            }
            block()
        }
    }

    test("createCheckboxGroup mutation should create a new group") {
        owner1Token shouldNotBe null
        owner1Id shouldNotBe null

        testWithApp {
            val response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Test Group\", description: \"Test Description\" }) { id name description ownerId createdAt updatedAt } }"
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            println("createCheckboxGroup response: $body")

            body shouldContainJsonKey "data.createCheckboxGroup"
            body shouldContainJsonKey "data.createCheckboxGroup.id"
            body shouldContain "Test Group"
            body shouldContain "Test Description"
            body shouldContain owner1Id!!
        }
    }

    test("checkboxGroups query should return user's groups") {
        owner1Token shouldNotBe null

        testWithApp {
            // First create a group
            val createResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"My Group\", description: \"My Description\" }) { id } }"
                    }
                """.trimIndent())
            }
            createResponse.status shouldBe HttpStatusCode.OK

            // Then query for all groups
            val queryResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "{ checkboxGroups { id name description ownerId } }"
                    }
                """.trimIndent())
            }

            queryResponse.status shouldBe HttpStatusCode.OK
            val body = queryResponse.bodyAsText()
            println("checkboxGroups response: $body")

            body shouldContainJsonKey "data.checkboxGroups"
            body shouldContain "My Group"
        }
    }

    test("checkboxGroup query by ID should return specific group") {
        owner1Token shouldNotBe null

        testWithApp {
            // Create a group
            val createResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Specific Group\", description: \"Specific Description\" }) { id name } }"
                    }
                """.trimIndent())
            }
            val createBody = createResponse.bodyAsText()

            // Extract the group ID from response
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(createBody)
            val groupId = groupIdMatch?.groupValues?.get(1)
            groupId shouldNotBe null

            // Query by ID
            val queryResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "{ checkboxGroup(id: \"$groupId\") { id name description } }"
                    }
                """.trimIndent())
            }

            queryResponse.status shouldBe HttpStatusCode.OK
            val body = queryResponse.bodyAsText()
            println("checkboxGroup by ID response: $body")

            body shouldContainJsonKey "data.checkboxGroup"
            body shouldContain "Specific Group"
        }
    }

    test("addGroupMember mutation should add a member to the group") {
        owner1Token shouldNotBe null
        member1Id shouldNotBe null

        testWithApp {
            // Create a group as owner1
            val createResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Member Test Group\" }) { id } }"
                    }
                """.trimIndent())
            }
            val createBody = createResponse.bodyAsText()
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(createBody)
            val groupId = groupIdMatch?.groupValues?.get(1)
            groupId shouldNotBe null

            // Add member1 to the group
            val addMemberResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { addGroupMember(input: { groupId: \"$groupId\", userId: \"$member1Id\" }) { id userId groupId joinedAt } }"
                    }
                """.trimIndent())
            }

            addMemberResponse.status shouldBe HttpStatusCode.OK
            val body = addMemberResponse.bodyAsText()
            println("addGroupMember response: $body")

            body shouldContainJsonKey "data.addGroupMember"
            body shouldContain member1Id!!
        }
    }

    test("members field resolver should return group members") {
        owner1Token shouldNotBe null
        member1Id shouldNotBe null

        testWithApp {
            // Create a group
            val createResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Members Test Group\" }) { id } }"
                    }
                """.trimIndent())
            }
            val createBody = createResponse.bodyAsText()
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(createBody)
            val groupId = groupIdMatch?.groupValues?.get(1)
            groupId shouldNotBe null

            // Add a member
            client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { addGroupMember(input: { groupId: \"$groupId\", userId: \"$member1Id\" }) { id } }"
                    }
                """.trimIndent())
            }

            // Query the group with members field
            val queryResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "{ checkboxGroup(id: \"$groupId\") { id name members { id userId groupId joinedAt } } }"
                    }
                """.trimIndent())
            }

            queryResponse.status shouldBe HttpStatusCode.OK
            val body = queryResponse.bodyAsText()
            println("members field resolver response: $body")

            body shouldContainJsonKey "data.checkboxGroup.members"
            body shouldContain member1Id!!
        }
    }

    test("removeGroupMember mutation should remove a member") {
        owner1Token shouldNotBe null
        member1Id shouldNotBe null

        testWithApp {
            // Create a group
            val createResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Remove Member Test Group\" }) { id } }"
                    }
                """.trimIndent())
            }
            val createBody = createResponse.bodyAsText()
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(createBody)
            val groupId = groupIdMatch?.groupValues?.get(1)
            groupId shouldNotBe null

            // Add member
            val addResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { addGroupMember(input: { groupId: \"$groupId\", userId: \"$member1Id\" }) { id } }"
                    }
                """.trimIndent())
            }
            addResponse.status shouldBe HttpStatusCode.OK

            // Remove member
            val removeResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { removeGroupMember(input: { groupId: \"$groupId\", userId: \"$member1Id\" }) }"
                    }
                """.trimIndent())
            }

            removeResponse.status shouldBe HttpStatusCode.OK
            val body = removeResponse.bodyAsText()
            println("removeGroupMember response: $body")

            body shouldContainJsonKey "data.removeGroupMember"
        }
    }

    test("non-owner cannot add members to group") {
        owner1Token shouldNotBe null
        member1Token shouldNotBe null
        owner2Id shouldNotBe null

        testWithApp {
            // Owner1 creates a group
            val createResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Owner Only Group\" }) { id } }"
                    }
                """.trimIndent())
            }
            val createBody = createResponse.bodyAsText()
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(createBody)
            val groupId = groupIdMatch?.groupValues?.get(1)
            groupId shouldNotBe null

            // Add member1 to the group
            client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $owner1Token")
                setBody("""
                    {
                        "query": "mutation { addGroupMember(input: { groupId: \"$groupId\", userId: \"$member1Id\" }) { id } }"
                    }
                """.trimIndent())
            }

            // Member1 (non-owner) tries to add owner2
            val unauthorizedResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $member1Token")
                setBody("""
                    {
                        "query": "mutation { addGroupMember(input: { groupId: \"$groupId\", userId: \"$owner2Id\" }) { id } }"
                    }
                """.trimIndent())
            }

            val body = unauthorizedResponse.bodyAsText()
            println("Unauthorized addGroupMember response: $body")

            // Should fail with RLS policy violation (403 or error in response)
            body shouldContain "error"
        }
    }

    afterSpec {
        runBlocking {
            try {
                owner1Client.auth.signOut()
                owner2Client.auth.signOut()
                member1Client.auth.signOut()
                println("All test users signed out")
            } catch (e: Exception) {
                println("Failed to sign out: ${e.message}")
            }
        }
    }
})
