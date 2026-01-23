# Viaduct Template

A starter template demonstrating a three-tier architecture with GraphQL middleware.

## Deploy in 5 Minutes

> **Before clicking Deploy**, complete Steps 1-3 below to get your Supabase credentials ready.

### Step 1: Create a Supabase Project

1. Go to [supabase.com](https://supabase.com) and sign in
2. Click **New Project**
3. Choose a name and password, then click **Create**
4. Wait ~2 minutes for the project to provision

### Step 2: Enable Email Authentication

1. In your Supabase project, go to **Authentication** (left sidebar)
2. Click **Providers**
3. Find **Email** and make sure it's **Enabled**

### Step 3: Get Your API Credentials

Go to **Settings** → **API** in your Supabase dashboard. You'll need these 3 values:

| Render Field | Where to Find It |
|--------------|------------------|
| `SUPABASE_URL` | **Project URL** (e.g., `https://abcdefg.supabase.co`) |
| `SUPABASE_ANON_KEY` | **anon public** key (starts with `eyJ...`) |
| `SUPABASE_SERVICE_ROLE_KEY` | **service_role** key (starts with `eyJ...`, keep secret!) |

### Step 4: Deploy to Render

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy)

1. Click the button above
2. Connect your GitHub account if prompted
3. Paste the 3 values from Step 3 into the form
4. Click **Apply**

The deploy takes ~5 minutes. Once complete, visit your frontend URL to start using the app!

---

## Architecture

```
React Frontend (Vite)
    │
    │ GraphQL
    ▼
Viaduct Backend (Kotlin/Ktor)
    │
    │ Supabase Client
    ▼
Supabase (PostgreSQL + Auth)
```

- **Frontend**: React + Vite with shadcn/ui components
- **Backend**: Viaduct GraphQL middleware (Kotlin/Ktor)
- **Database**: Supabase PostgreSQL with Row Level Security

## Local Development

```bash
# Install tools (requires mise: https://mise.jdx.dev)
mise install

# Start everything
mise run dev
```

Services:
- Frontend: http://localhost:5173
- GraphQL API: http://localhost:8080/graphql
- GraphiQL: http://localhost:8080/graphiql
- Supabase Studio: http://127.0.0.1:54323

## Documentation

See [CLAUDE.md](./CLAUDE.md) for complete documentation including:
- Development commands
- Architecture details
- GraphQL API reference
- Troubleshooting guide

## Costs

- **Supabase**: Free tier (500MB database, 50K auth users)
- **Render Frontend**: Free (static site)
- **Render Backend**: Free (512MB RAM, spins down after inactivity)
  - Upgrade to Starter ($7/mo) for always-on

## Project Origin

Bootstrapped with [Lovable](https://lovable.dev) and extended with Viaduct backend.
