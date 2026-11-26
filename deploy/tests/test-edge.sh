#!/usr/bin/env bash
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
deploy="$(cd "$here/.." && pwd)"
. "$here/lib.sh"

nginx_image="nginx:1.30.8-alpine"
suffix="$$"
network="neocat-edge-test-$suffix"
ingress_network="neocat-edge-test-ingress-$suffix"
stub_name="neocat-edge-stub-$suffix"
edge_name="neocat-edge-under-test-$suffix"
edge_address="172.28.250.10"
edge_ingress_address="172.28.251.10"
trusted_peer="172.28.251.11"
untrusted_peer="172.28.251.99"
forged_client="198.51.100.7"
real_client="203.0.113.5"

work="$(mktemp -d)"
cleanup() {
  docker rm -f "$edge_name" "$stub_name" >/dev/null 2>&1
  docker network rm "$network" "$ingress_network" >/dev/null 2>&1
  rm -rf "$work"
}
trap cleanup EXIT

if [ ! -f "$deploy/edge/nginx.conf" ]; then
  echo "FALLO: falta deploy/edge/nginx.conf"
  exit 1
fi

mkdir -p "$work/web"
echo "<html><body>web de prueba</body></html>" >"$work/web/index.html"
mkdir -p "$work/web/_expo/static/js"
echo "console.log('asset de prueba')" >"$work/web/_expo/static/js/app-test.js"
mkdir -p "$work/web/assets"
echo "<html><body>web de prueba</body></html>" >"$work/web/assets/real.html"
chmod -R a+rX "$work/web"
chmod 755 "$work"

cat >"$work/stub.conf" <<'CONF'
events {}
http {
  server {
    listen 8080;
    location / {
      default_type text/plain;
      add_header X-Content-Type-Options "backend-nosniff";
      add_header Strict-Transport-Security "max-age=1";
      add_header Referrer-Policy "backend-origin";
      add_header Content-Security-Policy "default-src backend";
      return 200 "xff=[$http_x_forwarded_for] forwarded=[$http_forwarded] host=[$http_x_forwarded_host] proto=[$http_x_forwarded_proto] port=[$http_x_forwarded_port]\n";
    }
  }
}
CONF

docker network create --internal --subnet 172.28.250.0/24 "$network" >/dev/null || exit 1
docker network create --subnet 172.28.251.0/24 "$ingress_network" >/dev/null || exit 1

docker run -d --name "$stub_name" --network "$network" --network-alias backend \
  -v "$work/stub.conf:/etc/nginx/nginx.conf:ro" "$nginx_image" >/dev/null || exit 1

docker run -d --name "$edge_name" --network "$network" --network-alias edge --ip "$edge_address" \
  --user 101:101 --read-only --cap-drop ALL --security-opt no-new-privileges:true --tmpfs /tmp \
  -v "$deploy/edge/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v "$work/web:/usr/share/nginx/html:ro" "$nginx_image" >/dev/null || exit 1

docker network connect --ip "$edge_ingress_address" --alias edge "$ingress_network" "$edge_name" || exit 1

request() {
  local peer="$1" path="$2"
  shift 2
  local header_args=()
  local header
  for header in "$@"; do
    header_args+=(--header "$header")
  done
  docker run --rm --network "$ingress_network" --ip "$peer" --entrypoint wget "$nginx_image" \
    -S -O - ${header_args[@]+"${header_args[@]}"} "http://edge:8080$path" 2>&1
}

raw_request() {
  local peer="$1" path="$2"
  docker run --rm --network "$ingress_network" --ip "$peer" --entrypoint sh "$nginx_image" \
    -c "printf 'GET $path HTTP/1.0\\r\\nHost: edge\\r\\n\\r\\n' | nc edge 8080" 2>&1
}

wait_for_edge() {
  local attempt
  for attempt in $(seq 1 30); do
    if web_is_served; then
      return 0
    fi
    sleep 1
  done
  return 1
}

status_of() {
  local output
  output="$(request "$1" "$2")"
  grep -m1 -oE 'HTTP/1\.[01] [0-9]+' <<<"$output" | grep -oE '[0-9]+$'
}

