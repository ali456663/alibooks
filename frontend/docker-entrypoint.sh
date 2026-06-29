#!/bin/sh
set -eu

: "${VITE_API_URL:=http://localhost:3000}"

cat > /usr/share/nginx/html/config.js <<EOF
window.__ALIBOOKS_CONFIG__ = {
  apiUrl: "${VITE_API_URL}"
};
EOF

exec nginx -g "daemon off;"
