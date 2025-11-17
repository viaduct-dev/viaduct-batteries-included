# Code Review Action Items

Generated: 2025-10-22

## HIGH PRIORITY - Fix Immediately

### ✅ 1. CORS Security - `anyHost()` is a security vulnerability
**Location:** `src/main/kotlin/com/viaduct/Application.kt:101`

**Issue:** Using `anyHost()` allows any origin to access the API

**Fix:** Use environment-based configuration with explicit allowed hosts
```kotlin
val allowedHosts = System.getenv("ALLOWED_ORIGINS")
    ?.split(",")
    ?: listOf("http://localhost:5173", "http://127.0.0.1:5173")

allowedHosts.forEach { host ->
    allowHost(host, schemes = listOf("http", "https"))
}
```

---

### ✅ 2. Unsafe JSON Deserialization with Type Erasure
**Location:** `src/main/kotlin/com/viaduct/Application.kt:109-115`

**Issue:** Using unchecked casts with `Map::class.java`

**Fix:** Create type-safe `GraphQLRequest` data class and use Ktor's content negotiation
```kotlin
@Serializable
data class GraphQLRequest(
    val query: String = "",
    val variables: Map<String, Any?> = emptyMap(),
    val operationName: String? = null
)
```

---

### ✅ 3. Missing Resource Cleanup for HttpClient
**Location:** `src/main/kotlin/com/viaduct/config/KoinModule.kt:23-31`

**Issue:** HttpClient singleton is never closed, leading to resource leaks

**Fix:** Add `onClose` handler to Koin definition
```kotlin
single {
    HttpClient(CIO) { ... }
} onClose { client ->
    client?.close()
}
```

---

### ✅ 4. Multiple HttpClient Instances Created
**Locations:**
- `src/main/kotlin/com/viaduct/SupabaseClient.kt:77-92` (customHttpClient)
- `src/main/kotlin/com/viaduct/SupabaseClient.kt:146-152` (getAuthenticatedClient fallback)
- `src/main/kotlin/com/viaduct/services/GroupService.kt:48` (unused HttpClient)

**Issue:** Multiple HttpClient instances defeat connection pooling

**Fix:**
- Inject shared HttpClient into SupabaseService
- Remove unused HttpClient from GroupService
- Use shared client's engine for Supabase client

---

### ✅ 5. Non-idiomatic Koin Retrieval in Application Setup
**Location:** `src/main/kotlin/com/viaduct/Application.kt:64-68`

**Issue:** Mixing `get<T>()` and `getKoin()` styles

**Fix:** Be consistent - use `koin.get<T>()` for all retrievals

---

### ✅ 6. Authentication Plugin Swallows Exceptions
**Location:** `src/main/kotlin/com/viaduct/plugins/AuthenticationPlugin.kt:42-56`

**Issue:** All exceptions return 401, hiding bugs

**Fix:** Differentiate between authentication failures (401) and infrastructure failures (500)

---

## MEDIUM PRIORITY - Fix in Next Sprint

### ✅ 7. CORS Plugin Try-Catch Anti-pattern
**Location:** `src/main/kotlin/com/viaduct/Application.kt:92-105`

**Fix:** Use `pluginOrNull(CORS)` instead of exception handling

---

### ✅ 8. Deprecated GroupService Method Still in Use
**Location:** `src/main/kotlin/com/viaduct/services/GroupService.kt:72-75`

**Fix:** Removed deprecated method entirely (not in use)

---

### ✅ 9. Type Casting in KoinTenantCodeInjector
**Location:** `src/main/kotlin/com/viaduct/config/KoinTenantCodeInjector.kt:17-24`

**Fix:** Kept original implementation (recommended simplification doesn't work without additional setup)

---

### ✅ 10. Manual JSON Serialization in Response
**Location:** `src/main/kotlin/com/viaduct/Application.kt:135`

**Fix:** Installed ContentNegotiation plugin and let Ktor handle JSON serialization

---

### ✅ 11. GlobalID Decoding Error Handling
**Location:** `src/main/kotlin/com/viaduct/policy/GroupMembershipPolicyExecutor.kt:62-82`

**Fix:** Added proper error messages and GlobalID format validation

---

### ✅ 12. Missing Structured Logging
**Location:** Throughout application

**Fix:** Added Ktor's `CallLogging` plugin for request/response monitoring

---

## LOW PRIORITY - Fix When Convenient

### ✅ 13. Unused application.yml File
**Location:** `src/main/resources/application.yml`

**Fix:** Deleted Spring Boot config file (Ktor uses application.conf)

---

### ⬜ 14. Hardcoded Port Configuration
**Location:** `src/main/resources/application.conf:3-4`

**Fix:** Clarify HOCON ordering with comments or reorder lines

---

### ✅ 15. Unused GroupService Injection in Resolvers
**Locations:** Multiple resolver files

**Fix:** Removed unused GroupService constructor parameters from all 6 resolver files

---

### ✅ 16. Manual JSON String Construction
**Location:** `src/main/kotlin/com/viaduct/SupabaseClient.kt` (multiple locations)

**Fix:** Replaced with type-safe serialization using data classes (SetUserAdminInput, SearchUsersInput, DeleteUserInput)

---

### ✅ 17. Resolver Documentation Clarity
**Location:** All resolver files

**Fix:** Clarified authorization mechanisms (Database RLS policies vs Viaduct policies) in all resolver documentation

---

### ⬜ 18. ~~Inconsistent singleOf Usage~~ ✓
**Status:** Already idiomatic - no action needed

---

### ✅ 19. GraphiQL HTML in Code
**Location:** `src/main/kotlin/com/viaduct/Application.kt:150-171`

**Fix:** Moved HTML template to `src/main/resources/graphiql.html` and updated route to load from resources

---

## Summary

- **Total Issues:** 19
- **High Priority:** 6 (security, resource leaks, type safety)
- **Medium Priority:** 6 (code quality, maintainability)
- **Low Priority:** 7 (style, minor improvements)
- **Not Issues:** Already idiomatic patterns verified

## Progress
- ✅ Completed: 18/19 (All High + All Medium + 5 Low Priority Complete!)
- 🚧 In Progress: 0/19
- ⬜ Not Started: 1/19 (Only #14 remaining - Port Configuration Comments)
