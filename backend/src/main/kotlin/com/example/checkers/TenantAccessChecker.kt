package com.example.checkers

import com.example.config.RequestContext
import com.example.services.ViaAccessService
import viaduct.engine.api.CheckerResult
import viaduct.engine.api.CheckerResultContext
import viaduct.engine.api.EngineExecutionContext
import viaduct.engine.api.EngineObjectData
import viaduct.engine.api.RequiredSelectionSet
import viaduct.engine.api.ViaductSchema
import viaduct.engine.api.spi.CheckerExecutor
import viaduct.engine.api.spi.CheckerExecutorFactory

private val GITHUB_TYPES = setOf(
    "GitHubRepoAsset", "GitHubTeamAsset", "GitHubRepoPolicy", "GitHubTeamPolicy",
    "ProviderUser", "IdentityImportSummary",
)

private val ASANA_TYPES = setOf(
    "AsanaProjectAsset", "AsanaPortfolioAsset", "AsanaProjectPolicy", "AsanaPortfolioPolicy",
    "AsanaProviderUser", "AsanaIdentityImportSummary",
)

private val TYPE_TO_TENANT: Map<String, String> =
    GITHUB_TYPES.associateWith { "github" } + ASANA_TYPES.associateWith { "asana" }

/**
 * Enforces that the requesting user has a TenantAssetPolicy granting them access
 * to the given tenant (github or asana) before resolving any type in that tenant.
 *
 * Registered per-type for every github/asana type. isAdmin bypasses the check.
 */
class TenantAccessCheckerExecutor(
    private val tenantName: String,
    private val viaAccessService: ViaAccessService,
) : CheckerExecutor {

    override val requiredSelectionSets: Map<String, RequiredSelectionSet?> = emptyMap()

    override suspend fun execute(
        arguments: Map<String, Any?>,
        objectDataMap: Map<String, EngineObjectData>,
        context: EngineExecutionContext,
        checkerType: CheckerExecutor.CheckerType,
    ): CheckerResult {
        val requestContext = context.requestContext as? RequestContext
            ?: return TenantAccessError("No request context available")

        if (requestContext.graphQLContext.isAdmin) return CheckerResult.Success

        val userId = requestContext.graphQLContext.userId
        val hasAccess = requestContext.authenticatedClient.userHasTenantAccess(userId, tenantName)

        return if (hasAccess) CheckerResult.Success
        else TenantAccessError("User does not have access to the $tenantName tenant")
    }
}

class TenantAccessError(message: String) : CheckerResult.Error {
    override val error: Exception = Exception(message)
    override fun isErrorForResolver(ctx: CheckerResultContext): Boolean = true
    override fun combine(fieldResult: CheckerResult.Error): CheckerResult.Error = this
}

class TenantAccessCheckerExecutorFactory(
    private val viaAccessService: ViaAccessService,
) : CheckerExecutorFactory {

    override fun checkerExecutorForField(
        schema: ViaductSchema,
        typeName: String,
        fieldName: String,
    ): CheckerExecutor? = null

    override fun checkerExecutorForType(
        schema: ViaductSchema,
        typeName: String,
    ): CheckerExecutor? {
        val tenant = TYPE_TO_TENANT[typeName] ?: return null
        return TenantAccessCheckerExecutor(tenant, viaAccessService)
    }
}
