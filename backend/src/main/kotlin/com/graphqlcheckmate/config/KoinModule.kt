package com.graphqlcheckmate.config

import com.graphqlcheckmate.AuthenticatedSupabaseClient
import com.graphqlcheckmate.GraphQLRequestContext
import com.graphqlcheckmate.SupabaseService
import com.graphqlcheckmate.resolvers.*
import com.graphqlcheckmate.services.AuthService
import com.graphqlcheckmate.services.GroupService
import com.graphqlcheckmate.services.UserService
import io.ktor.server.application.ApplicationCall
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.module.requestScope

/**
 * Koin module for dependency injection configuration
 */
fun appModule(supabaseUrl: String, supabaseKey: String) = module {
    // Core services (singletons)
    single { SupabaseService(supabaseUrl, supabaseKey) }
    singleOf(::AuthService)
    singleOf(::UserService)
    singleOf(::GroupService)

    // Request scope - automatically tied to Ktor request lifecycle
    // ApplicationCall is automatically available for injection in this scope
    requestScope {
        // Factory for GraphQLRequestContext - ApplicationCall injected automatically
        scoped {
            GraphQLRequestContextFactory(
                call = get(),
                authService = get()
            ).create()
        }

        // Factory for AuthenticatedSupabaseClient
        scoped<AuthenticatedSupabaseClient> {
            val requestContext = get<GraphQLRequestContext>()
            val supabaseService = get<SupabaseService>()
            supabaseService.createAuthenticatedClient(requestContext.accessToken)
        }

        // Factory for RequestContext wrapper
        scoped {
            RequestContext(
                graphQLContext = get(),
                authenticatedClient = get()
            )
        }
    }

    // Resolvers - Admin
    singleOf(::PingQueryResolver)
    singleOf(::SetUserAdminResolver)
    singleOf(::UsersQueryResolver)
    singleOf(::SearchUsersQueryResolver)
    singleOf(::DeleteUserResolver)

    // Resolvers - CheckboxGroup Queries
    singleOf(::CheckboxGroupsQueryResolver)
    singleOf(::CheckboxGroupQueryResolver)

    // Resolvers - CheckboxGroup Mutations
    singleOf(::CreateCheckboxGroupResolver)
    singleOf(::AddGroupMemberResolver)
    singleOf(::RemoveGroupMemberResolver)

    // Resolvers - CheckboxGroup Fields
    singleOf(::CheckboxGroupMembersResolver)
    singleOf(::CheckboxGroupChecklistItemsResolver)

    // Resolvers - ChecklistItem Queries
    singleOf(::ChecklistItemsQueryResolver)
    singleOf(::ChecklistItemsByGroupQueryResolver)

    // Resolvers - ChecklistItem Mutations
    singleOf(::CreateChecklistItemResolver)
    singleOf(::UpdateChecklistItemResolver)
    singleOf(::DeleteChecklistItemResolver)
}

/**
 * Factory for creating GraphQLRequestContext from ApplicationCall
 * ApplicationCall is automatically injected by Koin's requestScope
 */
class GraphQLRequestContextFactory(
    private val call: ApplicationCall,
    private val authService: AuthService
) {
    fun create(): GraphQLRequestContext {
        val authHeader = call.request.headers["Authorization"]
        val accessToken = authHeader?.removePrefix("Bearer ")?.trim()
            ?: throw IllegalArgumentException("Authorization header required")

        return authService.createRequestContext(accessToken)
    }
}
