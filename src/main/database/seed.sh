set -euo pipefail

: "${SUPERADMIN_PASSWORD:?SUPERADMIN_PASSWORD must be set}"
SUPERADMIN_USERNAME="${SUPERADMIN_USERNAME:-superadmin}"
SUPERADMIN_EMAIL="${SUPERADMIN_EMAIL:-superadmin@example.com}"

HASH=$(printf '%s' "$SUPERADMIN_PASSWORD" | sha256sum | cut -d' ' -f1)

mysql -uroot -p"$MYSQL_ROOT_PASSWORD" IBDCServer_db <<SQL
INSERT INTO users (user_id, username_or_email, email, password_hash, is_admin, is_superadmin)
SELECT UUID(), '${SUPERADMIN_USERNAME}', '${SUPERADMIN_EMAIL}', '${HASH}', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE is_superadmin = 1);
SQL