# Viaduct Template Backend

A Viaduct-based GraphQL backend service that sits between the Lovable frontend and Supabase database.

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
./gradlew bootRun
```

The server will start on `http://localhost:8080`.

### 3. Access GraphiQL

Open your browser to [http://localhost:8080/graphiql](http://localhost:8080/graphiql)

### 4. Try Example Queries

#### Get all checklist items

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

#### Create a new item

```graphql
mutation {
  createChecklistItem(input: {
    title: "Buy groceries"
    userId: "your-user-id"
  }) {
    id
    title
    completed
  }
}
```

#### Update an item

```graphql
mutation {
  updateChecklistItem(input: {
    id: "item-global-id"
    completed: true
  }) {
    id
    title
    completed
    updatedAt
  }
}
```

#### Delete an item

```graphql
mutation {
  deleteChecklistItem(input: {
    id: "item-global-id"
  })
}
```

## Project Structure

```
backend/
├── modules/
│   └── checklist/              # Checklist module
│       ├── src/main/
│       │   ├── kotlin/         # Kotlin resolvers and services
│       │   └── viaduct/schema/ # GraphQL schema definitions
│       └── build.gradle.kts
├── src/main/
│   ├── kotlin/                 # Main application
│   └── resources/              # Configuration files
└── build.gradle.kts            # Main build configuration
```

## Integration with Frontend

Update the frontend's GraphQL endpoint from Supabase to Viaduct:

```typescript
// Before (in src/lib/graphql.ts)
const response = await fetch(`${SUPABASE_URL}/graphql/v1`, { ... });

// After
const response = await fetch(`http://localhost:8080/graphql`, { ... });
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

## Notes

- GlobalIDs are base64-encoded identifiers combining type name and internal ID
- Authentication/authorization should be added via Spring Security
- CORS configuration may be needed for frontend integration