actuator_is_hidden() {
  [ "$(status_of "$untrusted_peer" /actuator/health)" = "404" ] \
    && [ "$(status_of "$untrusted_peer" /actuator)" = "404" ] \
    && [ "$(status_of "$untrusted_peer" /actuator/health/readiness)" = "404" ]
}

api_reaches_backend() {
  local output
  output="$(request "$untrusted_peer" /api/health)"
  grep -q "xff=" <<<"$output"
}

web_is_served() {
  local output
  output="$(request "$untrusted_peer" /)"
  grep -q "web de prueba" <<<"$output"
}

untrusted_peer_cannot_forge_forwarding_headers() {
  local output
  output="$(request "$untrusted_peer" /api/health \
    "X-Forwarded-For: $forged_client" "Forwarded: for=$forged_client" \
    "X-Forwarded-Host: evil.example.test" "X-Forwarded-Port: 1234" "X-Forwarded-Proto: http")"
  grep -q "xff=\[$untrusted_peer\]" <<<"$output" \
    && ! grep -q "$forged_client" <<<"$output" \
    && grep -q "forwarded=\[\]" <<<"$output" \
    && grep -q "host=\[\]" <<<"$output" \
    && grep -q "port=\[\]" <<<"$output" \
    && grep -q "proto=\[https\]" <<<"$output"
}

trusted_peer_gives_a_single_last_address() {
  local output
  output="$(request "$trusted_peer" /api/health "X-Forwarded-For: $forged_client, $real_client")"
  grep -q "xff=\[$real_client\]" <<<"$output" && ! grep -q "$forged_client" <<<"$output"
}

trusted_peer_cannot_inject_other_forwarding_headers() {
  local output
  output="$(request "$trusted_peer" /api/health "X-Forwarded-For: $real_client" \
    "Forwarded: for=$forged_client" "X-Forwarded-Host: evil.example.test" "X-Forwarded-Port: 1234")"
  grep -q "forwarded=\[\]" <<<"$output" \
    && grep -q "host=\[\]" <<<"$output" \
    && grep -q "port=\[\]" <<<"$output" \
    && grep -q "proto=\[https\]" <<<"$output"
}

header_present() {
  local output
  output="$(request "$untrusted_peer" "$1")"
  grep -qi "^ *$2:" <<<"$output"
}

hsts_without_subdomains() {
  local output line
  output="$(request "$untrusted_peer" /)"
  line="$(grep -i "^ *Strict-Transport-Security:" <<<"$output")"
  [ -n "$line" ] && ! grep -qi "includeSubDomains" <<<"$line"
}

csp_restricts_default_source() {
  local output
  output="$(request "$untrusted_peer" /)"
  grep -i "^ *Content-Security-Policy:" <<<"$output" | grep -q "default-src 'self'"
}

security_headers_on_errors() {
  local output
  output="$(raw_request "$untrusted_peer" /actuator/health)"
  grep -q "404" <<<"$output" \
    && grep -qi "^X-Content-Type-Options: nosniff" <<<"$output" \
    && grep -qi "^Strict-Transport-Security:" <<<"$output"
}

internal_route_serves_the_web() {
  local output
  output="$(request "$untrusted_peer" /communities/x)"
  [ "$(status_of "$untrusted_peer" /communities/x)" = "200" ] && grep -q "web de prueba" <<<"$output"
}

nested_internal_route_serves_the_web() {
  [ "$(status_of "$untrusted_peer" /communities/x/members/y)" = "200" ]
}

privacy_is_public() {
  local output
  output="$(request "$untrusted_peer" /privacy)"
  [ "$(status_of "$untrusted_peer" /privacy)" = "200" ] && grep -q "web de prueba" <<<"$output"
}

long_cache_only_on_static_assets() {
  local asset
  asset="$(request "$untrusted_peer" /_expo/static/js/app-test.js)"
  grep -qi "^ *Cache-Control: max-age=31536000" <<<"$asset" \
    && ! header_value_contains / Cache-Control "max-age=31536000" \
    && ! header_value_contains /privacy Cache-Control "max-age=31536000" \
    && ! header_value_contains /communities/x Cache-Control "max-age=31536000"
}

