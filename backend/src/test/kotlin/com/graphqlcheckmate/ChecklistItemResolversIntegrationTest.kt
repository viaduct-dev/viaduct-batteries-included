package com.graphqlcheckmate

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
 * Integration test for ChecklistItem resolvers.
 *
 * Tests:
 * - Creating checklist items in groups
 * - Querying items (all, by group)
 * - Updating items
 * - Deleting items
 * - Access control (only group members can access items)
 *
 * Requires running local Supabase instance: `supabase start`
 */
class ChecklistItemResolversIntegrationTest : FunSpec({
    // Helper function to decode GlobalID to internal UUID
    fun decodeGlobalId(globalId: String): String {
        val decoded = String(java.util.Base64.getDecoder().decode(globalId))
        return decoded.substringAfter(":")
    }

    // Test credentials
    val user1Email = "user1-items-${System.currentTimeMillis()}@example.com"
    val user2Email = "user2-items-${System.currentTimeMillis()}@example.com"
    val outsiderEmail = "outsider-${System.currentTimeMillis()}@example.com"
    val testPassword = "testPassword123!"

    // Supabase configuration
    val supabaseUrl = System.getenv("SUPABASE_URL") ?: "http://127.0.0.1:54321"
    val supabaseAnonKey = System.getenv("SUPABASE_ANON_KEY")
        ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"

    // Create Supabase clients
    val user1Client = createSupabaseClient(supabaseUrl, supabaseAnonKey) { install(Auth) }
    val user2Client = createSupabaseClient(supabaseUrl, supabaseAnonKey) { install(Auth) }
    val outsiderClient = createSupabaseClient(supabaseUrl, supabaseAnonKey) { install(Auth) }

    var user1Token: String? = null
    var user2Token: String? = null
    var outsiderToken: String? = null
    var user1Id: String? = null
    var user2Id: String? = null

    beforeSpec {
        runBlocking {
            // Authenticate user1
            try {
                user1Client.auth.signUpWith(Email) {
                    email = user1Email
                    password = testPassword
                }
                user1Client.auth.signInWith(Email) {
                    email = user1Email
                    password = testPassword
                }
                user1Token = user1Client.auth.currentAccessTokenOrNull()
                user1Id = user1Client.auth.currentUserOrNull()?.id
                println("User1 authenticated: $user1Email (ID: $user1Id)")
            } catch (e: Exception) {
                user1Client.auth.signInWith(Email) {
                    email = user1Email
                    password = testPassword
                }
                user1Token = user1Client.auth.currentAccessTokenOrNull()
                user1Id = user1Client.auth.currentUserOrNull()?.id
            }

            // Authenticate user2
            try {
                user2Client.auth.signUpWith(Email) {
                    email = user2Email
                    password = testPassword
                }
                user2Client.auth.signInWith(Email) {
                    email = user2Email
                    password = testPassword
                }
                user2Token = user2Client.auth.currentAccessTokenOrNull()
                user2Id = user2Client.auth.currentUserOrNull()?.id
                println("User2 authenticated: $user2Email (ID: $user2Id)")
            } catch (e: Exception) {
                user2Client.auth.signInWith(Email) {
                    email = user2Email
                    password = testPassword
                }
                user2Token = user2Client.auth.currentAccessTokenOrNull()
                user2Id = user2Client.auth.currentUserOrNull()?.id
            }

            // Authenticate outsider
            try {
                outsiderClient.auth.signUpWith(Email) {
                    email = outsiderEmail
                    password = testPassword
                }
                outsiderClient.auth.signInWith(Email) {
                    email = outsiderEmail
                    password = testPassword
                }
                outsiderToken = outsiderClient.auth.currentAccessTokenOrNull()
                println("Outsider authenticated: $outsiderEmail")
            } catch (e: Exception) {
                outsiderClient.auth.signInWith(Email) {
                    email = outsiderEmail
                    password = testPassword
                }
                outsiderToken = outsiderClient.auth.currentAccessTokenOrNull()
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

    test("createChecklistItem mutation should create item in group") {
        user1Token shouldNotBe null
        user1Id shouldNotBe null

        testWithApp {
            // Create a group first
            val createGroupResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Items Group\" }) { id } }"
                    }
                """.trimIndent())
            }
            val groupBody = createGroupResponse.bodyAsText()
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(groupBody)
            val groupIdGlobal = groupIdMatch?.groupValues?.get(1)
            groupIdGlobal shouldNotBe null

            // Decode GlobalID to get internal UUID
            val groupIdInternal = decodeGlobalId(groupIdGlobal!!)

            // Create a checklist item in the group
            val response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createChecklistItem(input: { title: \"Buy groceries\", groupId: \"$groupIdGlobal\" }) { id title completed userId groupId createdAt updatedAt } }"
                    }
                """.trimIndent())
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            println("createChecklistItem response: $body")

            body shouldContainJsonKey "data.createChecklistItem"
            body shouldContain "Buy groceries"
            body shouldContain user1Id!!
            // ChecklistItem.groupId is String type, so it returns internal UUID not GlobalID
            body shouldContain groupIdInternal
        }
    }

    test("checklistItems query should return all accessible items") {
        user1Token shouldNotBe null
        user1Id shouldNotBe null

        testWithApp {
            // Create group and item
            val groupResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"All Items Group\" }) { id } }"
                    }
                """.trimIndent())
            }
            val groupBody = groupResponse.bodyAsText()
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(groupBody)
            val groupId = groupIdMatch?.groupValues?.get(1)

            client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createChecklistItem(input: { title: \"Task 1\", groupId: \"$groupId\" }) { id } }"
                    }
                """.trimIndent())
            }

            // Query all items
            val queryResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "{ checklistItems { id title completed userId groupId } }"
                    }
                """.trimIndent())
            }

            queryResponse.status shouldBe HttpStatusCode.OK
            val body = queryResponse.bodyAsText()
            println("checklistItems response: $body")

            body shouldContainJsonKey "data.checklistItems"
            body shouldContain "Task 1"
        }
    }

    test("checklistItemsByGroup query should filter by group") {
        user1Token shouldNotBe null
        user1Id shouldNotBe null

        testWithApp {
            // Create two groups
            val group1Response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Group 1\" }) { id } }"
                    }
                """.trimIndent())
            }
            val group1Body = group1Response.bodyAsText()
            val group1IdMatch = Regex(""""id":\s*"([^"]+)"""").find(group1Body)
            val group1Id = group1IdMatch?.groupValues?.get(1)

            val group2Response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Group 2\" }) { id } }"
                    }
                """.trimIndent())
            }
            val group2Body = group2Response.bodyAsText()
            val group2IdMatch = Regex(""""id":\s*"([^"]+)"""").find(group2Body)
            val group2Id = group2IdMatch?.groupValues?.get(1)

            // Create item in group1
            client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createChecklistItem(input: { title: \"Group 1 Task\", groupId: \"$group1Id\" }) { id } }"
                    }
                """.trimIndent())
            }

            // Create item in group2
            client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createChecklistItem(input: { title: \"Group 2 Task\", groupId: \"$group2Id\" }) { id } }"
                    }
                """.trimIndent())
            }

            // Query items by group1
            val queryResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "{ checklistItemsByGroup(groupId: \"$group1Id\") { id title groupId } }"
                    }
                """.trimIndent())
            }

            queryResponse.status shouldBe HttpStatusCode.OK
            val body = queryResponse.bodyAsText()
            println("checklistItemsByGroup response: $body")

            body shouldContain "Group 1 Task"
            // Should NOT contain Group 2 task
            body.contains("Group 2 Task") shouldBe false
        }
    }

    test("updateChecklistItem mutation should update item") {
        user1Token shouldNotBe null
        user1Id shouldNotBe null

        testWithApp {
            // Create group and item
            val groupResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Update Test Group\" }) { id } }"
                    }
                """.trimIndent())
            }
            val groupBody = groupResponse.bodyAsText()
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(groupBody)
            val groupId = groupIdMatch?.groupValues?.get(1)

            val createResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createChecklistItem(input: { title: \"Original Title\", groupId: \"$groupId\" }) { id title completed } }"
                    }
                """.trimIndent())
            }
            val createBody = createResponse.bodyAsText()
            val itemIdMatch = Regex(""""id":\s*"([^"]+)"""").find(createBody)
            val itemId = itemIdMatch?.groupValues?.get(1)
            itemId shouldNotBe null

            // Update the item
            val updateResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { updateChecklistItem(input: { id: \"$itemId\", title: \"Updated Title\", completed: true }) { id title completed updatedAt } }"
                    }
                """.trimIndent())
            }

            updateResponse.status shouldBe HttpStatusCode.OK
            val body = updateResponse.bodyAsText()
            println("updateChecklistItem response: $body")

            body shouldContainJsonKey "data.updateChecklistItem"
            body shouldContain "Updated Title"
            body shouldContain "\"completed\":true"
        }
    }

    test("deleteChecklistItem mutation should delete item") {
        user1Token shouldNotBe null
        user1Id shouldNotBe null

        testWithApp {
            // Create group and item
            val groupResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Delete Test Group\" }) { id } }"
                    }
                """.trimIndent())
            }
            val groupBody = groupResponse.bodyAsText()
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(groupBody)
            val groupId = groupIdMatch?.groupValues?.get(1)

            val createResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createChecklistItem(input: { title: \"To Delete\", groupId: \"$groupId\" }) { id } }"
                    }
                """.trimIndent())
            }
            val createBody = createResponse.bodyAsText()
            val itemIdMatch = Regex(""""id":\s*"([^"]+)"""").find(createBody)
            val itemId = itemIdMatch?.groupValues?.get(1)
            itemId shouldNotBe null

            // Delete the item
            val deleteResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { deleteChecklistItem(input: { id: \"$itemId\" }) }"
                    }
                """.trimIndent())
            }

            deleteResponse.status shouldBe HttpStatusCode.OK
            val body = deleteResponse.bodyAsText()
            println("deleteChecklistItem response: $body")

            body shouldContainJsonKey "data.deleteChecklistItem"
        }
    }

    test("checklistItems field resolver should return group items") {
        user1Token shouldNotBe null
        user1Id shouldNotBe null

        testWithApp {
            // Create group
            val groupResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Field Resolver Test\" }) { id } }"
                    }
                """.trimIndent())
            }
            val groupBody = groupResponse.bodyAsText()
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(groupBody)
            val groupId = groupIdMatch?.groupValues?.get(1)

            // Create items
            client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createChecklistItem(input: { title: \"Item 1\", groupId: \"$groupId\" }) { id } }"
                    }
                """.trimIndent())
            }

            client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createChecklistItem(input: { title: \"Item 2\", groupId: \"$groupId\" }) { id } }"
                    }
                """.trimIndent())
            }

            // Query group with checklistItems field
            val queryResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "{ checkboxGroup(id: \"$groupId\") { id name checklistItems { id title completed } } }"
                    }
                """.trimIndent())
            }

            queryResponse.status shouldBe HttpStatusCode.OK
            val body = queryResponse.bodyAsText()
            println("checklistItems field resolver response: $body")

            body shouldContainJsonKey "data.checkboxGroup.checklistItems"
            body shouldContain "Item 1"
            body shouldContain "Item 2"
        }
    }

    test("group member can access items") {
        user1Token shouldNotBe null
        user2Token shouldNotBe null
        user1Id shouldNotBe null
        user2Id shouldNotBe null

        testWithApp {
            // User1 creates group
            val groupResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Shared Group\" }) { id } }"
                    }
                """.trimIndent())
            }
            val groupBody = groupResponse.bodyAsText()
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(groupBody)
            val groupId = groupIdMatch?.groupValues?.get(1)

            // User1 adds user2 as member
            client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { addGroupMember(input: { groupId: \"$groupId\", userId: \"$user2Id\" }) { id } }"
                    }
                """.trimIndent())
            }

            // User1 creates item
            val createResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createChecklistItem(input: { title: \"Shared Task\", groupId: \"$groupId\" }) { id } }"
                    }
                """.trimIndent())
            }
            val createBody = createResponse.bodyAsText()
            val itemIdMatch = Regex(""""id":\s*"([^"]+)"""").find(createBody)
            val itemId = itemIdMatch?.groupValues?.get(1)

            // User2 (member) queries items
            val queryResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user2Token")
                setBody("""
                    {
                        "query": "{ checklistItemsByGroup(groupId: \"$groupId\") { id title } }"
                    }
                """.trimIndent())
            }

            queryResponse.status shouldBe HttpStatusCode.OK
            val body = queryResponse.bodyAsText()
            println("Member access response: $body")

            body shouldContain "Shared Task"
        }
    }

    test("non-member cannot access items") {
        user1Token shouldNotBe null
        outsiderToken shouldNotBe null
        user1Id shouldNotBe null

        testWithApp {
            // User1 creates group
            val groupResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createCheckboxGroup(input: { name: \"Private Group\" }) { id } }"
                    }
                """.trimIndent())
            }
            val groupBody = groupResponse.bodyAsText()
            val groupIdMatch = Regex(""""id":\s*"([^"]+)"""").find(groupBody)
            val groupId = groupIdMatch?.groupValues?.get(1)

            // User1 creates item
            client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $user1Token")
                setBody("""
                    {
                        "query": "mutation { createChecklistItem(input: { title: \"Private Task\", groupId: \"$groupId\" }) { id } }"
                    }
                """.trimIndent())
            }

            // Outsider (not a member) tries to query items
            val queryResponse = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                setBody("""
                    {
                        "query": "{ checklistItemsByGroup(groupId: \"$groupId\") { id title } }"
                    }
                """.trimIndent())
            }

            val body = queryResponse.bodyAsText()
            println("Outsider access response: $body")

            // Should return an error due to Viaduct policy check
            // The outsider is not a member of the group, so access is denied
            body shouldContain "error"
            body shouldContain "Access denied"
        }
    }

    afterSpec {
        runBlocking {
            try {
                user1Client.auth.signOut()
                user2Client.auth.signOut()
                outsiderClient.auth.signOut()
                println("All test users signed out")
            } catch (e: Exception) {
                println("Failed to sign out: ${e.message}")
            }
        }
    }
})
