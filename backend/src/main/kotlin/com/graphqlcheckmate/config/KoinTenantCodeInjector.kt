package com.graphqlcheckmate.config

import org.koin.core.context.GlobalContext
import viaduct.service.api.spi.TenantCodeInjector
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Custom TenantCodeInjector that uses Koin for dependency injection
 * This allows Viaduct to resolve resolver instances using Koin
 *
 * Note: Uses GlobalContext to always access the current active Koin instance,
 * which is important for test scenarios where Koin may be restarted
 */
class KoinTenantCodeInjector : TenantCodeInjector {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getProvider(clazz: Class<T>): Provider<T> {
        return Provider {
            // Convert Java Class to Kotlin KClass and get instance from current Koin
            val kClass = (clazz as Class<Any>).kotlin as KClass<Any>
            GlobalContext.get().get(kClass, null) as T
        }
    }
}