header_value_contains() {
  local output
  output="$(request "$untrusted_peer" "$1")"
  grep -i "^ *$2:" <<<"$output" | grep -qi "$3"
}

entry_page_is_not_cached() {
  header_value_contains / Cache-Control "no-cache" \
    && header_value_contains /communities/x Cache-Control "no-cache"
}

missing_static_asset_is_not_the_web() {
  [ "$(status_of "$untrusted_peer" /_expo/static/js/missing.js)" = "404" ]
}

static_assets_keep_security_headers() {
  header_present /_expo/static/js/app-test.js X-Content-Type-Options \
    && header_present /_expo/static/js/app-test.js Strict-Transport-Security
}

internal_routes_keep_security_headers() {
  header_present /communities/x Content-Security-Policy \
    && header_present /communities/x X-Content-Type-Options
}

access_log_masks_identifiers_in_paths() {
  local member="4d77fe61-bf8b-4a8d-b4d7-d054ce7647e1" user="35142736-add8-4efe-90fc-9454473898da"
  request "$trusted_peer" "/api/members/$member/contact" >/dev/null
  request "$trusted_peer" "/api/users/$user" >/dev/null
  local log
  log="$(docker logs "$edge_name" 2>&1)"
  grep -q "/api/members/:id/contact" <<<"$log" \
    && grep -q "/api/users/:id" <<<"$log" \
    && ! grep -q "$member" <<<"$log" \
    && ! grep -q "$user" <<<"$log"
}

access_log_is_free_of_personal_data() {
  request "$trusted_peer" "/api/health?token=query-sentinel" \
    "X-Forwarded-For: $real_client" "Authorization: Bearer authorization-sentinel" >/dev/null
  local log
  log="$(docker logs "$edge_name" 2>&1)"
  grep -q "/api/health" <<<"$log" \
    && ! grep -q "$real_client" <<<"$log" \
    && ! grep -q "$trusted_peer" <<<"$log" \
    && ! grep -q "$untrusted_peer" <<<"$log" \
    && ! grep -q "query-sentinel" <<<"$log" \
    && ! grep -q "authorization-sentinel" <<<"$log" \
    && ! grep -qi "authorization" <<<"$log"
}

api_redirect_is_relative() {
  local output
  output="$(docker run --rm --network "$ingress_network" --ip "$untrusted_peer" --entrypoint sh "$nginx_image" \
    -c "printf 'GET /api HTTP/1.0\\r\\nHost: evil.example.test\\r\\n\\r\\n' | nc edge 8080" 2>&1)"
  grep -qi "^Location: /api/" <<<"$output" && ! grep -qi "evil.example.test" <<<"$output"
}

header_count() {
  local output
  output="$(request "$untrusted_peer" "$1")"
  grep -ci "^ *$2:" <<<"$output"
}

api_responses_carry_each_security_header_once() {
  local header
  for header in Strict-Transport-Security X-Content-Type-Options Referrer-Policy Content-Security-Policy; do
    [ "$(header_count /api/health "$header")" = "1" ] || return 1
  done
}

api_security_headers_come_from_the_edge() {
  local output
  output="$(request "$untrusted_peer" /api/health)"
  grep -i "^ *X-Content-Type-Options:" <<<"$output" | grep -qi "nosniff" \
    && grep -i "^ *Content-Security-Policy:" <<<"$output" | grep -q "default-src 'self'" \
    && ! grep -qi "backend" <<<"$output"
}

hidden_files_are_not_found() {
  [ "$(status_of "$untrusted_peer" /.env)" = "404" ] \
    && [ "$(status_of "$untrusted_peer" /.git/config)" = "404" ] \
    && [ "$(status_of "$untrusted_peer" /assets/.hidden)" = "404" ]
}

missing_files_with_extension_are_not_the_web() {
  [ "$(status_of "$untrusted_peer" /missing.png)" = "404" ] \
    && [ "$(status_of "$untrusted_peer" /assets/missing.json)" = "404" ] \
    && [ "$(status_of "$untrusted_peer" /assets/real.html)" = "200" ]
}

