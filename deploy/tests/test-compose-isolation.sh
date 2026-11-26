#!/usr/bin/env bash
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
deploy="$(cd "$here/.." && pwd)"
. "$here/lib.sh"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

export BACKEND_IMAGE="neocatecumenal-backend:test-isolation"
export WEB_IMAGE="neocatecumenal-web:test-isolation"
export NEOCAT_HOME="$work/home"
export ALLOWED_ORIGINS="https://isolation.example.test"
export PRIVACY_NOTICE_VERSION="v1"
export MINIMUM_APP_VERSION="1.0.0"
export NEOCAT_SECRETS_GID="1900"

render() {
  docker compose --profile ops "$@" config --format json
}

render -f "$deploy/compose.yaml" >"$work/base.json" 2>"$work/base.err" || true
if [ -f "$deploy/compose.smoke.yaml" ]; then
  render -f "$deploy/compose.yaml" -f "$deploy/compose.smoke.yaml" >"$work/smoke.json" 2>"$work/smoke.err" || true
else
  : >"$work/smoke.json"
fi

assert() {
  python3 -I - "$work/base.json" "$work/smoke.json" "$1" <<'PY'
import json, sys

base_path, smoke_path, rule = sys.argv[1:4]
with open(base_path) as handle:
    text = handle.read()
if not text.strip():
    sys.exit(1)
base = json.loads(text)
services = base["services"]
networks = base.get("networks", {})
expected_core = {"postgres", "backend", "migrate", "bootstrap"}


def memory_limited(service):
    if service.get("mem_limit"):
        return True
    limits = service.get("deploy", {}).get("resources", {}).get("limits", {})
    return bool(limits.get("memory"))


def run():
    if rule == "secret-readers-share-the-secrets-group":
        readers = [s for s in services.values() if s.get("secrets")]
        return len(readers) >= 5 and all(
            "1900" in [str(g) for g in s.get("group_add", [])]
            and all(not ({"uid", "gid", "mode"} & set(ref)) for ref in s["secrets"])
            for s in readers
        )
    if rule == "core-services-present":
        return expected_core <= set(services)
    if rule == "no-published-ports":
        return all(not s.get("ports") for s in services.values())
    if rule == "postgres-without-ports":
        return not services["postgres"].get("ports")
    if rule == "smoke-only-edge-ports":
        with open(smoke_path) as handle:
            smoke_text = handle.read()
        if not smoke_text.strip():
            return False
        smoke = json.loads(smoke_text)["services"]
        published = {n: s["ports"] for n, s in smoke.items() if s.get("ports")}
        if set(published) != {"edge"}:
            return False
        ports = published["edge"]
        return len(ports) == 1 and ports[0].get("host_ip") == "127.0.0.1" and str(ports[0].get("published")) == "8088"
    if rule == "db-net-internal":
        return networks.get("db-net", {}).get("internal") is True
    if rule == "read-only":
        return all(s.get("read_only") is True for s in services.values())
    if rule == "cap-drop-all":
        return all("ALL" in s.get("cap_drop", []) for s in services.values())
    if rule == "no-new-privileges":
        return all("no-new-privileges:true" in s.get("security_opt", []) for s in services.values())
    if rule == "memory-limits":
        return all(memory_limited(s) for s in services.values())
    if rule == "log-rotation":
        for name, s in services.items():
            logging = s.get("logging", {})
            if name == "bootstrap":
                if logging.get("driver") != "none":
                    return False
            else:
                options = logging.get("options", {})
                if logging.get("driver") != "local" or options.get("max-size") != "10m" or str(options.get("max-file")) != "3":
                    return False
        return True
    if rule == "no-docker-socket":
        for s in services.values():
            for volume in s.get("volumes", []):
                if "docker.sock" in str(volume.get("source", "")) or "docker.sock" in str(volume.get("target", "")):
                    return False
        return True
    if rule == "db-net-members":
        members = {n for n, s in services.items() if "db-net" in s.get("networks", {})}
        return members == expected_core
    if rule == "postgres-only-db-net":
        return set(services["postgres"].get("networks", {})) == {"db-net"}
    if rule == "edge-net-fixed-subnet":
        config = networks.get("edge-net", {}).get("ipam", {}).get("config", [])
        return any(entry.get("subnet") for entry in config)
    if rule == "edge-services-present":
        return {"edge", "tailscale"} <= set(services)
    if rule == "edge-net-members":
        members = {n for n, s in services.items() if "edge-net" in s.get("networks", {})}
        return members == {"backend", "edge"}
    if rule == "tailscale-net-members":
        members = {n for n, s in services.items() if "tailscale-net" in s.get("networks", {})}
        return members == {"edge", "tailscale"}
    if rule == "edge-net-internal":
        return networks.get("edge-net", {}).get("internal") is True
    if rule == "tailscale-net-reaches-the-internet":
        return networks.get("tailscale-net", {}).get("internal") is not True
    if rule == "backend-without-internet":
        backend_networks = services["backend"].get("networks", {})
        return bool(backend_networks) and all(networks[name].get("internal") is True for name in backend_networks)
    if rule == "tailscale-net-fixed-subnet":
        config = networks.get("tailscale-net", {}).get("ipam", {}).get("config", [])
        return any(entry.get("subnet") == "172.28.251.0/24" for entry in config)
    if rule == "edge-fixed-addresses":
        edge = services["edge"]["networks"]
        tailscale = services["tailscale"]["networks"]["tailscale-net"]
        return (edge["edge-net"].get("ipv4_address") == "172.28.250.10"
                and edge["tailscale-net"].get("ipv4_address") == "172.28.251.10"
                and tailscale.get("ipv4_address") == "172.28.251.11")
    if rule == "tailscale-userspace":
        tailscale = services["tailscale"]
        environment = tailscale.get("environment", {})
        return (str(environment.get("TS_USERSPACE")).lower() == "true"
                and not tailscale.get("devices")
                and "NET_ADMIN" not in tailscale.get("cap_add", []))
    if rule == "tailscale-state-volume":
        volumes = services["tailscale"].get("volumes", [])
        return any(v.get("type") == "volume" and v.get("source") == "neocatecumenal-tailscale-state" for v in volumes)
    if rule == "smoke-without-tailscale":
        with open(smoke_path) as handle:
            smoke_text = handle.read()
        if not smoke_text.strip():
            return False
        return "tailscale" not in json.loads(smoke_text)["services"]
    if rule == "restart-policy":
        return all(s.get("restart") == "unless-stopped" for n, s in services.items() if n not in {"migrate", "bootstrap"})
    return False


sys.exit(0 if run() else 1)
PY
}

