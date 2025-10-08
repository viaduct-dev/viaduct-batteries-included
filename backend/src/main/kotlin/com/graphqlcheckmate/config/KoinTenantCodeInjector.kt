package com.graphqlcheckmate.config

import org.koin.core.Koin
import viaduct.service.api.spi.TenantCodeInjector
import javax.inject.Provider

/**
 * Custom TenantCodeInjector that uses Koin for dependency injection
 * This allows Viaduct to resolve resolver instances using Koin
 */
class KoinTenantCodeInjector(private val koin: Koin) : TenantCodeInjector {
    override fun <T> getProvider(clazz: Class<T>): Provider<T> {
        return Provider {
            koin.get(clazz)
        }
    }
}
