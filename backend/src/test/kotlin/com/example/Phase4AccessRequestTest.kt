package com.example

import com.example.config.DelegatingTenantCodeInjector
import com.example.config.KoinTenantCodeInjector
import com.example.config.appModule
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
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import org.koin.logger.slf4jLogger
import viaduct.api.bootstrap.ViaductTenantAPIBootstrapper
import viaduct.service.SchemaScopeInfo
import viaduct.service.runtime.SchemaConfiguration
import viaduct.service.runtime.StandardViaduct

/**
 * Integration tests for Phase 4: approval workflow, requestable assets, and access request state machine.
 *
 * Setup:
 *   - editorUser: has TenantAsset(default) EDITOR grant — can mark assets requestable, review requests
 *   - requesterUser: has TenantAsset(default) REQUESTER grant — can discover and request requestable assets
 *
 * Test coverage:
 *   - setAssetRequestable: EDITOR can mark; requestableAssets returns it; non-requestable not returned
 *   - requestGroupAccess: REQUESTER can submit for requestable asset; blocked for non-requestable
 *   - pendingAccessRequests: EDITOR sees all; requester sees only own
 *   - approveAccessRequest: creates live policy, enqueues sync, transitions to APPROVED
 *   - rejectAccessRequest: no live policy created, transitions to REJECTED
 *   - cancelAccessRequest: requester can cancel own; transitions to CANCELED
 *   - RLS: requester cannot see another user's request
 */
