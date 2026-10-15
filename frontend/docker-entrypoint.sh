#!/bin/sh
set -eu

if [ "${VITE_API_URL:-}" != "/api" ]; then
  echo "VITE_API_URL must be /api for the production frontend" >&2
  exit 1
fi

cat > /usr/share/nginx/html/config.js <<EOF
window.__ALIBOOKS_CONFIG__ = {
  apiUrl: "/api"
};
EOF

exec nginx -g "daemon off;"
