# Quickstart: validar el despliegue a producción

**Feature**: `004-despliegue-produccion` | **Plan**: [plan.md](./plan.md) | **Contratos**:
[service.openapi.yaml](./contracts/service.openapi.yaml), [operations.md](./contracts/operations.md) | **Modelo**:
[data-model.md](./data-model.md)

Guía para comprobar de punta a punta, primero en el Mac y después en el servidor, que la feature funciona. No contiene
implementación. Los nombres de servicios, órdenes y variables están en [operations.md](./contracts/operations.md).

## Requisitos previos

- Java 25, Node 24, Rancher Desktop con Docker y `docker compose`.
- `export TESTCONTAINERS_RYUK_DISABLED=true` con Rancher Desktop.
- Para § 6: cuenta de Tailscale con HTTPS y Funnel activados en el tailnet, y la app Expo de pruebas o una build
  `preview`.
- Para § 3-5: `export NEOCAT_HOME=~/desarrollo/java/tmp/neocatecumenal/004/home NEOCAT_CONFIG_DIR=~/desarrollo/java/tmp/neocatecumenal/004/etc`
  y `NEOCAT_BACKUP_DIR=~/desarrollo/java/tmp/neocatecumenal/004/backups NEOCAT_BACKUP_REQUIRE_MOUNT=false` (en el Mac
  no hay partición aparte; `RESTIC_REPOSITORY=$NEOCAT_BACKUP_DIR/restic`).
- Navegador para `http://localhost:8088`: Chrome o Firefox. Safari no guarda la cookie `__Host-session` (`Secure`) sobre
  `http://localhost`, así que el inicio de sesión web no funciona ahí; en Safari, solo por la URL https de Funnel (§ 6).
- Datos sintéticos: nombres y teléfonos inventados (`600000001`…), nunca reales.
- Logs y temporales en `~/desarrollo/java/tmp/neocatecumenal/004/`.

## 0. Exploración previa (T001): IP real tras Funnel

1. Levantar `tailscale` + un `edge` de prueba que registre `X-Forwarded-For` y `$remote_addr`.
2. Abrir la URL de Funnel desde un móvil con datos (Wi-Fi apagada).

Esperado: la última dirección de `X-Forwarded-For` es la IP pública del móvil. Si no lo es, parar y subir la decisión
del plan B (plan, «Decisiones tomadas»).

## 1. Gates automáticos y escenarios cubiertos por tests

```bash
npx --yes @redocly/cli@2.76.0 lint api/openapi.yaml --config api/redocly.yaml
cd backend && ./mvnw -B verify
cd app && npm run lint && npm run typecheck && npm test && npx expo export -p web
```

Esperado: exit 0.

| Escenario | Spec | Nivel de test | Resultado esperado |
|---|---|---|---|
| Carga inicial sin administrador | US2-1, FR-007 | aplicación + `BootstrapRunnerIT` | 1 comunidad, 1 administrador con cambio obligatorio; contraseña solo en la consola |
| Carga inicial con administrador | US2-2 | aplicación + IT | aborta, exit ≠ 0, sin escrituras (sin llamadas a `CommunityProvisioner` ni `save`) |
| Carga inicial sin comunidad | caso límite | IT | aborta sin crear nada |
| Contraseña fuera de logs | US2-3 | IT de logs | el log no contiene la contraseña |
| Falta configuración obligatoria | US1-3, FR-002 | `@SpringBootTest` con `prod` | no arranca; el log nombra la propiedad y no muestra valores |
| `prod` con `local` | FR-003 | idem | no arranca |
| Migración correcta y rota | FR-006, US3-3 | Testcontainers con `migrate` | exit 0 y esquema nuevo; con una migración rota en `SPRING_FLYWAY_LOCATIONS`, exit ≠ 0 y `flyway_schema_history` sin cambios |
| IP del cliente | FR-004 | `@SpringBootTest` con `prod` | desde la IP de `edge`, `X-Forwarded-For` se respeta; desde otra IP, se ignora |
| Compatibilidad | FR-021, US3-4 | REST | `200` sin sesión con `minimumAppVersion` y `serviceRevision` |
| Supresiones anotadas | FR-009 | aplicación + Testcontainers | borrar usuario, borrar hermano y retirar un consentimiento crean su fila; sin nombres ni valores |
| Purga del registro | FR-009 | aplicación | se borran solo filas de más de 35 días |
| App por debajo del mínimo | US5-4 | Jest | `1.2.0 < 1.10.0` bloquea; igual o mayor no; error de red no bloquea; web no consulta |

