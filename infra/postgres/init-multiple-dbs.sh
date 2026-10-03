#!/bin/bash
# Init script for postgres container (mounted to /docker-entrypoint-initdb.d/).
# Chay 1 lan duy nhat khi volume postgres con trong (lan dau docker compose up).
# Docs muc 4.3: tao 7 database, moi service 1 user rieng chi co quyen tren DB cua minh,
# bat extension postgis cho property_db.
set -e

# $POSTGRES_USER / $POSTGRES_DB do image postgres cung cap san.
# $POSTGRES_MULTIPLE_DATABASES: danh sach cach nhau boi dau phay, vd:
#   auth_db,user_db,property_db,listing_db,contract_db,billing_db,asset_db
# Dung if (khong dung exit) vi entrypoint postgres co the source file nay thay vi execute.
if [ -z "$POSTGRES_MULTIPLE_DATABASES" ]; then
  echo "POSTGRES_MULTIPLE_DATABASES is empty, skipping multi-db init."
else

# Escape dau nhay don trong password de ghep cau SQL an toan.
escape_sql() {
  printf "%s" "$1" | sed "s/'/''/g"
}

for raw in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
  db=$(echo "$raw" | xargs)
  [ -z "$db" ] && continue

  # Tao database (bo qua neu trung POSTGRES_DB mac dinh vi image da tu tao).
  if [ "$db" != "$POSTGRES_DB" ]; then
    if psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
        -tc "SELECT 1 FROM pg_database WHERE datname = '$db'" | grep -q 1; then
      echo "Database $db already exists, skipping CREATE."
    else
      echo "Creating database: $db"
      psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
        -c "CREATE DATABASE \"$db\";"
    fi
  fi

  # Tim user rieng theo quy uoc <PREFIX>_DB_USER / <PREFIX>_DB_PASSWORD,
  # vd auth_db -> AUTH_DB_USER / AUTH_DB_PASSWORD.
  prefix=$(echo "$db" | sed 's/_db$//' | tr '[:lower:]' '[:upper:]')
  user_var="${prefix}_DB_USER"
  pass_var="${prefix}_DB_PASSWORD"
  db_user="${!user_var}"
  db_pass="${!pass_var}"

  if [ -n "$db_user" ] && [ -n "$db_pass" ]; then
    echo "Creating role $db_user with grants on $db"
    esc_pass=$(escape_sql "$db_pass")
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$db_user') THEN
    CREATE ROLE "$db_user" LOGIN PASSWORD '$esc_pass';
  END IF;
END
\$\$;
GRANT CONNECT ON DATABASE "$db" TO "$db_user";
EOSQL
    # Quyen tren schema public cua dung DB do (Flyway can CREATE de tao bang migration).
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" <<EOSQL
GRANT ALL ON SCHEMA public TO "$db_user";
ALTER DEFAULT PRIVILEGES FOR ROLE "$POSTGRES_USER" IN SCHEMA public
  GRANT ALL ON TABLES TO "$db_user";
ALTER DEFAULT PRIVILEGES FOR ROLE "$POSTGRES_USER" IN SCHEMA public
  GRANT ALL ON SEQUENCES TO "$db_user";
EOSQL
  else
    echo "No ${user_var}/${pass_var} set, keeping superuser-only access for $db (dev fallback)."
  fi

  # Docs muc 4.3 + muc 2: bat postgis cho property_db (tim ban kinh bang ST_DWithin).
  if [ "$db" = "property_db" ]; then
    echo "Enabling postgis extension on property_db"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" \
      -c "CREATE EXTENSION IF NOT EXISTS postgis;"
  fi
done

echo "Multi-db init done."
fi
