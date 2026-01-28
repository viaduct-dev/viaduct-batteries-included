# Viaduct Template

A starter template demonstrating a three-tier architecture with GraphQL middleware.

To build your Viaduct-backed application, first fork this repository as a starting point.  After you successfully build and test our basline functionality (using both the frontend and GraphiQL), you're ready to build your app!

## Architecture

- **Frontend** (in `src/`): React + Vite with shadcn/ui components (TypeScript)
- **Backend** (in `backend/`): Viaduct GraphQL middleware layer (Kotlin/Ktor)
- **Database**: Supabase PostgreSQL with Row Level Security (database DDL in `dbschema/`)

The backend provides a type-safe GraphQL API that sits between the React frontend and Supabase, enabling efficient data fetching with batch resolution and modular schema organization.

## Quick Start

This project uses [mise](https://mise.jdx.dev/) for unified tool management and orchestration:

```bash
# Install all dependencies (Java 21, Podman, Supabase CLI, etc.)
mise install

# Start the full development environment
mise run dev
```

This will start:
- Frontend at http://localhost:5173
- Backend GraphQL API at http://localhost:8080/graphql
- GraphiQL playground at http://localhost:8080/graphiql
- Supabase Studio at http://127.0.0.1:54323

## Documentation

**For detailed setup, development commands, architecture details, and troubleshooting, see [AGENTS.md](./AGENTS.md)**

The CLAUDE.md file contains comprehensive documentation including:
- Complete development commands (mise, npm, gradle, supabase)
- Architecture and request flow details
- GraphQL API reference with example queries
- Environment configuration
- Database schema
- Troubleshooting guide

## Technology Stack

**Frontend:**
- React 18 with TypeScript
- Vite for build tooling
- shadcn/ui components
- Tailwind CSS
- Supabase Auth client

**Backend:**
- Viaduct GraphQL framework
- Kotlin with Ktor
- Supabase Kotlin client
- GraphQL Java

**Infrastructure:**
- Supabase (PostgreSQL + Auth + Realtime)
- Podman for local containers
- mise for tool orchestration

## Project Origin

This project was bootstrapped with [Lovable](https://lovable.dev/projects/be4f049d-baa1-4d1e-89a9-8c73750f8724) and extended with a Viaduct backend and Supabase integration.
