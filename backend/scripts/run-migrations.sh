#!/bin/bash
# Run Supabase migrations on startup
# This script runs before the main application starts

set -e

MIGRATIONS_DIR="/app/migrations"
MIGRATION_TABLE="schema_migrations"

# Support both old and new env var names
SUPABASE_KEY="${SUPABASE_PUBLISHABLE_KEY:-$SUPABASE_ANON_KEY}"
SUPABASE_SECRET="${SUPABASE_SECRET_KEY:-$SUPABASE_SERVICE_ROLE_KEY}"

# Extract project ref from JWT key (the "ref" claim in the JWT payload)
extract_project_ref() {
    local key="$1"
    # JWT is base64url encoded, split by dots: header.payload.signature
    local payload=$(echo "$key" | cut -d'.' -f2)
    # Add padding if needed and decode
    local padded_payload="$payload"
    case $((${#payload} % 4)) in
        2) padded_payload="${payload}==" ;;
        3) padded_payload="${payload}=" ;;
    esac
    # Decode and extract "ref" field
    echo "$padded_payload" | base64 -d 2>/dev/null | grep -o '"ref":"[^"]*"' | cut -d'"' -f4
}

# Construct DATABASE_URL from Supabase credentials if not explicitly set
if [ -z "$DATABASE_URL" ]; then
    if [ -n "$SUPABASE_SECRET" ]; then
        # Try to get project ref from explicit URL first, then from the publishable key
        if [ -n "$SUPABASE_URL" ]; then
            PROJECT_REF=$(echo "$SUPABASE_URL" | sed -E 's|https?://([^.]+)\.supabase\.co.*|\1|')
        elif [ -n "$SUPABASE_KEY" ]; then
            PROJECT_REF=$(extract_project_ref "$SUPABASE_KEY")
            echo "[Migrations] Extracted project ref from publishable key"
        fi

        if [ -n "$PROJECT_REF" ] && [ "$PROJECT_REF" != "$SUPABASE_URL" ]; then
            # Construct database URL using Supabase's direct connection
            # Format: postgresql://postgres.[ref]:[key]@db.[ref].supabase.co:5432/postgres
            DATABASE_URL="postgresql://postgres.${PROJECT_REF}:${SUPABASE_SECRET}@db.${PROJECT_REF}.supabase.co:5432/postgres"
            echo "[Migrations] Constructed database URL from Supabase credentials"
        else
            echo "[Migrations] Could not extract project ref from credentials"
            echo "[Migrations] Skipping migrations"
            exit 0
        fi
    else
        echo "[Migrations] SUPABASE_SECRET_KEY not set"
        echo "[Migrations] Skipping migrations - run manually with: supabase db push"
        exit 0
    fi
fi

echo "[Migrations] Starting migration check..."

# Check if psql is available
if ! command -v psql &> /dev/null; then
    echo "[Migrations] psql not found - skipping migrations"
    echo "[Migrations] Migrations should be run manually: supabase db push"
    exit 0
fi

# Check if migrations directory exists and has files
if [ ! -d "$MIGRATIONS_DIR" ] || [ -z "$(ls -A $MIGRATIONS_DIR/*.sql 2>/dev/null)" ]; then
    echo "[Migrations] No migration files found in $MIGRATIONS_DIR"
    exit 0
fi

# Test database connection
echo "[Migrations] Testing database connection..."
if ! psql "$DATABASE_URL" -c "SELECT 1;" > /dev/null 2>&1; then
    echo "[Migrations] WARNING: Could not connect to database"
    echo "[Migrations] Skipping migrations - run manually with: supabase db push"
    exit 0
fi

# Create migrations tracking table if it doesn't exist
echo "[Migrations] Ensuring migration tracking table exists..."
psql "$DATABASE_URL" -q <<EOF
CREATE TABLE IF NOT EXISTS $MIGRATION_TABLE (
    version VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
EOF

# Get list of applied migrations
APPLIED=$(psql "$DATABASE_URL" -t -A -c "SELECT version FROM $MIGRATION_TABLE ORDER BY version;")

# Run each migration file in order
for migration_file in $(ls -1 "$MIGRATIONS_DIR"/*.sql 2>/dev/null | sort); do
    filename=$(basename "$migration_file")
    version="${filename%.sql}"

    # Check if already applied
    if echo "$APPLIED" | grep -q "^${version}$"; then
        echo "[Migrations] Already applied: $filename"
        continue
    fi

    echo "[Migrations] Applying: $filename"

    # Run the migration
    if psql "$DATABASE_URL" -q -f "$migration_file"; then
        # Record successful migration
        psql "$DATABASE_URL" -q -c "INSERT INTO $MIGRATION_TABLE (version) VALUES ('$version');"
        echo "[Migrations] Successfully applied: $filename"
    else
        echo "[Migrations] ERROR: Failed to apply $filename"
        exit 1
    fi
done

echo "[Migrations] All migrations complete!"
