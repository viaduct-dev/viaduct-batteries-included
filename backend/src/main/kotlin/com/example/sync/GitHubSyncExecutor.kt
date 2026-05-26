package com.example.sync

import com.pulumi.automation.ConfigValue
import com.pulumi.automation.LocalWorkspace
import com.pulumi.automation.PreviewOptions
import com.pulumi.automation.RefreshOptions
import com.pulumi.automation.UpOptions
import com.example.AuthenticatedSupabaseClient
import com.example.services.SyncJobEntity
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val MAX_ATTEMPTS = 3

class GitHubSyncExecutor(
    private val githubToken: String,
    private val githubOrg: String,
) {
    private val log = LoggerFactory.getLogger(GitHubSyncExecutor::class.java)

    suspend fun executeRepoSync(
        client: AuthenticatedSupabaseClient,
        job: SyncJobEntity,
        owner: String,
        repo: String,
        preview: Boolean = false,
    ) {
        val newAttempt = job.attempt_count + 1
        client.updateSyncJob(job.id, "RUNNING", attemptCount = newAttempt)
        try {
            val db = SupabaseStackDb(client)

            // Check that all group members with policies on this asset have verified GitHub identities.
            val policies = db.loadPoliciesForAsset(job.asset_id)
            val missingIdentities = mutableListOf<String>()
            for (policy in policies) {
                for (member in db.loadMembersForGroup(policy.groupId)) {
                    if (db.loadExternalIdentity(member.userId, "github") == null) {
                        missingIdentities += member.userId
                    }
                }
            }
            if (missingIdentities.isNotEmpty()) {
                val msg = "Missing or unverified GitHub identities for user(s): ${missingIdentities.distinct().joinToString()}"
                log.warn("Sync job ${job.id} BLOCKED: $msg")
                client.updateSyncJob(job.id, "BLOCKED", lastError = msg)
                return
            }

            val stackName = "viaaccess-repo-${job.asset_id}"
            val ownerRepo = "$owner/$repo"

            val stack = LocalWorkspace.createOrSelectStack(
                "viaaccess-github",
                stackName,
                githubRepoStackProgram(ownerRepo, job.asset_id, db)
            )

            stack.setConfig("github:token", ConfigValue(githubToken, true))
            stack.setConfig("github:owner", ConfigValue(githubOrg))

            when {
                job.action == "IMPORT_ASSET" -> {
                    // Seed Pulumi state from live external resources via stack refresh.
                    val result = stack.refresh(RefreshOptions.builder()
                        .onStandardOutput { log.info("[import] $it") }
                        .build())
                    val changes = result.summary()?.resourceChanges()
                    val summary = if (changes.isNullOrEmpty()) "no changes"
                        else changes.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                    log.info("Import job ${job.id} succeeded: $summary")
                    client.updateSyncJob(job.id, "SUCCEEDED", planSummary = summary, attemptCount = newAttempt)
                    client.updatePolicySyncStatus(job.asset_id, "HEALTHY")
                }
                preview -> {
                    val result = stack.preview(PreviewOptions.builder()
                        .onStandardOutput { log.info("[preview] $it") }
                        .build())
                    val changes = result.changeSummary()
                    val summary = if (changes.isNullOrEmpty()) "no changes"
                        else changes.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                    client.updateSyncJob(job.id, "SUCCEEDED", planSummary = summary, attemptCount = newAttempt)
                }
                else -> {
                    val result = stack.up(UpOptions.builder()
                        .onStandardOutput { log.info("[up] $it") }
                        .build())
                    val changes = result.summary()?.resourceChanges()
                    val summary = if (changes.isNullOrEmpty()) "no changes"
                        else changes.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                    log.info("Sync job ${job.id} succeeded: $summary")
                    client.updateSyncJob(job.id, "SUCCEEDED", planSummary = summary, attemptCount = newAttempt)
                    client.updatePolicySyncStatus(job.asset_id, "HEALTHY")
                }
            }
        } catch (e: Exception) {
            log.error("Sync job ${job.id} failed (attempt $newAttempt)", e)
            if (newAttempt >= MAX_ATTEMPTS) {
                log.warn("Sync job ${job.id} EXHAUSTED after $newAttempt attempts")
                client.updateSyncJob(job.id, "EXHAUSTED", lastError = e.message?.take(2000), attemptCount = newAttempt)
                client.updatePolicySyncStatus(job.asset_id, "FAILED")
            } else {
                val retryAt = Instant.now().plus((newAttempt * 5).toLong(), ChronoUnit.MINUTES).toString()
                client.updateSyncJob(job.id, "FAILED", lastError = e.message?.take(2000), attemptCount = newAttempt, nextRetryAt = retryAt)
                client.updatePolicySyncStatus(job.asset_id, "DEGRADED")
            }
        }
    }

    companion object {
        fun fromEnv(): GitHubSyncExecutor? {
            val token = System.getenv("GITHUB_TOKEN") ?: return null
            val org = System.getenv("GITHUB_ORG") ?: return null
            return GitHubSyncExecutor(token, org)
        }
    }
}
