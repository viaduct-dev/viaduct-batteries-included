package com.example.resolvers

import com.example.resolvers.resolverbases.MutationResolvers
import com.example.services.GroupService
import com.example.services.ViaAccessService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Group
import viaduct.api.grts.GroupAccessResult
import viaduct.api.grts.GroupStatus
import viaduct.api.grts.SyncJob

@Resolver
class GrantGroupAccessResolver(
    private val viaAccessService: ViaAccessService,
    private val groupService: GroupService,
) : MutationResolvers.GrantGroupAccess() {
    override suspend fun resolve(ctx: Context): GroupAccessResult {
        val groupId = ctx.arguments.groupId.internalID
        val client = ctx.authenticatedClient

        val syncJobs = mutableListOf<SyncJob>()

        for (grant in ctx.arguments.grants) {
            val assetId = grant.assetId
            val permission = grant.permission

            val asset = client.getAssetById(assetId)
                ?: error("Asset not found: $assetId")

            val jobEntity = when (asset.asset_type) {
                "GITHUB_REPO" -> {
                    viaAccessService.createGitHubRepoPolicy(client, assetId, groupId, permission)
                    client.createSyncJob(assetId, asset.asset_type, asset.tenant_name, "RECONCILE_ASSET")
                }
                "GITHUB_TEAM" -> {
                    viaAccessService.createGitHubTeamPolicy(client, assetId, groupId, permission)
                    client.createSyncJob(assetId, asset.asset_type, asset.tenant_name, "RECONCILE_ASSET")
                }
                "ASANA_PROJECT" -> {
                    viaAccessService.createAsanaProjectPolicy(client, assetId, groupId, permission)
                    client.createSyncJob(assetId, asset.asset_type, asset.tenant_name, "RECONCILE_ASSET")
                }
                "ASANA_PORTFOLIO" -> {
                    viaAccessService.createAsanaPortfolioPolicy(client, assetId, groupId, permission)
                    client.createSyncJob(assetId, asset.asset_type, asset.tenant_name, "RECONCILE_ASSET")
                }
                else -> error("Unsupported asset type: ${asset.asset_type}")
            }
            syncJobs.add(jobEntity.toGrt(ctx))
        }

        val groupEntity = groupService.getGroupById(client, groupId)
            ?: error("Group not found: $groupId")
        val group = Group.Builder(ctx)
            .id(ctx.arguments.groupId)
            .name(groupEntity.name)
            .description(groupEntity.description)
            .createdBy(groupEntity.created_by)
            .status(GroupStatus.valueOf(groupEntity.status))
            .createdAt(groupEntity.created_at)
            .updatedAt(groupEntity.updated_at)
            .build()

        return GroupAccessResult.Builder(ctx)
            .group(group)
            .syncJobs(syncJobs)
            .build()
    }
}
