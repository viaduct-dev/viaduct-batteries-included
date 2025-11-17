# Viaduct Integration Guide

This document explains how viaduct-template has been integrated with Viaduct as a GraphQL middleware layer.

## Architecture Overview

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│                 │         │                 │         │                 │
│  Lovable        │────────▶│  Viaduct        │────────▶│   Supabase      │
│  Frontend       │         │  GraphQL Layer  │         │   PostgreSQL    │
│  (React/Vite)   │         │  (Kotlin)       │         │                 │
│                 │         │                 │         │                 │
└─────────────────┘         └─────────────────┘         └─────────────────┘
```

### Benefits of This Architecture

1. **Abstraction**: Frontend doesn't need to know about Supabase implementation details
2. **Type Safety**: Strongly-typed GraphQL schema with Kotlin resolvers
3. **Performance**: Batch resolution prevents N+1 query problems
4. **Modularity**: Schema organized into tenant modules for easy maintenance
5. **Flexibility**: Easy to add new data sources or change backends

## Project Structure

```
viaduct-template/
├── backend/                    # Viaduct GraphQL server
│   ├── modules/
│   │   └── checklist/         # Checklist tenant module
│   │       ├── src/main/
│   │       │   ├── kotlin/    # Resolvers and services
│   │       │   └── viaduct/schema/  # GraphQL schema files
│   │       └── build.gradle.kts
│   ├── src/main/
│   │   ├── kotlin/            # Spring Boot application
│   │   └── resources/         # Configuration
│   └── build.gradle.kts
├── src/                       # Frontend (Lovable/React)
│   ├── lib/graphql.ts        # GraphQL client (updated for Viaduct)
│   └── pages/Index.tsx       # Main app (updated response handling)
└── supabase/                 # Supabase migrations
```

## Setup Instructions

### 1. Backend Setup

```bash
cd backend

# Set environment variables
export SUPABASE_URL=https://your-project.supabase.co
export SUPABASE_ANON_KEY=your-anon-key

# Build and run
./gradlew bootRun
```

The backend will start on <http://localhost:8080> with GraphiQL available at <http://localhost:8080/graphiql>

### 2. Frontend Setup

The frontend has been updated to use the Viaduct GraphQL endpoint:

```bash
# Add environment variable (optional - defaults to localhost:8080)
echo "VITE_GRAPHQL_ENDPOINT=http://localhost:8080/graphql" >> .env

# Run frontend
npm run dev
```

## Key Changes Made

### Backend (New)

1. **Created Viaduct Application**
   - Spring Boot application with Viaduct plugins
   - Modular architecture using tenant modules
   - GraphQL schema in `.graphqls` files

2. **Implemented GraphQL Schema**
   - `ChecklistItem` type implementing `Node` interface
   - Query: `checklistItems`
   - Mutations: `createChecklistItem`, `updateChecklistItem`, `deleteChecklistItem`

3. **Created Resolvers**
   - Node resolver for fetching items by GlobalID
   - Query resolver for listing items
   - Mutation resolvers for CRUD operations
   - Supabase client integration

### Frontend (Modified)

1. **Updated GraphQL Client** (`src/lib/graphql.ts`)
   - Changed endpoint from Supabase to Viaduct
   - Updated queries to match new schema
   - Added user ID header for authentication context

2. **Updated Component** (`src/pages/Index.tsx`)
   - Updated response handling for new GraphQL structure
   - Changed field names from snake_case to camelCase
   - Updated variable names to match new schema

## GraphQL Schema Comparison

### Before (Supabase GraphQL)

```graphql
query {
  checklist_itemsCollection {
    edges {
      node {
        id
        title
        completed
        created_at
        updated_at
      }
    }
  }
}
```

### After (Viaduct)

```graphql
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

## Development Workflow

### Running Both Services

```bash
# Terminal 1: Start backend
cd backend
./gradlew bootRun

# Terminal 2: Start frontend
npm run dev
```

### Testing GraphQL Queries

Use GraphiQL at <http://localhost:8080/graphiql> to test queries:

```graphql
# Example: Get all items
query {
  checklistItems {
    id
    title
    completed
  }
}

# Example: Create item
mutation {
  createChecklistItem(input: { title: "Test item", userId: "user-uuid-here" }) {
    id
    title
  }
}
```

## Resources

- [Viaduct Documentation](https://github.com/airbnb/viaduct)
- [Star Wars Demo](../treehouse/projects/viaduct/oss/demoapps/starwars) - Reference implementation
- [Backend README](./backend/README.md) - Detailed backend documentation