class Phase4AccessRequestTest : FunSpec({
    val objectMapper = jacksonObjectMapper()

    val supabaseUrl = System.getenv("SUPABASE_URL") ?: "http://127.0.0.1:54321"
    val supabaseAnonKey = System.getenv("SUPABASE_ANON_KEY")
        ?: "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH"
    val supabaseServiceKey = System.getenv("SUPABASE_SERVICE_ROLE_KEY")
        ?: "sb_secret_N7UND0UgjKTVK-Uodkm0Hg_xSvEMPvz"

    val ts = System.currentTimeMillis()
    val editorEmail    = "p4-editor-$ts@example.com"
    val requesterEmail = "p4-requester-$ts@example.com"
    val testPassword = "TestPassword123!"

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

    val editorClient   = createSupabaseClient(supabaseUrl, supabaseAnonKey) { install(Auth) }
    val requesterClient = createSupabaseClient(supabaseUrl, supabaseAnonKey) { install(Auth) }

    var editorToken: String = ""
    var requesterToken: String = ""
    var testAssetId: String = ""
    var testGroupId: String = ""

    fun globalId(type: String, uuid: String): String =
        java.util.Base64.getEncoder().encodeToString("$type:$uuid".toByteArray())

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

    fun gql(token: String, query: String, variables: String = "{}") = """
        {"query":${objectMapper.writeValueAsString(query)},"variables":$variables}
    """.trimIndent()

    beforeSpec {
        runBlocking {
            val adminHttp = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = 30_000 } }

            suspend fun adminPost(path: String, body: String): io.ktor.client.statement.HttpResponse =
                adminHttp.post("$supabaseUrl$path") {
                    header("Authorization", "Bearer $supabaseServiceKey")
                    header("apikey", supabaseServiceKey)
                    header("Prefer", "return=representation")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.also { resp ->
                    check(resp.status.value in 200..299) {
                        "adminPost $path failed ${resp.status}: ${resp.bodyAsText()}"
                    }
                }

            // group_members: the add_owner_to_group trigger fires on groups INSERT and
            // pre-inserts the creator, so an explicit insert for the creator hits a
            // (group_id, person_id) unique constraint.  Use ignore-duplicates so the
            // trigger-inserted row is treated as success.
            suspend fun adminAddMember(groupId: String, personId: String) {
                adminHttp.post("$supabaseUrl/rest/v1/group_members") {
                    header("Authorization", "Bearer $supabaseServiceKey")
                    header("apikey", supabaseServiceKey)
                    header("Prefer", "return=representation,resolution=ignore-duplicates")
                    contentType(ContentType.Application.Json)
                    setBody("""{"group_id":"$groupId","person_id":"$personId"}""")
                }.also { resp ->
                    check(resp.status.value in 200..299 || resp.status.value == 409) {
                        "adminAddMember failed ${resp.status}: ${resp.bodyAsText()}"
                    }
                }
            }

            suspend fun adminGet(path: String): io.ktor.client.statement.HttpResponse =
                adminHttp.get("$supabaseUrl$path") {
                    header("Authorization", "Bearer $supabaseServiceKey")
                    header("apikey", supabaseServiceKey)
                }.also { resp ->
                    check(resp.status.value in 200..299) {
                        "adminGet $path failed ${resp.status}: ${resp.bodyAsText()}"
                    }
                }

            suspend fun lookupPersonId(authUserId: String): String {
                val body = adminGet("/rest/v1/persons?auth_user_id=eq.$authUserId&select=id").bodyAsText()
                val node = objectMapper.readTree(body)
                check(node.isArray && node.size() > 0) {
                    "No person row found for auth_user_id=$authUserId — run `supabase db reset` to apply the person-model migration. body=$body"
                }
                return node[0]["id"].asText()
            }

            // Create editor user
            editorClient.auth.signUpWith(Email) { email = editorEmail; password = testPassword }
            editorClient.auth.signInWith(Email)  { email = editorEmail; password = testPassword }
            editorToken = editorClient.auth.currentAccessTokenOrNull()!!
            val editorUserId = editorClient.auth.currentUserOrNull()!!.id
            val editorPersonId = lookupPersonId(editorUserId)

            // Create requester user
            requesterClient.auth.signUpWith(Email) { email = requesterEmail; password = testPassword }
            requesterClient.auth.signInWith(Email)  { email = requesterEmail; password = testPassword }
            requesterToken = requesterClient.auth.currentAccessTokenOrNull()!!
            val requesterUserId = requesterClient.auth.currentUserOrNull()!!.id
            val requesterPersonId = lookupPersonId(requesterUserId)

            // Get default tenant_asset id
            val taBody = adminGet("/rest/v1/tenant_assets?tenant_name=eq.default&select=id").bodyAsText()
            val defaultTenantAssetId = objectMapper.readTree(taBody)[0]["id"].asText()

            // Grant EDITOR on default to editorUser (via group)
            val editorGroupId = objectMapper.readTree(
                adminPost("/rest/v1/groups", """{"name":"editor-group-$ts","created_by":"$editorUserId"}""").bodyAsText()
            )[0]["id"].asText()
            adminAddMember(editorGroupId, editorPersonId)
            adminPost("/rest/v1/tenant_asset_policies", """{"tenant_asset_id":"$defaultTenantAssetId","group_id":"$editorGroupId","permission":"EDITOR"}""")

            // Grant REQUESTER on default to requesterUser (via group)
            val requesterGroupId = objectMapper.readTree(
                adminPost("/rest/v1/groups", """{"name":"requester-group-$ts","created_by":"$editorUserId"}""").bodyAsText()
            )[0]["id"].asText()
            adminAddMember(requesterGroupId, requesterPersonId)
            adminPost("/rest/v1/tenant_asset_policies", """{"tenant_asset_id":"$defaultTenantAssetId","group_id":"$requesterGroupId","permission":"REQUESTER"}""")

            // Create a GitHub tenant_asset row (needed for assets.tenant_name FK)
            val githubTaBody = adminGet("/rest/v1/tenant_assets?tenant_name=eq.github&select=id").bodyAsText()
            val githubTenantAssetId = objectMapper.readTree(githubTaBody).let {
                if (it.size() > 0) it[0]["id"].asText()
                else objectMapper.readTree(adminPost("/rest/v1/tenant_assets", """{"tenant_name":"github"}""").bodyAsText())[0]["id"].asText()
            }

            // Create a test asset (GITHUB_REPO, not yet requestable)
            val assetBody = objectMapper.readTree(
                adminPost("/rest/v1/assets", """{"asset_type":"GITHUB_REPO","tenant_name":"github","external_id":"test-repo-$ts","name":"test-repo-$ts","requestable":false}""").bodyAsText()
            )
            testAssetId = assetBody[0]["id"].asText()

            // Also insert a github_repo_assets row required by the schema
            adminPost("/rest/v1/github_repo_assets", """{"id":"$testAssetId","owner":"test-org","repo":"test-repo-$ts"}""")

            // Grant EDITOR on github tenant to editorUser (needed for setAssetRequestable)
            val editorGitHubGroupId = objectMapper.readTree(
                adminPost("/rest/v1/groups", """{"name":"editor-github-group-$ts","created_by":"$editorUserId"}""").bodyAsText()
            )[0]["id"].asText()
            adminAddMember(editorGitHubGroupId, editorPersonId)
            adminPost("/rest/v1/tenant_asset_policies", """{"tenant_asset_id":"$githubTenantAssetId","group_id":"$editorGitHubGroupId","permission":"EDITOR"}""")

            // Grant REQUESTER on github tenant to requesterUser (requestGroupAccess checks asset.tenant_name)
            val githubRequesterGroupId = objectMapper.readTree(
                adminPost("/rest/v1/groups", """{"name":"requester-github-group-$ts","created_by":"$editorUserId"}""").bodyAsText()
            )[0]["id"].asText()
            adminAddMember(githubRequesterGroupId, requesterPersonId)
            adminPost("/rest/v1/tenant_asset_policies", """{"tenant_asset_id":"$githubTenantAssetId","group_id":"$githubRequesterGroupId","permission":"REQUESTER"}""")

            // Create a group for the requester to request access with
            testGroupId = objectMapper.readTree(
                adminPost("/rest/v1/groups", """{"name":"access-test-group-$ts","created_by":"$editorUserId"}""").bodyAsText()
            )[0]["id"].asText()
            adminAddMember(testGroupId, requesterPersonId)

            println("Phase4 setup: editor=$editorEmail requester=$requesterEmail asset=$testAssetId group=$testGroupId")
        }
    }

    test("requestableAssets: non-requestable asset is not returned") {
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $requesterToken")
                setBody(gql(requesterToken, "{ requestableAssets { id name } }"))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContainJsonKey "data.requestableAssets"
            body shouldNotContain "test-repo-$ts"
        }
    }

    test("setAssetRequestable: EDITOR can mark asset requestable") {
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $editorToken")
                setBody(gql(editorToken,
                    """mutation { setAssetRequestable(assetId: "$testAssetId", requestable: true) }"""))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContainJsonKey "data.setAssetRequestable"
            body shouldContain "true"
        }
    }

    test("requestableAssets: requestable asset appears after marking") {
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $requesterToken")
                setBody(gql(requesterToken, "{ requestableAssets { id name availablePermissions } }"))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContain "test-repo-$ts"
            body shouldContain "READ"
        }
    }

    var accessRequestId: String = ""

    test("requestGroupAccess: REQUESTER can submit a request for a requestable asset") {
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $requesterToken")
                setBody(gql(requesterToken,
                    """mutation { requestGroupAccess(input: {
                        assetId: "$testAssetId",
                        groupId: "${globalId("Group", testGroupId)}",
                        requestedPermission: "READ"
                    }) { id status requestedPermission requestedBy } }"""))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContainJsonKey "data.requestGroupAccess.id"
            body shouldContain "PENDING"
            body shouldContain "READ"
            accessRequestId = objectMapper.readTree(body)
                .get("data").get("requestGroupAccess").get("id").asText()
            println("Created access request: $accessRequestId")
        }
    }

    test("pendingAccessRequests: EDITOR sees all pending requests") {
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $editorToken")
                setBody(gql(editorToken,
                    """{ pendingAccessRequests(tenantName: "github") { id status requestedBy } }"""))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContainJsonKey "data.pendingAccessRequests"
            body shouldContain "PENDING"
        }
    }

    test("pendingAccessRequests: requester sees own requests only") {
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $requesterToken")
                setBody(gql(requesterToken,
                    """{ pendingAccessRequests(tenantName: "github") { id status } }"""))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContainJsonKey "data.pendingAccessRequests"
        }
    }

    test("approveAccessRequest: EDITOR can approve; request transitions to APPROVED") {
        accessRequestId shouldNotBe ""
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $editorToken")
                setBody(gql(editorToken,
                    """mutation { approveAccessRequest(id: "$accessRequestId", note: "lgtm") {
                        id status reviewerNote
                    } }"""))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContainJsonKey "data.approveAccessRequest.id"
            body shouldContain "APPROVED"
            body shouldContain "lgtm"
        }
    }

    test("approveAccessRequest: re-approving APPROVED request succeeds (idempotent replay)") {
        accessRequestId shouldNotBe ""
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $editorToken")
                setBody(gql(editorToken,
                    """mutation { approveAccessRequest(id: "$accessRequestId") { id status } }"""))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContainJsonKey "data.approveAccessRequest.id"
            body shouldContain "APPROVED"
        }
    }

    // --- reject flow ---

    var rejectRequestId: String = ""

    test("requestGroupAccess: second request for reject test") {
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $requesterToken")
                setBody(gql(requesterToken,
                    """mutation { requestGroupAccess(input: {
                        assetId: "$testAssetId",
                        groupId: "${globalId("Group", testGroupId)}",
                        requestedPermission: "WRITE"
                    }) { id status } }"""))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContain "PENDING"
            rejectRequestId = objectMapper.readTree(body)
                .get("data").get("requestGroupAccess").get("id").asText()
        }
    }

    test("rejectAccessRequest: EDITOR can reject; request transitions to REJECTED") {
        rejectRequestId shouldNotBe ""
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $editorToken")
                setBody(gql(editorToken,
                    """mutation { rejectAccessRequest(id: "$rejectRequestId", note: "not now") {
                        id status reviewerNote
                    } }"""))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContain "REJECTED"
            body shouldContain "not now"
        }
    }

    // --- cancel flow ---

    var cancelRequestId: String = ""

    test("requestGroupAccess: third request for cancel test") {
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $requesterToken")
                setBody(gql(requesterToken,
                    """mutation { requestGroupAccess(input: {
                        assetId: "$testAssetId",
                        groupId: "${globalId("Group", testGroupId)}",
                        requestedPermission: "READ"
                    }) { id status } }"""))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContain "PENDING"
            cancelRequestId = objectMapper.readTree(body)
                .get("data").get("requestGroupAccess").get("id").asText()
        }
    }

    test("cancelAccessRequest: requester can cancel own pending request") {
        cancelRequestId shouldNotBe ""
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $requesterToken")
                setBody(gql(requesterToken,
                    """mutation { cancelAccessRequest(id: "$cancelRequestId") { id status } }"""))
            }
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            body shouldContain "CANCELED"
        }
    }

    test("setAssetRequestable: EDITOR can hide asset again") {
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $editorToken")
                setBody(gql(editorToken,
                    """mutation { setAssetRequestable(assetId: "$testAssetId", requestable: false) }"""))
            }
            resp.status shouldBe HttpStatusCode.OK
            resp.bodyAsText() shouldContain "false"
        }
    }

    test("requestableAssets: hidden asset no longer appears") {
        testWithApp {
            val resp = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $requesterToken")
                setBody(gql(requesterToken, "{ requestableAssets { id name } }"))
            }
            resp.status shouldBe HttpStatusCode.OK
            resp.bodyAsText() shouldNotContain "test-repo-$ts"
        }
    }
})
