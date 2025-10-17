package com.graphqlcheckmate.policy

import com.graphqlcheckmate.AuthenticatedSupabaseClient
import com.graphqlcheckmate.GraphQLRequestContext
import com.graphqlcheckmate.SupabaseService
import com.graphqlcheckmate.services.GroupService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import viaduct.engine.api.CheckerResult
import viaduct.engine.api.EngineExecutionContext
import viaduct.engine.api.EngineObjectData

/**
 * Fake GroupService implementation for testing.
 * Avoids Java 21 / Byte Buddy compatibility issues with MockK.
 */
class FakeGroupService : GroupService(mockk<SupabaseService>(relaxed = true)) {
    private val memberships = mutableMapOf<Pair<String, String>, Boolean>()

    fun setMembership(userId: String, groupId: String, isMember: Boolean) {
        memberships[Pair(userId, groupId)] = isMember
    }

    override suspend fun isUserMemberOfGroup(userId: String, groupId: String): Boolean {
        return memberships[Pair(userId, groupId)] ?: false
    }
}

/**
 * Integration test for GroupMembershipPolicyExecutor.
 *
 * This test demonstrates Viaduct's per-row policy check feature by testing:
 * 1. Access granted when user is a member of the group
 * 2. Access denied when user is not a member of the group
 * 3. Access granted when group ID is null (backward compatibility)
 * 4. Proper error messages on access denial
 */
class GroupMembershipPolicyCheckTest : FunSpec({

    val testUserId = "user-123"
    val testGroupId = "group-456"
    val anotherGroupId = "group-789"

    lateinit var groupService: FakeGroupService
    lateinit var policyExecutor: GroupMembershipPolicyExecutor
    lateinit var mockContext: EngineExecutionContext

    beforeEach {
        // Use fake GroupService to simulate group memberships
        groupService = FakeGroupService()

        // User is a member of testGroupId but not anotherGroupId
        groupService.setMembership(testUserId, testGroupId, true)
        groupService.setMembership(testUserId, anotherGroupId, false)

        // Create policy executor
        policyExecutor = GroupMembershipPolicyExecutor(
            groupIdFieldName = "groupId",
            groupService = groupService
        )

        // Mock execution context with user ID
        mockContext = mockk<EngineExecutionContext>(relaxed = true)
        every { mockContext.requestContext } returns GraphQLRequestContext(
            userId = testUserId,
            accessToken = "test-token",
            isAdmin = false
        )
    }

    test("should grant access when user is a member of the group") {
        // Arrange: Create object data with group ID that user is a member of
        val objectData = mockk<EngineObjectData>()
        every { runBlocking { objectData.fetch("groupId") } } returns testGroupId

        val objectDataMap = mapOf("" to objectData)
        val arguments = emptyMap<String, Any?>()

        // Act: Execute policy check
        val result = policyExecutor.execute(
            arguments = arguments,
            objectDataMap = objectDataMap,
            context = mockContext
        )

        // Assert: Access should be granted
        result shouldBe CheckerResult.Success
    }

    test("should deny access when user is not a member of the group") {
        // Arrange: Create object data with group ID that user is NOT a member of
        val objectData = mockk<EngineObjectData>()
        every { runBlocking { objectData.fetch("groupId") } } returns anotherGroupId

        val objectDataMap = mapOf("" to objectData)
        val arguments = emptyMap<String, Any?>()

        // Act: Execute policy check
        val result = policyExecutor.execute(
            arguments = arguments,
            objectDataMap = objectDataMap,
            context = mockContext
        )

        // Assert: Access should be denied with error
        result shouldNotBe CheckerResult.Success
        result as GroupMembershipErrorResult
        result.error.message shouldContain "not a member of this group"
    }

    test("should grant access when group ID is null (backward compatibility)") {
        // Arrange: Create object data with null group ID (legacy personal items)
        val objectData = mockk<EngineObjectData>()
        every { runBlocking { objectData.fetch("groupId") } } returns null

        val objectDataMap = mapOf("" to objectData)
        val arguments = emptyMap<String, Any?>()

        // Act: Execute policy check
        val result = policyExecutor.execute(
            arguments = arguments,
            objectDataMap = objectDataMap,
            context = mockContext
        )

        // Assert: Access should be granted for null group ID
        result shouldBe CheckerResult.Success
    }

    test("should check membership from arguments when object data is null") {
        // Arrange: No object data, but group ID provided as argument
        val objectDataMap = emptyMap<String, EngineObjectData>()
        val arguments = mapOf("groupId" to testGroupId)

        // Act: Execute policy check
        val result = policyExecutor.execute(
            arguments = arguments,
            objectDataMap = objectDataMap,
            context = mockContext
        )

        // Assert: Access should be granted (user is member of testGroupId)
        result shouldBe CheckerResult.Success
    }

    test("should deny access from arguments when user is not a member") {
        // Arrange: No object data, but group ID provided as argument for non-member group
        val objectDataMap = emptyMap<String, EngineObjectData>()
        val arguments = mapOf("groupId" to anotherGroupId)

        // Act: Execute policy check
        val result = policyExecutor.execute(
            arguments = arguments,
            objectDataMap = objectDataMap,
            context = mockContext
        )

        // Assert: Access should be denied
        result shouldNotBe CheckerResult.Success
        result as GroupMembershipErrorResult
        result.error.message shouldContain "not a member of this group"
    }

    test("should grant access when no group ID is provided anywhere") {
        // Arrange: No object data and no group ID argument
        val objectDataMap = emptyMap<String, EngineObjectData>()
        val arguments = emptyMap<String, Any?>()

        // Act: Execute policy check
        val result = policyExecutor.execute(
            arguments = arguments,
            objectDataMap = objectDataMap,
            context = mockContext
        )

        // Assert: Access should be granted (no group to check)
        result shouldBe CheckerResult.Success
    }

    test("error result should indicate it's for the resolver") {
        // Arrange: Create an error result
        val errorResult = GroupMembershipErrorResult(
            RuntimeException("Test error")
        )

        val mockResultContext = mockk<viaduct.engine.api.CheckerResultContext>()

        // Assert: Error should be returned to resolver
        errorResult.isErrorForResolver(mockResultContext) shouldBe true
    }

    test("error result combine should prefer field error") {
        // Arrange: Create two error results
        val typeError = GroupMembershipErrorResult(
            RuntimeException("Type error")
        )
        val fieldError = GroupMembershipErrorResult(
            RuntimeException("Field error")
        )

        // Act: Combine errors
        val combinedResult = typeError.combine(fieldError)

        // Assert: Field error should take precedence
        combinedResult shouldBe fieldError
        (combinedResult as GroupMembershipErrorResult).error.message shouldBe "Field error"
    }
})
