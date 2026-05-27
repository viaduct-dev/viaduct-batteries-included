package com.example.resolvers

import com.example.resolvers.resolverbases.QueryResolvers
import com.example.services.PersonEntity
import com.example.services.TenantPermission
import com.example.services.ViaAccessAuthorizationService
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Person

@Resolver
class PersonsQueryResolver(
    private val authService: ViaAccessAuthorizationService,
) : QueryResolvers.Persons() {
    override suspend fun resolve(ctx: Context): List<Person> {
        ctx.requireTenantPermission(authService, "default", TenantPermission.EDITOR)
        val persons = ctx.authenticatedClient.getAllPersons()
        return persons.map { it.toGrt(ctx) }
    }
}

internal fun PersonEntity.toGrt(ctx: viaduct.api.context.ExecutionContext): Person =
    Person.Builder(ctx)
        .id(ctx.globalIDFor(Person.Reflection, id))
        .displayName(display_name)
        .email(email)
        .authUserId(auth_user_id)
        .createdAt(created_at)
        .build()
