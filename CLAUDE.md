# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Viaduct template project with a three-tier architecture:
- **Frontend**: React/Vite with shadcn/ui components
- **Backend**: Viaduct GraphQL middleware layer (Kotlin/Spring Boot)
- **Database**: Supabase PostgreSQL

## Development Commands

### Using mise (Recommended)

This project uses [mise](https://mise.jdx.dev/) for tool management and orchestration:

```bash
# Install all dependencies (Java, Podman, Supabase CLI, etc.)
mise install

# Start full development environment (all services)
mise run dev

# Start only dependencies (Podman + Supabase)
mise run deps-start

# Start backend only (requires deps-start first)
mise run backend

# Start frontend only
mise run frontend

# Check status of all services
mise run status

# Stop all services
mise run stop
```

### Using npm (Frontend only)

```bash
npm install          # Install dependencies
npm run dev          # Start frontend dev server (port 5173)
npm run build        # Production build
npm run build:dev    # Development build
npm run lint         # Run ESLint
npm run preview      # Preview production build
```

### Using Gradle (Backend only)

```bash
cd backend
./gradlew bootRun    # Start backend server (port 8080)
./gradlew build      # Build the project
./gradlew test       # Run tests
./gradlew clean build # Clean and rebuild
```

### Supabase (Database)

```bash
supabase start       # Start local Supabase
supabase stop        # Stop local Supabase
supabase status      # Check Supabase status
supabase db reset    # Reset database to migrations
```

## Architecture

### Request Flow

```
React Frontend (port 5173)
    ↓ GraphQL over HTTP
Viaduct Backend (port 8080)
    ↓ Supabase Kotlin Client
Supabase PostgreSQL (port 54321)
```

### Key Architecture Decisions

1. **Viaduct GraphQL Layer**: Provides abstraction between frontend and Supabase, enabling:
   - Type-safe GraphQL schema
   - Efficient batch resolution (prevents N+1 queries)
   - Modular schema organization
   - Backend flexibility (can swap data sources)

2. **Authentication**: Uses Supabase Auth. The frontend passes the access token and user ID in headers to the Viaduct backend.

3. **GraphQL Schema**: Uses camelCase field names (not snake_case like raw Supabase). Schema defined in `.graphqls` files under `backend/src/main/viaduct/schema/`.

### Database Schema

Single table: `checklist_items`
- `id`: UUID (primary key)
- `user_id`: UUID (foreign key to auth.users)
- `title`: TEXT
- `completed`: BOOLEAN (default false)
- `created_at`: TIMESTAMP WITH TIME ZONE
- `updated_at`: TIMESTAMP WITH TIME ZONE

## Code Organization

### Frontend (`src/`)

- `App.tsx`: Main app component with React Router setup
- `pages/Index.tsx`: Main checklist page with CRUD operations
- `pages/Auth.tsx`: Authentication page
- `lib/graphql.ts`: GraphQL client with queries/mutations for Viaduct backend
- `integrations/supabase/`: Supabase client and TypeScript types
- `components/`: Reusable UI components (shadcn/ui based)
- `hooks/`: Custom React hooks

### Backend (`backend/`)

- `src/main/kotlin/com/viaduct/`:
  - `Application.kt`: Spring Boot application entry point
  - `SupabaseClient.kt`: Supabase client configuration
  - `resolvers/`: GraphQL resolvers for queries, mutations, and nodes
  - `config/`: Spring configuration classes
- `src/main/viaduct/schema/`: GraphQL schema definitions (`.graphqls` files)
- `build.gradle.kts`: Gradle build configuration with Viaduct plugins

### Database (`supabase/`)

- `migrations/`: SQL migration files
- `config.toml`: Supabase configuration

## Environment Setup

### Environment Variables

All required environment variables are automatically managed by mise via `mise.toml`:

**Backend (Viaduct/Kotlin):**
- `SUPABASE_URL`: Local Supabase API endpoint
- `SUPABASE_ANON_KEY`: Supabase anonymous key

**Frontend (Vite/React):**
- `VITE_SUPABASE_URL`: Local Supabase URL for frontend
- `VITE_SUPABASE_PUBLISHABLE_KEY`: Supabase key for frontend
- `VITE_SUPABASE_PROJECT_ID`: Project identifier
- `VITE_GRAPHQL_ENDPOINT`: Defaults to `http://localhost:8080/graphql` if not set

**Infrastructure:**
- `DOCKER_HOST`: Dynamically detected from Podman machine configuration
- The socket path is auto-detected at runtime, making the project portable across different machines

No manual exports needed - mise activates these automatically when you `cd` into the project directory.

### Tool Requirements

All required tools are managed by mise and installed via `mise install`:
- **Java JDK 21**: Required for Viaduct/Kotlin backend
- **Podman**: Container runtime for Supabase
- **Supabase CLI**: Local Supabase development
- **Node.js**: For frontend development (can also be managed via mise)

## GraphQL API

### Queries

```graphql
# Get all checklist items for authenticated user
query {
  checklistItems {
    id
    title
    completed
    createdAt
    updatedAt
  }
}
```

### Mutations

```graphql
# Create item
mutation {
  createChecklistItem(input: {
    title: "New task"
    userId: "user-uuid"
  }) {
    id
    title
    completed
  }
}

# Update item
mutation {
  updateChecklistItem(input: {
    id: "global-id"
    completed: true
  }) {
    id
    completed
    updatedAt
  }
}

# Delete item
mutation {
  deleteChecklistItem(input: {
    id: "global-id"
  })
}
```

### Testing GraphQL

Access GraphiQL at http://localhost:8080/graphiql when backend is running.

## Service URLs (Local Development)

- **Frontend**: http://localhost:5173
- **Backend GraphQL**: http://localhost:8080/graphql
- **GraphiQL**: http://localhost:8080/graphiql
- **Supabase Studio**: http://127.0.0.1:54323
- **Supabase API**: http://127.0.0.1:54321
- **Local PostgreSQL**: postgresql://postgres:postgres@127.0.0.1:54322/postgres

## Important Implementation Details

### GlobalIDs

Viaduct uses GlobalIDs (base64-encoded identifiers combining type name and internal ID). The backend handles conversion between Supabase UUIDs and GlobalIDs.

### Authentication Flow

1. Frontend authenticates via Supabase Auth
2. Frontend sends GraphQL requests with headers:
   - `Authorization: Bearer <access_token>`
   - `X-User-Id: <user_id>`
3. Backend uses these for Supabase RLS policy enforcement

### TypeScript Configuration

Project uses relaxed TypeScript settings for Lovable compatibility:
- `noImplicitAny: false`
- `strictNullChecks: false`
- Path alias `@/*` maps to `./src/*`

## Viaduct Module System

The backend uses Viaduct's modular architecture:
- Each module can have its own schema files and resolvers
- Schema files use `@resolver` directive to generate resolver base classes
- Types implement `Node` interface for GlobalID support
- Use `@scope(to: ["default"])` for tenant isolation

## Troubleshooting

### Backend won't start
1. Run `mise install` to ensure Java JDK 21 and all tools are installed
2. Check Java is available: `java -version` (should show version 21)
3. Verify environment variables are set
4. Ensure Supabase is running: `supabase status`

### Frontend can't connect to backend
1. Verify backend is running on port 8080
2. Check CORS configuration if needed
3. Verify `VITE_GRAPHQL_ENDPOINT` environment variable

### Podman issues

**Socket not found errors:**
If Supabase can't find the Podman socket, run the diagnostic:
```bash
mise run diagnose-podman
```

Common fixes:
```bash
# Restart Podman machine
podman machine stop
podman machine start

# If machine doesn't exist, initialize it
podman machine init
podman machine start

# Verify connection
podman info
```

The project now auto-detects the Podman socket location, so it should work across different machines without manual configuration.

### Database reset needed
```bash
mise run stop
supabase db reset
mise run dev
```
