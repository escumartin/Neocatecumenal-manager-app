# Contrato de operación: despliegue a producción

**Feature**: `004-despliegue-produccion` | **Plan**: [plan.md](../plan.md) | **Research**: [research.md](../research.md)

Interfaces que el servicio expone al mantenedor y al servidor de casa: variables y secretos, servicios de compose,
perfiles de Spring, imágenes, órdenes del host y comprobaciones de aviso. El contrato HTTP nuevo está en
[service.openapi.yaml](./service.openapi.yaml).

## 1. Perfiles de Spring

| Perfil | Uso | Efecto |
|---|---|---|
| `prod` | contenedor `backend` | config de [§ 2](#2-configuración-del-backend); reenvío de cabeceras de R2; `ProductionConfigurationGuard` |
| `migrate` | contenedor `migrate` (con `prod`) | sin web; Flyway + validación de Hibernate; sale con 0 o ≠ 0 |
| `bootstrap` | contenedor `bootstrap` (con `prod`) | sin web; crea comunidad y administrador o aborta (R7) |
| `local` | `scripts/local-run.sh` | valores por defecto de conexión y CORS de desarrollo; prohibido junto a `prod` |

## 2. Configuración del backend

Sin valor por defecto en `prod`: si falta, no arranca y el log nombra la propiedad sin su valor (FR-002).

| Origen | Propiedad | Obligatoria en `prod` | Ejemplo sin secreto |
|---|---|---|---|
| env `DATABASE_URL` | `spring.datasource.url` | sí | `jdbc:postgresql://postgres:5432/neocatecumenal` |
| secreto `spring.datasource.username` | `spring.datasource.username` | sí | fichero en `/run/secrets/` |
| secreto `spring.datasource.password` | `spring.datasource.password` | sí | fichero en `/run/secrets/` |
| env `ALLOWED_ORIGINS` | `identity.allowed-origins` | sí, no vacía | `https://<nodo>.<tailnet>.ts.net` |
| env `PRIVACY_NOTICE_VERSION` | `identity.privacy-notice-version` | sí | `v1` |
| env `MINIMUM_APP_VERSION` | `service.minimum-app-version` | sí | `1.0.0` |
| imagen `SERVICE_REVISION` | `service.revision` | sí (la pone la imagen; `^[0-9a-f]{7,40}$`) | `86a5aa6` |
| env `IDENTITY_BOOTSTRAP_ADMINISTRATORNAME` | `identity.bootstrap.administrator-name` | solo en `bootstrap` | `Administrador` |
| env `IDENTITY_BOOTSTRAP_ADMINISTRATORCOMMUNITY` | `identity.bootstrap.administrator-community` | solo en `bootstrap` | `1` |

Secretos por `spring.config.import: optional:configtree:/run/secrets/`; el nombre del fichero es el de la propiedad.

## 3. Servicios de `deploy/compose.yaml`

Proyecto `neocatecumenal`. Ningún servicio publica puertos en el host.

| Servicio | Imagen | Redes | Notas |
|---|---|---|---|
| `postgres` | `postgres:18.7` (digest) | `db-net` | volumen `neocatecumenal-pgdata`; `POSTGRES_PASSWORD_FILE`; healthcheck `pg_isready` |
| `backend` | `${BACKEND_IMAGE}` (`ghcr.io/escumartin/neocatecumenal-backend@sha256:…` en producción) | `db-net`, `edge-net` (ambas `internal: true`: sin salida a Internet) | `SPRING_PROFILES_ACTIVE=prod`; healthcheck a `/actuator/health/readiness`; `depends_on: postgres: service_healthy` |
| `edge` | `${WEB_IMAGE}` (`ghcr.io/escumartin/neocatecumenal-web@sha256:…` en producción) | `edge-net` y `tailscale-net` (IP fija en ambas) | nginx: web estática y `/api/` → `backend:8080`; `/actuator` → 404 |
| `tailscale` | `tailscale/tailscale:v1.104.7` (digest) | `tailscale-net` (IP fija) | `TS_USERSPACE=true`, `TS_SERVE_CONFIG`, Funnel 443 → `edge`; volumen de estado propio; clave de alta por secreto |
| `migrate` | igual que `backend` | `db-net` | perfil de compose `ops`; `prod,migrate`; lo lanza el actualizador |
| `bootstrap` | igual que `backend` | `db-net` | perfil de compose `ops`; `prod,bootstrap`; `tty`, `stdin_open`, `logging.driver: none` |

Todos: `read_only: true` (con `tmpfs` donde haga falta), `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`,
límites de memoria, `restart: unless-stopped` en los de larga vida, `logging: {driver: local, options: {max-size: 10m,
max-file: "3"}}` salvo `bootstrap` (`none`). El `access_log` de `edge` usa un formato sin IP, sin consulta, sin cuerpo y
sin `Authorization`.

Perfil de compose único para tareas puntuales: `ops` (`migrate` y `bootstrap`).

`deploy/compose.smoke.yaml` (solo CI y Mac): sin `tailscale`; `edge` en `127.0.0.1:8088`;
`ALLOWED_ORIGINS=http://localhost:8088` (el puerto se cambia con `NEOCAT_SMOKE_PORT`); `BACKEND_IMAGE` y `WEB_IMAGE` por etiqueta local (esas variables salen de
`deploy/env/smoke.env`, que se pasa con `--env-file`); el volumen de PostgreSQL se llama `neocatecumenal-smoke-pgdata`.
`edge` tiene en `edge-net` la dirección fija `172.28.250.10` (subred `172.28.250.0/24`, la de confianza de `internal-proxies` en el backend) y en `tailscale-net` la `172.28.251.10`; `tailscale` tiene la `172.28.251.11` (subred `172.28.251.0/24`, la de `set_real_ip_from` en `edge`).

## 4. Imágenes

| Imagen | Etiquetas | Contenido |
|---|---|---|
| `ghcr.io/escumartin/neocatecumenal-backend` | `sha-<commit>`, `production` | jar por capas sobre `eclipse-temurin:25.0.5_8-jre-noble`; usuario sin privilegios; etiqueta OCI `revision` |
| `ghcr.io/escumartin/neocatecumenal-web` | `sha-<commit>`, `production` | `npx expo export -p web` sin `EXPO_PUBLIC_API_URL` sobre `nginx:1.30.8-alpine` |

`production` solo se mueve desde el job `publish` de `master` tras `backend`, `contract`, `app` y el humo de compose.

Visibilidad: un paquete nuevo de GHCR nace privado. Tras el primer `publish`, el mantenedor abre cada paquete en GitHub
› *Package settings* › *Change visibility* › *Public*. Alternativa si se prefiere privado: token clásico con solo
`read:packages`, guardado con `docker login ghcr.io` como root en el host (§ 6).

## 5. Órdenes del host (`deploy/bin/`)

Bash, sin comentarios, mensajes en castellano, nunca imprimen secretos ni datos de la base.

| Orden | Disparo | Hace | Salida |
|---|---|---|---|
| `neocat-update` | `neocat-update.timer`, cada 2 min | resuelve los digest de `production`; si son los de `failed.env`, no hace nada; si cambian: `migrate` con la imagen nueva → etiqueta las imágenes en marcha como `neocatecumenal-{backend,web}:previous` y escribe `previous.env` → escribe `current.env` → `up -d backend edge` (con `postgres` por dependencia) → espera `healthy` (180 s) → `up -d tailscale` si el proyecto lo define (sin condicionar el resultado) → si no llega a `healthy`, `up -d` con `previous.env` y anota los digest en `failed.env` | 0 sin cambios o desplegado y ping de éxito a `deploy`; 0 sin ping si salta una versión ya fallida; ≠ 0 y un único ping `/fail` por versión fallida (migración o salud) |
| `neocat-update apply <backend-ref> <web-ref>` | a mano y en el quickstart | los mismos pasos con referencias dadas (etiquetas locales valen: la vuelta atrás usa las etiquetas `:previous`, no digest de registro) | igual |
| `neocat-backup` | `neocat-backup.timer`, diario 03:30 | comprueba que `$NEOCAT_BACKUP_DIR` está montado → `pg_dump -Fc` sin datos de sesiones ni frenos por tubería a `restic backup --stdin` → `forget --keep-within-daily 30d --prune` → instantánea más antigua < 31 días → uso de la partición < `NEOCAT_BACKUP_MAX_USE` (80 %); `restic check` los domingos | ping `/start`, éxito o `/fail` a `backup` con texto fijo (fallo de copia, partición sin montar o partición casi llena) |
| `neocat-erasure-sync` | `neocat-erasure-sync.timer`, cada hora | si hay filas en `identity_erasure` o `community_erasure` posteriores a la última instantánea, lanza `neocat-backup` | ping a `erasure-sync` |
| `neocat-restore <snapshot> <db-destino>` | a mano | `restic dump <snapshot> neocatecumenal.dump` desde la partición → `pg_restore` en una base aparte → aplica supresiones posteriores ([data-model.md](../data-model.md) § 3) | informe por id, sin nombres |

Estado del actualizador: `$NEOCAT_HOME/state/{current,previous,failed}.env` con `BACKEND_IMAGE` y `WEB_IMAGE` (`failed.env` solo lo escribe la ejecución del temporizador y solo lo borra un despliegue correcto de otro digest; `apply` lo ignora) y `state/migrate.log` con las últimas 40 líneas de la última migración fallida, que nunca viajan al ping.

Partición de copias: distinta de la de datos (ideal: otro disco), LUKS2 + TPM2, montada en `$NEOCAT_BACKUP_DIR`;
repositorio `restic` en `$NEOCAT_BACKUP_DIR/restic`. No hay copia fuera de casa (decisión del usuario).

Rutas base: `NEOCAT_HOME` (por defecto `/srv/neocatecumenal`), `NEOCAT_CONFIG_DIR` (por defecto
`/etc/neocatecumenal`) y `NEOCAT_BACKUP_DIR` (por defecto `/mnt/neocatecumenal-backups`; en el Mac, con
`NEOCAT_BACKUP_REQUIRE_MOUNT=false`); en el Mac se apuntan a `~/desarrollo/java/tmp/neocatecumenal/004/`.

## 6. Ficheros fuera del repositorio

| Ruta en el servidor | Permisos | Contenido | Ejemplo versionado |
|---|---|---|---|
| `$NEOCAT_HOME/secrets/` | `750`, ficheros `440`, `root:neocat-secrets` (gid en `NEOCAT_SECRETS_GID`; los servicios que leen secretos entran en ese grupo con `group_add`, sin `uid`, `gid` ni `mode` en `secrets`, que compose ignora en secretos de fichero) | `spring.datasource.username` y `spring.datasource.password` (un solo rol de base de datos, que usan PostgreSQL y la app), `tailscale.authkey` (clave de alta de Tailscale) | — |
| `$NEOCAT_HOME/.env` | `600` | `ALLOWED_ORIGINS`, `PRIVACY_NOTICE_VERSION`, `MINIMUM_APP_VERSION`, `NEOCAT_SECRETS_GID` | `deploy/env/neocatecumenal.env.example` |
| `$NEOCAT_CONFIG_DIR/backup.env` | `600`, root | `RESTIC_REPOSITORY` (`$NEOCAT_BACKUP_DIR/restic`), `RESTIC_PASSWORD_FILE` (opcional), `NEOCAT_BACKUP_MAX_USE`, URLs de ping | `deploy/env/backup.env.example` |
| `$NEOCAT_CONFIG_DIR/restic.password` | `400`, root | contraseña del repositorio restic; se monta de solo lectura en el contenedor de restic, nunca como variable de entorno | — |
| `$NEOCAT_CONFIG_DIR/deploy.env` | `600`, root | URL de ping `deploy` | `deploy/env/deploy.env.example` |

Solo si GHCR queda privado: credencial de `docker login ghcr.io` (token clásico `read:packages`) en `/root/.docker/config.json`.

La contraseña de restic y la clave de recuperación LUKS también en el gestor de contraseñas del mantenedor (FR-011).

## 7. Comprobaciones de Healthchecks.io

| Comprobación | Periodo / gracia | Quién hace ping |
|---|---|---|
| `backup` | 1 día / 2 h | `neocat-backup` |
| `erasure-sync` | 1 h / 30 min | `neocat-erasure-sync` |
| `deploy` | 1 h / 1 h | `neocat-update`: éxito al final de cada ejecución correcta (también sin cambios), `/fail` si falla |

## 8. Secretos de GitHub Actions

| Secreto | Workflow | Uso |
|---|---|---|
| `GITHUB_TOKEN` (`packages: write`) | `ci.yml` › `publish` | subir a GHCR |
| `EXPO_TOKEN` | `mobile-build.yml`, `mobile-update.yml` | EAS Build, Submit y Update |
