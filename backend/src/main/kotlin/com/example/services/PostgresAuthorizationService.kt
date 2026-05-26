package com.example.services

import com.example.AuthenticatedSupabaseClient

class PostgresAuthorizationService : ViaAccessAuthorizationService {

    override suspend fun requireTenantPermission(
        client: AuthenticatedSupabaseClient,
        userId: String,
        tenantName: String,
        required: TenantPermission,
    ) {
        val hasPermission = client.userHasTenantPermission(userId, tenantName, required)
        if (!hasPermission) {
            throw IllegalArgumentException(
                "Insufficient permission on tenant '$tenantName': requires $required"
            )
        }
    }

    override suspend fun getAssetsAffectedByGroup(
        client: AuthenticatedSupabaseClient,
        groupId: String,
    ): List<AffectedAsset> = client.getAssetsAffectedByGroup(groupId)
}
