package com.graphqlcheckmate.config

import com.graphqlcheckmate.SupabaseService
import com.graphqlcheckmate.resolvers.*
import com.graphqlcheckmate.services.AuthService
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

    // Resolvers
    singleOf(::PingQueryResolver)
    singleOf(::SetUserAdminResolver)
    singleOf(::UsersQueryResolver)
    singleOf(::DeleteUserResolver)
}