## 2. Humo de imágenes en el Mac

`deploy/tests/smoke.sh` hace todo esto con un `NEOCAT_HOME` temporal y desmonta el proyecto al acabar; a mano, hace falta
`NEOCAT_HOME` apuntando a un directorio con `secrets/spring.datasource.username` y `secrets/spring.datasource.password`.

```bash
docker build --build-context api=api -t neocatecumenal-backend:local --build-arg SERVICE_REVISION=$(git rev-parse --short HEAD) backend
docker build -t neocatecumenal-web:local -f deploy/edge/Dockerfile .
docker compose --env-file deploy/env/smoke.env -f deploy/compose.yaml -f deploy/compose.smoke.yaml up -d --wait
curl -fsS http://localhost:8088/api/health
curl -fsS http://localhost:8088/api/compatibility
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8088/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8088/communities/x
docker compose --env-file deploy/env/smoke.env -f deploy/compose.yaml -f deploy/compose.smoke.yaml ps
```

Esperado: `{"status":"UP"}`; `serviceRevision` = commit local; `/actuator/health` → `404`; ruta interna → `200` con la
web (US6-1); ningún servicio con puertos publicados salvo `edge` en `127.0.0.1:8088`; `postgres` sin puerto.

## 3. Primer administrador

```bash
docker compose --env-file deploy/env/smoke.env -f deploy/compose.yaml -f deploy/compose.smoke.yaml --profile ops run --rm \
  -e IDENTITY_BOOTSTRAP_ADMINISTRATORNAME=Administrador -e IDENTITY_BOOTSTRAP_ADMINISTRATORCOMMUNITY=1 bootstrap
```

Esperado (US2-1): identificador y contraseña en la terminal. Repetir la orden: aborta con «ya existe un administrador» y
exit ≠ 0 (US2-2). Recuentos en la base: 1 comunidad, 1 usuario, 0 salones, 0 eventos (US1-4, SC-004). Buscar la
contraseña en `docker compose logs` y en `journalctl`: 0 apariciones (US2-3, SC-005). Iniciar sesión en
`http://localhost:8088` y comprobar el cambio obligatorio.

## 4. Copia, restauración y supresiones

1. Con el administrador, crear una comunidad y tres hermanos sintéticos con móvil.
2. `neocat-backup` con el `backup.env` de prueba y una URL de ping de una comprobación de prueba (o sin URL).
3. Borrar un hermano y retirar el consentimiento del móvil de otro.
4. Cronometrar: `neocat-restore latest neocatecumenal_restore` siguiendo `docs/operacion.md`.

Esperado: `restic snapshots` muestra la instantánea de hoy en `$NEOCAT_BACKUP_DIR/restic` (US4-1); la base de
producción no cambia (US4-2); en la restaurada el hermano borrado no está y el móvil retirado es `null`; el informe solo
lista ids; `identity_session` está vacía; < 30 min (SC-003). Retención (US4-3): con instantáneas sintéticas fechadas (`restic backup --time`) de hace 10, 29, 31 y 40 días,
`neocat-backup` deja las de 10 y 29 y la de hoy (`restic snapshots`); con `forget` desactivado a mano
y una instantánea de 40 días, `neocat-backup` sale con ≠ 0 por la comprobación de antigüedad (purga del registro de
supresiones protegida). Partición llena: con `NEOCAT_BACKUP_MAX_USE=1`, `neocat-backup` sale con ≠ 0 y hace ping `/fail` «partición de
copias casi llena»; en el servidor, con `NEOCAT_BACKUP_REQUIRE_MOUNT=true` y la partición desmontada, falla sin
escribir en el disco de datos. Fallo: con un repositorio inaccesible, `neocat-backup` sale con ≠ 0 y hace ping
`/fail` (FR-020; con una URL de Healthchecks real, llega el correo).
Restauración tras fallo del disco de datos (runbook, en el servidor): con el volumen de datos borrado, montar la
partición y `neocat-restore latest neocatecumenal` lo deja en servicio.

