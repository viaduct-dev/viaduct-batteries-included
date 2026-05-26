package com.example.sync

import com.pulumi.test.Mocks
import com.pulumi.test.PulumiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

private const val REPO = "acme/api"
private const val ASSET_ID = "test-asset-uuid"

private val COLLAB_TYPE = "github:index/repositoryCollaborator:RepositoryCollaborator"

class GitHubRepoStackProgramTest : FunSpec({

    afterEach { PulumiTest.cleanup() }

    test("single group member gets the declared permission") {
        val db = FakeStackDb().apply {
            policy(ASSET_ID, "eng", "WRITE")
            member("eng", "user-alice")
            githubIdentity("user-alice", "alice")
        }
        val mocks = CapturingMocks()

        PulumiTest.withMocks(mocks).runTest(githubRepoStackProgram(REPO, ASSET_ID, db))

        val collabs = mocks.collaborators()
        collabs.size shouldBe 1
        collabs.single().run {
            inputs["username"] shouldBe "alice"
            inputs["permission"] shouldBe "push"
            inputs["repository"] shouldBe REPO
        }
    }

    test("highest permission wins when two groups cover the same user") {
        val db = FakeStackDb().apply {
            policy(ASSET_ID, "eng", "WRITE")
            policy(ASSET_ID, "on-call", "ADMIN")
            member("eng", "user-alice")
            member("on-call", "user-alice")
            githubIdentity("user-alice", "alice")
        }
        val mocks = CapturingMocks()

        PulumiTest.withMocks(mocks).runTest(githubRepoStackProgram(REPO, ASSET_ID, db))

        val collabs = mocks.collaborators()
        collabs.size shouldBe 1
        collabs.single().inputs["permission"] shouldBe "admin"
    }

    test("members without a github identity are silently skipped") {
        val db = FakeStackDb().apply {
            policy(ASSET_ID, "eng", "WRITE")
            member("eng", "user-alice")
            member("eng", "user-bob")   // no github identity
            githubIdentity("user-alice", "alice")
        }
        val mocks = CapturingMocks()

        PulumiTest.withMocks(mocks).runTest(githubRepoStackProgram(REPO, ASSET_ID, db))

        val collabs = mocks.collaborators()
        collabs.size shouldBe 1
        collabs.single().inputs["username"] shouldBe "alice"
    }

    test("multiple members in the same group each get a collaborator resource") {
        val db = FakeStackDb().apply {
            policy(ASSET_ID, "eng", "READ")
            member("eng", "user-alice")
            member("eng", "user-bob")
            githubIdentity("user-alice", "alice")
            githubIdentity("user-bob", "bob")
        }
        val mocks = CapturingMocks()

        PulumiTest.withMocks(mocks).runTest(githubRepoStackProgram(REPO, ASSET_ID, db))

        val usernames = mocks.collaborators().map { it.inputs["username"] as String }
        usernames shouldContainExactlyInAnyOrder listOf("alice", "bob")
    }

    test("no policies produces an empty stack") {
        val db = FakeStackDb()
        val mocks = CapturingMocks()

        PulumiTest.withMocks(mocks).runTest(githubRepoStackProgram(REPO, ASSET_ID, db))

        mocks.collaborators().shouldBeEmpty()
    }

    test("permission enum maps to correct github strings") {
        mapOf(
            "READ" to "pull",
            "TRIAGE" to "triage",
            "WRITE" to "push",
            "MAINTAIN" to "maintain",
            "ADMIN" to "admin",
        ).forEach { (viaAccess, github) ->
            val db = FakeStackDb().apply {
                policy(ASSET_ID, "grp", viaAccess)
                member("grp", "user-1")
                githubIdentity("user-1", "gh-user")
            }
            val mocks = CapturingMocks()
            PulumiTest.withMocks(mocks).runTest(githubRepoStackProgram(REPO, ASSET_ID, db))
            PulumiTest.cleanup()

            mocks.collaborators().single().inputs["permission"] shouldBe github
        }
    }
})

data class CapturedResource(val type: String, val name: String, val inputs: Map<String, Any>)

/** Captures every resource created by the stack program. */
private class CapturingMocks : Mocks {
    private val resources = mutableListOf<CapturedResource>()

    fun collaborators() = resources.filter { it.type == COLLAB_TYPE }

    override fun newResourceAsync(args: Mocks.ResourceArgs): java.util.concurrent.CompletableFuture<Mocks.ResourceResult> {
        val inputs = args.inputs ?: emptyMap()
        resources += CapturedResource(args.type ?: "", args.name ?: "", inputs)
        return java.util.concurrent.CompletableFuture.completedFuture(
            Mocks.ResourceResult.of(
                java.util.Optional.of("${args.name}-id"),
                inputs + mapOf("id" to "${args.name}-id")
            )
        )
    }

    override fun callAsync(args: Mocks.CallArgs) =
        java.util.concurrent.CompletableFuture.completedFuture(emptyMap<String, Any>())
}
