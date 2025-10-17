package com.graphqlcheckmate.policy

import com.graphqlcheckmate.GraphQLRequestContext
import com.graphqlcheckmate.services.GroupService
import viaduct.engine.api.CheckerExecutor
import viaduct.engine.api.CheckerResult
import viaduct.engine.api.EngineExecutionContext
import viaduct.engine.api.EngineObjectData
import viaduct.engine.api.RequiredSelectionSet

/**
 * Policy executor that checks if a user is a member of a checkbox group.
 * This demonstrates Viaduct's per-row policy check capabilities.
 *
 * The executor reads the group ID from the object data and verifies that
 * the authenticated user is a member of that group.
 */
class GroupMembershipPolicyExecutor(
    private val groupIdFieldName: String,
    private val groupService: GroupService
) : CheckerExecutor {

    /**
     * Specify which fields must be selected for the policy check to work.
     * For simplicity, we're not requiring selection sets in this implementation.
     * The group ID will be extracted from object data if available.
     */
    override val requiredSelectionSets: Map<String, RequiredSelectionSet?> = emptyMap()

    override suspend fun execute(
        arguments: Map<String, Any?>,
        objectDataMap: Map<String, EngineObjectData>,
        context: EngineExecutionContext
    ): CheckerResult {
        // Extract the request context containing user information
        val requestContext = context.requestContext as? GraphQLRequestContext
            ?: return GroupMembershipErrorResult(
                RuntimeException("Authentication required: request context not found")
            )

        val userId = requestContext.userId

        // Get the object data (empty string key means current object)
        val objectData = objectDataMap[""]

        // If there's no object data, we're checking a field-level policy
        // In this case, check if the groupId is provided as an argument
        if (objectData == null) {
            val groupIdArg = arguments["groupId"] as? String
            if (groupIdArg != null) {
                // Check membership using the argument
                return checkGroupMembership(userId, groupIdArg)
            }
            // No group ID available - allow access (this might be a query that returns all groups)
            return CheckerResult.Success
        }

        // Extract the group ID from the object data
        val groupId = try {
            objectData.fetch(groupIdFieldName) as? String
        } catch (e: Exception) {
            // Field not found or null - this might be a legacy item without a group
            null
        }

        // If group ID is null, allow access (backward compatibility for personal items)
        if (groupId == null) {
            return CheckerResult.Success
        }

        // Check if the user is a member of the group
        return checkGroupMembership(userId, groupId)
    }

    private suspend fun checkGroupMembership(userId: String, groupId: String): CheckerResult {
        val isMember = try {
            groupService.isUserMemberOfGroup(userId, groupId)
        } catch (e: Exception) {
            return GroupMembershipErrorResult(
                RuntimeException("Failed to check group membership: ${e.message}", e)
            )
        }

        return if (isMember) {
            CheckerResult.Success
        } else {
            GroupMembershipErrorResult(
                RuntimeException("Access denied: You are not a member of this group")
            )
        }
    }
}

/**
 * Error result for group membership policy check failures.
 */
class GroupMembershipErrorResult(
    override val error: Exception
) : CheckerResult.Error {

    /**
     * Should this error be returned to the resolver?
     * Yes - we want the resolver to know about permission failures.
     */
    override fun isErrorForResolver(ctx: viaduct.engine.api.CheckerResultContext): Boolean = true

    /**
     * How to combine field and type errors when both fail.
     * Field error takes precedence.
     */
    override fun combine(fieldResult: CheckerResult.Error): CheckerResult.Error {
        return fieldResult
    }
}
