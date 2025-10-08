# Development Guide

This project uses [mise](https://mise.jdx.dev/) for tool management and task orchestration.

## Prerequisites

- mise (installed via zinit in your dotfiles)
- That's it! mise will handle installing everything else

## Quick Start

### 1. Install dependencies

```bash
# mise will automatically install podman, supabase-cli, and other tools
mise install
```

### 2. Start the full development environment

```bash
# This starts Podman, Supabase, backend (Viaduct), and frontend (React)
mise run dev
```

That's it! The command will:
1. Start Podman machine
2. Start local Supabase
3. Start Viaduct GraphQL backend on http://localhost:8080
4. Start React frontend on http://localhost:5173

## Individual Commands

### Start only dependencies
```bash
mise run deps-start
```

This starts Podman and Supabase.

### Start backend only
```bash
mise run backend
```

Starts the Viaduct GraphQL server (requires dependencies).

### Start frontend only
```bash
mise run frontend
```

Starts the React development server.

### Check status
```bash
mise run status
```

Shows status of Podman and Supabase.

### Stop everything
```bash
mise run stop
```

Stops Supabase and Podman machine.

## Available Services

Once running, you'll have access to:

- **Frontend**: http://localhost:5173
- **Backend GraphQL**: http://localhost:8080/graphql
- **GraphiQL**: http://localhost:8080/graphiql
- **Supabase Studio**: http://127.0.0.1:54323
- **Supabase API**: http://127.0.0.1:54321
- **Local Database**: postgresql://postgres:postgres@127.0.0.1:54322/postgres

## Architecture

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│                 │         │                 │         │                 │
│  React          │────────▶│  Viaduct        │────────▶│   Supabase      │
│  Frontend       │         │  GraphQL Layer  │         │   PostgreSQL    │
│  :5173          │         │  :8080          │         │   :54321        │
│                 │         │                 │         │                 │
└─────────────────┘         └─────────────────┘         └─────────────────┘
```

## Tools Managed by mise

All tools are automatically installed and versioned via `mise.toml`:

- **Podman**: Container runtime (replaces Docker)
- **Supabase CLI**: Local Supabase development
- **(Future)** Java, Gradle, Node.js can also be managed by mise

## Environment Variables

Environment variables are managed in `mise.toml` under the `[env]` section:

- `DOCKER_HOST`: Points to Podman socket for Supabase CLI

## Troubleshooting

### Podman not starting
```bash
podman machine init
podman machine start
```

### Supabase fails to start
```bash
mise run stop
mise run deps-start
```

### Port conflicts
Check if something is already running on ports 5173, 8080, or 54321-54324.

### Reset everything
```bash
mise run stop
supabase db reset
mise run dev
```

## Learn More

- [mise documentation](https://mise.jdx.dev/)
- [Viaduct documentation](https://github.com/airbnb/viaduct)
- [Supabase local development](https://supabase.com/docs/guides/cli/local-development)
