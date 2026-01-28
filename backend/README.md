# Viaduct Template Backend

A Viaduct-based GraphQL backend service that sits between the React frontend and Supabase database.

## Architecture

This backend uses [Viaduct](https://github.com/airbnb/viaduct), a composable GraphQL server in Kotlin, to provide a GraphQL layer that:
- Abstracts Supabase implementation details
- Provides strongly-typed GraphQL schema
- Enables efficient batch resolution (N+1 query prevention)
- Supports modular schema organization

## Requirements

- Java JDK 21
- Environment variables for Supabase:
  - `SUPABASE_URL`: Your Supabase project URL
  - `SUPABASE_ANON_KEY`: Your Supabase anonymous key

## Quick Start

### 1. Set environment variables

```bash
export SUPABASE_URL=https://your-project.supabase.co
export SUPABASE_ANON_KEY=your-anon-key
```

### 2. Build and run

```bash
./gradlew run
```

The server will start on `http://localhost:8080`.

### 3. Access GraphiQL

Open your browser to [http://localhost:8080/graphiql](http://localhost:8080/graphiql)

### 4. Try Example Queries

#### Get Supabase configuration (public)

```graphql
query {
  supabaseConfig {
    url
    anonKey
  }
}
```

#### Get all groups (authenticated)

```graphql
query {
  groups {
    id
    name
    description
  }
}
```

#### Create a group (authenticated)

```graphql
mutation {
  createGroup(input: {
    name: "My Group"
    description: "Optional description"
  }) {
    id
    name
  }
}
```

## Project Structure

```
backend/
├── src/main/
│   ├── kotlin/                 # Application, resolvers, services
│   │   └── com/viaduct/
│   │       ├── Application.kt  # Ktor entry point
│   │       ├── SupabaseClient.kt # Supabase integration
│   │       └── resolvers/      # GraphQL resolvers
│   └── viaduct/schema/         # GraphQL schema definitions (.graphqls)
└── build.gradle.kts            # Build configuration
```

## Development

### Build the project

```bash
./gradlew build
```

### Run tests

```bash
./gradlew test
```

### Clean build

```bash
./gradlew clean build
```

## Key Concepts

- **GlobalIDs**: Base64-encoded identifiers combining type name and internal ID
- **Scopes**: `@scope(to: ["default"])` for authenticated, `@scope(to: ["public"])` for unauthenticated
- **Resolvers**: Extend generated base classes and implement `resolve()` method
- **Authentication**: JWT token passed via `Authorization` header, creates authenticated Supabase client

## Adding New Features

See [IMPLEMENTING_A_RESOURCE.md](../docs/IMPLEMENTING_A_RESOURCE.md) for a complete guide to adding new resources.