api_paths_with_extension_reach_the_backend() {
  grep -q "xff=" <<<"$(request "$untrusted_peer" /api/export.json)"
}

error_pages_for_hidden_files_keep_security_headers() {
  local output
  output="$(raw_request "$untrusted_peer" /.env)"
  grep -q "404" <<<"$output" && grep -qi "^X-Content-Type-Options: nosniff" <<<"$output"
}

access_log_masks_any_number_of_identifiers() {
  local path="" identifiers=() index id
  for index in $(seq 1 12); do
    id="$(printf '%08x-aaaa-4bbb-8ccc-%012x' "$index" "$index")"
    identifiers+=("$id")
    path="$path/$id"
  done
  request "$trusted_peer" "/api/many$path" >/dev/null
  local log
  log="$(docker logs "$edge_name" 2>&1)"
  grep -qE " GET (-|/api/many[^ ]*) 200 " <<<"$log" || return 1
  for id in "${identifiers[@]}"; do
    ! grep -q "$id" <<<"$log" || return 1
  done
}

check "edge arranca con solo lectura, sin capacidades y como usuario sin privilegios" wait_for_edge

check "/actuator/** devuelve 404" actuator_is_hidden
check "la redirección de /api a /api/ es relativa y no refleja el Host" api_redirect_is_relative
check "/api/ llega al backend" api_reaches_backend
check "la web estática se sirve en /" web_is_served
check "un origen no confiable no puede falsear X-Forwarded-For, Forwarded, host ni puerto" untrusted_peer_cannot_forge_forwarding_headers
check "desde el nodo de Tailscale X-Forwarded-For llega como único valor, la última dirección" trusted_peer_gives_a_single_last_address
check "Forwarded, X-Forwarded-Host y X-Forwarded-Port entrantes se descartan y X-Forwarded-Proto es https" trusted_peer_cannot_inject_other_forwarding_headers
check "Strict-Transport-Security sin includeSubDomains" hsts_without_subdomains
check "X-Content-Type-Options presente" header_present / X-Content-Type-Options
check "Referrer-Policy presente" header_present / Referrer-Policy
check "Content-Security-Policy con default-src 'self'" csp_restricts_default_source
check "las cabeceras de seguridad también salen en las respuestas de error" security_headers_on_errors
check "una ruta interna como /communities/x devuelve 200 con la web" internal_route_serves_the_web
check "una ruta interna anidada devuelve 200 con la web" nested_internal_route_serves_the_web
check "/privacy responde 200 sin sesión" privacy_is_public
check "la caché larga solo está en /_expo/static/" long_cache_only_on_static_assets
check "la página de entrada y las rutas internas no se cachean" entry_page_is_not_cached
check "un asset inexistente en /_expo/static/ devuelve 404" missing_static_asset_is_not_the_web
check "los assets estáticos conservan las cabeceras de seguridad" static_assets_keep_security_headers
check "las rutas internas conservan las cabeceras de seguridad" internal_routes_keep_security_headers
check "el access_log sustituye los UUID del path por :id" access_log_masks_identifiers_in_paths
check "el access_log enmascara cualquier número de UUID" access_log_masks_any_number_of_identifiers
check "las respuestas de /api/ llevan cada cabecera de seguridad una sola vez" api_responses_carry_each_security_header_once
check "las cabeceras de seguridad de /api/ son las del edge" api_security_headers_come_from_the_edge
check "los ficheros ocultos devuelven 404" hidden_files_are_not_found
check "los ficheros con extensión inexistentes devuelven 404 y no la web" missing_files_with_extension_are_not_the_web
check "las rutas de /api/ con extensión llegan al backend" api_paths_with_extension_reach_the_backend
check "el 404 de un fichero oculto conserva las cabeceras de seguridad" error_pages_for_hidden_files_keep_security_headers
check "el access_log no contiene IP, consulta ni Authorization" access_log_is_free_of_personal_data

finish
