# E2E Tests

This directory contains end-to-end tests for the Viaduct Template application using Playwright.

## Prerequisites

Before running the E2E tests, ensure the following services are running:

1. **Supabase** (local instance on port 54321)
2. **Backend** (GraphQL server on port 8080)
3. **Frontend** (Vite dev server - will be started automatically if not running)

### Starting Required Services

Using mise:
```bash
# Start Supabase
mise run deps-start

# Start backend (in a separate terminal)
mise run backend
```

Or manually:
```bash
# Start Supabase
supabase start

# Start backend (in a separate terminal)
cd backend && ./gradlew run
```

## Running Tests

### All tests
```bash
npm run test:e2e
```

### With UI mode (interactive)
```bash
npm run test:e2e:ui
```

### In headed mode (see the browser)
```bash
npm run test:e2e:headed
```

### Debug mode
```bash
npm run test:e2e:debug
```

## Test Structure

### policy-checking.spec.ts

Tests the group membership policy enforcement across the entire stack:

1. **Setup**: Creates three test users (e2euser1, e2euser2, e2euser3)
2. **User 1**: Creates a group and checklist item
3. **User 1**: Adds User 2 to the group
4. **User 2**: Verifies access to group and checklist item (✅ ALLOWED)
5. **User 3**: Verifies NO access to group or checklist item (✅ BLOCKED)
6. **API Test**: Verifies User 3 gets empty results from GraphQL API
7. **API Test**: Verifies User 2 gets full results from GraphQL API

## Test Flow

```
┌─────────────┐
│   User 1    │ (Owner)
│  Creates    │────┐
│   Group     │    │
└─────────────┘    │
                   ▼
              ┌─────────┐
              │  Group  │
              │  +Item  │
              └─────────┘
                   │
        ┌──────────┼──────────┐
        │                     │
        ▼                     ▼
   ┌─────────┐          ┌─────────┐
   │ User 2  │          │ User 3  │
   │ MEMBER  │          │NOT MEMBER│
   │   ✅    │          │    ❌    │
   └─────────┘          └─────────┘
   Can Access          Cannot Access
```

## What's Being Tested

The tests verify that the `@requiresGroupMembership` policy directive correctly enforces access control at both:

- **Frontend level**: UI shows/hides content based on membership
- **Backend level**: GraphQL API returns filtered data based on membership

### Frontend Testing
- User interface properly displays groups only for members
- Checklist items are visible only to group members
- Non-members see empty state messages

### Backend Testing
- Direct GraphQL queries respect group membership
- Non-members receive empty arrays for protected resources
- Members receive full data for resources they have access to

## Troubleshooting

### Tests fail with "Address already in use"
Make sure the backend and frontend aren't already running on the required ports.

### Tests fail with "Connection refused"
Ensure Supabase is running: `supabase status`

### Tests are flaky
The test suite is configured to run serially (not in parallel) to avoid race conditions with user creation and group membership.

## CI/CD

When running in CI:
- Tests will retry up to 2 times on failure
- The frontend webServer will be automatically started
- Make sure to start Supabase and the backend as part of your CI pipeline

Example GitHub Actions workflow:
```yaml
- name: Start Supabase
  run: supabase start

- name: Start Backend
  run: cd backend && ./gradlew run &

- name: Run E2E Tests
  run: npm run test:e2e
```