## 5. Despliegue y vuelta atrás

1. `neocat-update apply` con las imágenes locales de § 2: sin cambios → exit 0.
2. Construir otra imagen con `SERVICE_REVISION=$(git rev-parse --short HEAD~1)` (cumple `^[0-9a-f]{7,40}$`),
   etiquetarla `neocatecumenal-backend:probe` y aplicarla: `/api/compatibility` muestra ese commit (US3-4),
   `current.env` apunta a `:probe` y `previous.env` a `neocatecumenal-backend:previous` (la imagen de § 2).
3. Construir una imagen con una migración que falla y aplicarla: el paso `migrate` falla, `backend` sigue con `probe`
   atendiendo y la orden sale con ≠ 0 y ping `/fail` (US3-3, caso límite). Una ejecución correcta posterior hace ping de éxito y la
   comprobación `deploy` vuelve a verde.
4. Construir una imagen de prueba que migra bien pero no arranca (imagen derivada con `ENV SERVER_PORT=9999`: `migrate` no abre puerto y pasa, `backend` escucha donde no mira el `healthcheck`) y aplicarla:
   `backend` no llega a `healthy` y la orden vuelve sola a `previous.env`
   (la imagen `:probe`, etiquetada `:previous` antes del cambio).
5. Con esa imagen rota como `production` (registro local), una segunda ejecución sin argumentos no toca el servicio ni
   repite el ping `/fail`; con otro digest en `production` vuelve a intentarlo (`state/failed.env`).

## 6. Funnel y móviles (Mac primero, servidor después)

1. Levantar `deploy/compose.yaml` completo con `tailscale` y `ALLOWED_ORIGINS=https://<nodo>.<tailnet>.ts.net`.
2. Desde un móvil con datos: abrir la URL, comprobar el candado, iniciar sesión con el administrador y llegar a la
   agenda cronometrando (US1, SC-001). `http://<nodo>…` no responde (US1-1).
3. Comprobar en `identity_login_ip_throttle` tras un fallo de contraseña que la IP es la del móvil, no la de `edge`
   (FR-004).
4. Build `preview` de EAS con `EXPO_PUBLIC_API_URL` a la URL de Funnel (se pone en el bloque `env` del perfil `preview` de `app/eas.json`, o como variable de entorno de EAS del entorno `preview`; el entorno de la terminal no llega a la build en la nube): iniciar sesión y crear una reserva que se ve
   en la web (US5). Con `MINIMUM_APP_VERSION` por encima de la versión de la build: pantalla de actualización (US5-4).
5. `eas update --channel preview` con un cambio de texto: llega al móvil en < 1 h (US5-2, SC-006).

## 7. CD real (con el servidor)

1. Merge de un cambio menor a `master`.
2. `ci.yml` › `publish` en verde; `ghcr.io/…:production` apunta a `sha-<commit>`.
3. En < 15 min, `/api/compatibility` muestra el commit (US3-1, SC-002).
4. Una PR con checks en rojo no llega a `master`; si un push a `master` falla, `publish` no corre (US3-2).
5. Con el servidor apagado durante un merge: al encenderlo, en ≤ 2 min aplica la última versión (caso límite).

## 8. Revisión del servidor (runbook)

- `lsblk -f` muestra el disco de datos y la partición de copias, distintos, en `crypto_LUKS` (FR-019, FR-009);
  `findmnt $NEOCAT_BACKUP_DIR` la muestra montada.
- `ss -tlnp` no muestra puertos del proyecto escuchando en el host; el router sin reenvíos (FR-017).
- `docker network inspect`: `db-net` y `edge-net` con `internal: true`; `db-net` solo con `backend`, `migrate` y `bootstrap` además de `postgres`; `edge-net` solo con `backend` y `edge`.
- La ACL de Tailscale impide que `tag:neocatecumenal` alcance otros nodos.
- Paquetes de GHCR públicos (o `docker login` con token `read:packages`): `docker pull` sin credenciales funciona.
- `$NEOCAT_HOME/secrets` en `700`; `$NEOCAT_CONFIG_DIR/*.env` en `600`; la contraseña de restic (`restic.password`, `400`) y la clave LUKS en el
  gestor de contraseñas (FR-011).
- Secret scanning y push protection activos en el repo (SC-005).
