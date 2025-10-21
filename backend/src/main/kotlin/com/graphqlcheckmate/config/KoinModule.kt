package com.graphqlcheckmate.config

import com.graphqlcheckmate.SupabaseService
import com.graphqlcheckmate.resolvers.*
import com.graphqlcheckmate.services.AuthService
import com.graphqlcheckmate.services.GroupService
import com.graphqlcheckmate.services.UserService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin module for dependency injection configuration
 */
fun appModule(supabaseUrl: String, supabaseKey: String) = module {
    // Core services
    single { SupabaseService(supabaseUrl, supabaseKey) }
    singleOf(::AuthService)
    singleOf(::UserService)
    singleOf(::GroupService)

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
