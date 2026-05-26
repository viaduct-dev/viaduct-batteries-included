package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.SyncJob
import viaduct.api.grts.SyncJobAction
import viaduct.api.grts.SyncJobStatus

@Resolver
class SyncJobQueryResolver(
    private val viaAccessService: ViaAccessService
) : QueryResolvers.SyncJob() {
    override suspend fun resolve(ctx: Context): SyncJob? {
        val jobId = ctx.arguments.id.internalID
        val entity = viaAccessService.getSyncJob(ctx.authenticatedClient, jobId) ?: return null
        return entity.toGrt(ctx)
    }
}

internal fun com.example.services.SyncJobEntity.toGrt(ctx: viaduct.api.context.ExecutionContext): SyncJob =
    SyncJob.Builder(ctx)
        .id(ctx.globalIDFor(SyncJob.Reflection, id))
        .assetId(asset_id)
        .assetType(asset_type)
        .action(SyncJobAction.valueOf(action))
        .status(SyncJobStatus.valueOf(status))
        .planSummary(plan_summary)
        .lastError(last_error)
        .attemptCount(attempt_count)
        .createdAt(created_at)
        .completedAt(completed_at)
        .build()
