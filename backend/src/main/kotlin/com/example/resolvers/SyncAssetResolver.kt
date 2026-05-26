package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import com.example.sync.GitHubSyncExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import viaduct.api.resolver.Resolver
import viaduct.api.grts.SyncJob

@Resolver
class SyncAssetResolver(
    private val viaAccessService: ViaAccessService,
    private val githubSyncExecutor: GitHubSyncExecutor?,
    private val authService: ViaAccessAuthorizationService,
) : MutationResolvers.SyncAsset() {
    override suspend fun resolve(ctx: Context): SyncJob {
        ctx.requireTenantPermission(authService, "github", TenantPermission.EDITOR)
        val assetId = ctx.arguments.assetId

        val repoPair = viaAccessService.getGitHubRepoAssetById(ctx.authenticatedClient, assetId)
        if (repoPair != null) {
            val (asset, repoAsset) = repoPair
            val job = viaAccessService.createSyncJob(ctx.authenticatedClient, assetId, asset.asset_type, asset.tenant_name, "RECONCILE_ASSET")
            if (githubSyncExecutor != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    githubSyncExecutor.executeRepoSync(ctx.authenticatedClient, job, repoAsset.owner, repoAsset.repo)
                }
            }
            return job.toGrt(ctx)
        }

        val teamPair = viaAccessService.getGitHubTeamAssets(ctx.authenticatedClient).firstOrNull { it.first.id == assetId }
        if (teamPair != null) {
            val (asset, _) = teamPair
            val job = viaAccessService.createSyncJob(ctx.authenticatedClient, assetId, asset.asset_type, asset.tenant_name, "RECONCILE_ASSET")
            return job.toGrt(ctx)
        }

        error("Asset not found: $assetId")
    }
}