check "compose.yaml se resuelve y define postgres, backend, migrate y bootstrap" assert core-services-present
check "ningún servicio publica puertos en compose.yaml" assert no-published-ports
check "postgres no publica puerto" assert postgres-without-ports
if [ -f "$deploy/compose.smoke.yaml" ]; then
  check "con compose.smoke.yaml solo edge publica, en 127.0.0.1:8088" assert smoke-only-edge-ports
  check "compose.smoke.yaml no levanta tailscale" assert smoke-without-tailscale
fi
check "los servicios con secretos entran en el grupo compartido y no declaran uid, gid ni modo" assert secret-readers-share-the-secrets-group
check "compose.yaml define edge y tailscale" assert edge-services-present
check "solo backend y edge están en edge-net" assert edge-net-members
check "solo edge y tailscale están en tailscale-net" assert tailscale-net-members
check "edge-net es interna" assert edge-net-internal
check "tailscale-net no es interna: tailscale necesita salir a Internet" assert tailscale-net-reaches-the-internet
check "el backend solo está en redes internas, sin salida a Internet" assert backend-without-internet
check "tailscale-net tiene subred fija" assert tailscale-net-fixed-subnet
check "edge y tailscale tienen dirección fija" assert edge-fixed-addresses
check "tailscale corre en modo userspace sin NET_ADMIN ni dispositivos" assert tailscale-userspace
check "tailscale guarda su estado en un volumen propio" assert tailscale-state-volume
check "db-net es interna" assert db-net-internal
check "edge-net tiene subred fija" assert edge-net-fixed-subnet
check "todos los servicios son de solo lectura" assert read-only
check "todos los servicios sueltan todas las capacidades" assert cap-drop-all
check "todos los servicios llevan no-new-privileges" assert no-new-privileges
check "todos los servicios tienen límite de memoria" assert memory-limits
check "todos los servicios rotan logs con driver local (bootstrap sin logs)" assert log-rotation
check "ningún servicio monta docker.sock" assert no-docker-socket
check "solo postgres, backend, migrate y bootstrap están en db-net" assert db-net-members
check "postgres solo está en db-net" assert postgres-only-db-net
check "los servicios de larga vida se reinician salvo parada manual" assert restart-policy

finish
