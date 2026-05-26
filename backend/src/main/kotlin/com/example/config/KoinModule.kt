package com.example.config

import com.example.SupabaseService
import com.example.resolvers.*
import com.example.services.AuthService
import com.example.services.GroupService
import com.example.services.PostgresAuthorizationService
import com.example.services.UserService
import com.example.services.ViaAccessAuthorizationService
import com.example.services.ViaAccessService
import com.example.sync.AsanaWorkspaceClient
import com.example.sync.GitHubOrgClient
import com.example.sync.GitHubSyncExecutor
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin module for dependency injection configuration.
 *
 * This module is used standalone (not as a Ktor plugin) so that singletons
 * can survive CRaC checkpoint/restore independently of the server.
 * Request-scoped context creation is handled directly by the auth plugin.
 */
fun appModule(supabaseUrl: String, supabaseKey: String) = module {
    // HTTP client (singleton) - shared across all requests for connection pooling
    single {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000  // 60 seconds for Supabase requests
                connectTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    // Core services (singletons)
    single { SupabaseService(supabaseUrl, supabaseKey, get()) }
    singleOf(::AuthService)
    singleOf(::UserService)
    singleOf(::GroupService)
    singleOf(::ViaAccessService)
    single<ViaAccessAuthorizationService> { PostgresAuthorizationService() }
    single { GitHubSyncExecutor.fromEnv() }
    single { GitHubOrgClient.fromEnv(get()) }
    single { AsanaWorkspaceClient.fromEnv(get()) }

    // Resolvers - Auth (public, no authentication required)
    singleOf(::SignInResolver)
    singleOf(::SignUpResolver)
    singleOf(::RefreshTokenResolver)
    singleOf(::SupabaseConfigResolver)

    // Resolvers - Admin
    singleOf(::PingQueryResolver)
    singleOf(::SetUserAdminResolver)
    singleOf(::UsersQueryResolver)
    singleOf(::SearchUsersQueryResolver)
    singleOf(::DeleteUserResolver)

    // Resolvers - Group Queries
    singleOf(::GroupsQueryResolver)
    singleOf(::GroupQueryResolver)

    // Resolvers - Group Mutations
    singleOf(::CreateGroupResolver)
    singleOf(::AddGroupMemberResolver)
    singleOf(::RemoveGroupMemberResolver)
    singleOf(::ArchiveGroupResolver)

    // Resolvers - Group Fields
    singleOf(::GroupMembersResolver)
    singleOf(::GroupAccessSummaryResolver)

    // Resolvers - TenantAsset (default tenant)
    singleOf(::TenantAssetsQueryResolver)
    singleOf(::TenantAssetQueryResolver)
    singleOf(::TenantAssetPoliciesResolver)
    singleOf(::TenantAssetPolicyTenantAssetResolver)
    singleOf(::TenantAssetPolicyGroupResolver)
    singleOf(::GrantTenantAccessResolver)
    singleOf(::RevokeTenantAccessResolver)
    singleOf(::GrantGroupAccessResolver)

    // Resolvers - SyncJob / ExternalIdentity (default tenant)
    singleOf(::SyncJobQueryResolver)
    singleOf(::MyExternalIdentitiesResolver)

    // Resolvers - GitHub tenant
    singleOf(::GitHubRepoAssetsQueryResolver)
    singleOf(::GitHubRepoAssetQueryResolver)
    singleOf(::GitHubTeamAssetsQueryResolver)
    singleOf(::SearchProviderUsersQueryResolver)
    singleOf(::RegisterGitHubRepoResolver)
    singleOf(::RegisterGitHubTeamResolver)
    singleOf(::GitHubRepoPoliciesResolver)
    singleOf(::GitHubTeamPoliciesResolver)
    singleOf(::CreateGitHubRepoPolicyResolver)
    singleOf(::DeleteGitHubRepoPolicyResolver)
    singleOf(::CreateGitHubTeamPolicyResolver)
    singleOf(::DeleteGitHubTeamPolicyResolver)
    singleOf(::SetExternalIdentityResolver)
    singleOf(::ImportProviderIdentitiesResolver)
    singleOf(::PreviewAssetSyncResolver)
    singleOf(::ImportAssetResolver)
    singleOf(::SyncAssetResolver)

    // Resolvers - Asana tenant
    singleOf(::AsanaProjectAssetsQueryResolver)
    singleOf(::AsanaProjectAssetQueryResolver)
    singleOf(::AsanaPortfolioAssetsQueryResolver)
    singleOf(::SearchAsanaUsersQueryResolver)
    singleOf(::RegisterAsanaProjectResolver)
    singleOf(::RegisterAsanaPortfolioResolver)
    singleOf(::AsanaProjectPoliciesResolver)
    singleOf(::AsanaPortfolioPoliciesResolver)
    singleOf(::CreateAsanaProjectPolicyResolver)
    singleOf(::DeleteAsanaProjectPolicyResolver)
    singleOf(::CreateAsanaPortfolioPolicyResolver)
    singleOf(::DeleteAsanaPortfolioPolicyResolver)
    singleOf(::SetAsanaIdentityResolver)
    singleOf(::ImportAsanaIdentitiesResolver)
    singleOf(::PreviewAsanaAssetSyncResolver)
    singleOf(::ImportAsanaAssetResolver)
    singleOf(::SyncAsanaAssetResolver)

    // Resolvers - Phase 4: Access requests
    singleOf(::RequestableAssetsResolver)
    singleOf(::PendingAccessRequestsResolver)
    singleOf(::RequestGroupAccessResolver)
    singleOf(::ApproveAccessRequestResolver)
    singleOf(::RejectAccessRequestResolver)
    singleOf(::CancelAccessRequestResolver)
    singleOf(::SetAssetRequestableResolver)

    // Resolvers - Admin tenant
    singleOf(::GroupPoliciesQueryResolver)
    singleOf(::AllAssetsQueryResolver)
    singleOf(::ReconcileToExistingUserResolver)
    singleOf(::ReconcileToNewUserResolver)

    // Resolvers - Per-asset provider user lists
    singleOf(::GitHubRepoProviderUsersResolver)
    singleOf(::GitHubTeamProviderUsersResolver)
    singleOf(::AsanaProjectProviderUsersResolver)
    singleOf(::AsanaPortfolioProviderUsersResolver)
}
