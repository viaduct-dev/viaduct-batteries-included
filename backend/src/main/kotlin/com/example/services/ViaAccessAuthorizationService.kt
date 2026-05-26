package com.example.services

import com.example.AuthenticatedSupabaseClient

enum class TenantPermission { REQUESTER, VIEWER, EDITOR, OWNER }

interface ViaAccessAuthorizationService {
    suspend fun requireTenantPermission(
        client: AuthenticatedSupabaseClient,
        userId: String,
        tenantName: String,
        required: TenantPermission,
    )
    suspend fun getAssetsAffectedByGroup(
        client: AuthenticatedSupabaseClient,
        groupId: String,
    ): List<AffectedAsset>
}

data class AffectedAsset(
    val assetId: String,
    val assetType: String,
    val tenantName: String,
)
