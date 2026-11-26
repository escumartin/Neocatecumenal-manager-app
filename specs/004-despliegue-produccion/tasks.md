---

description: "Task list for Despliegue a producción"
---

# Tasks: Despliegue a producción

**Input**: Design documents from `/specs/004-despliegue-produccion/`

**Prerequisites**: plan.md, spec.md, research.md (R1-R14), data-model.md, contracts/service.openapi.yaml, contracts/operations.md, quickstart.md, `.specify/memory/constitution.md`

**Tests**: obligatorios (constitución IV, regla 3 del proyecto): cada regla nace de un test que falla y debe verse fallar antes de implementar. Backend: JUnit sin Spring, MockMvc y Testcontainers. Cliente: Jest. Infraestructura (Dockerfiles, compose, nginx, scripts del host, workflows): scripts de prueba en `deploy/tests/*.sh`, que se ejecutan en el Mac con Rancher Desktop y en el job `publish`; el test va siempre antes del artefacto. Los escenarios siguen quickstart.md § 1-5.

**Organization**: tareas agrupadas por historia de usuario (US1-US6). Cero comentarios en código y configuración (Dockerfiles, compose, nginx, systemd, workflows y scripts incluidos); mensajes de scripts y logs en castellano; identificadores en inglés.

## Format: `[ID] [P?] [Story] [MANUAL?] Description`

- **[P]**: se puede ejecutar en paralelo (ficheros distintos, sin dependencias)
- **[Story]**: US1 a US6
- **[MANUAL]**: solo se puede verificar en el servidor real, en las tiendas o con cuentas de terceros; no se puede marcar `[x]` en local. Las crea y ejecuta el mantenedor; el asistente nunca ve los secretos (spec, Assumptions)
- Las citas `data-model.md Entidad.campo` indican el campo restringido que la tarea implementa o valida

## Path Conventions

- `B/` = `backend/src/main/java/io/github/escumartin/neocatecumenal/`
- `BT/` = `backend/src/test/java/io/github/escumartin/neocatecumenal/`
- `BR/` = `backend/src/main/resources/`
- `A/` = `app/src/`
- `D/` = `deploy/` (nueva: compose, edge, tailscale, bin, sql, systemd, env, tests)
- Contrato: `api/openapi.yaml` (borrador en `specs/004-despliegue-produccion/contracts/service.openapi.yaml`)
- Logs y temporales de prueba en `~/desarrollo/java/tmp/neocatecumenal/004/`; con Rancher Desktop, `export TESTCONTAINERS_RYUK_DISABLED=true`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: validar la hipótesis de la que depende R2 y dejar el contrato primero; sin dependencias Maven nuevas

- [ ] T001 [MANUAL] Exploración previa (quickstart § 0, R2): levantar `tailscale` con un `edge` de prueba que registre `X-Forwarded-For` y `$remote_addr`, abrir la URL de Funnel desde un móvil con datos y comprobar que la última dirección de `X-Forwarded-For` es la IP pública del móvil; si no lo es, parar y subir al usuario la decisión del plan B (plan.md, «Decisiones tomadas»); requiere cuenta de Tailscale con HTTPS y Funnel activados
- [x] T002 Fusionar `specs/004-despliegue-produccion/contracts/service.openapi.yaml` en `api/openapi.yaml` según la «Regla de fusión del contrato» de plan.md (aditiva: `servers`, `security` y `securitySchemes` de `api/openapi.yaml`; etiqueta `service`, path `/api/compatibility`, esquemas `AppVersion` y `Compatibility`; `getHealth` sin cambios; sin llevar `x-merge`; `info.version` 0.3.0 → 0.4.0), conservando el borrador de `contracts/`, y validar con Redocly (`npx --yes @redocly/cli@2.76.0 lint api/openapi.yaml --config api/redocly.yaml`)
- [x] T003 [P] Generar las interfaces del backend (`ServiceApi`) con `cd backend && ./mvnw -B generate-sources` sin editar nada generado
- [x] T004 [P] Regenerar los tipos del cliente en `A/api/schema.d.ts` (`cd app && npm run api:generate` y `npm run api:check`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: configuración de producción sin valores por defecto, perfil `migrate`, imagen del backend y compose base aislado. Bloquea todas las historias.

**CRITICAL**: ninguna historia empieza hasta cerrar esta fase.

### Tests (escribirlos primero y verlos fallar)

- [x] T005 [P] Test sin Spring de `ProductionConfigurationGuard` en `BT/ProductionConfigurationGuardTest.java`: `prod` junto a `local` no arranca (FR-003); `identity.allowed-origins` vacío no arranca (FR-002); el mensaje nombra la propiedad y nunca su valor
- [x] T006 [P] Test `@SpringBootTest` con `prod` en `BT/ProductionProfileIT.java`: sin `DATABASE_URL`, `ALLOWED_ORIGINS`, `PRIVACY_NOTICE_VERSION` o `MINIMUM_APP_VERSION` no arranca y el log nombra la propiedad sin mostrar valores ni secretos (FR-002, US1-3); `prod` con `local` no arranca (FR-003); con la base de `application.yml` sola no hay usuario ni contraseña de base de datos por defecto
- [x] T007 [P] Test Testcontainers del perfil `migrate` en `BT/MigrationOnlyRunnerIT.java`: con migraciones correctas el proceso sale con 0, aplica el esquema y no abre puerto; con una migración rota en `SPRING_FLYWAY_LOCATIONS` sale con ≠ 0 y `flyway_schema_history` queda sin cambios (FR-006, US3-3)
- [x] T008 [P] Test de imagen en `D/tests/test-backend-image.sh`: el build falla sin `--build-arg SERVICE_REVISION` o con valor que no cumple `^[0-9a-f]{7,40}$`; la imagen corre con usuario sin privilegios, trae la etiqueta OCI `org.opencontainers.image.revision` y `ENV SERVICE_REVISION`, no contiene secretos ni ficheros `application-local.yml` con credenciales (FR-001, R4)
- [x] T009 [P] Test de aislamiento en `D/tests/test-compose-isolation.sh` sobre `docker compose config`: ningún servicio publica `ports:` (salvo `edge` en `127.0.0.1:8088` con `compose.smoke.yaml`); `postgres` sin puerto; `db-net` con `internal: true`; todos con `read_only`, `cap_drop: [ALL]`, `no-new-privileges`, límites de memoria, driver de log `local` con `max-size: 10m` y `max-file: "3"` (`bootstrap` con `none`); ningún montaje de `docker.sock`; solo `backend`, `migrate` y `bootstrap` además de `postgres` en `db-net` (FR-017, R14)

### Implementación

- [x] T010 Perfiles de configuración: quitar de `BR/application.yml` los valores por defecto de conexión, añadir `service.minimum-app-version: 1.0.0` y `service.revision: ${SERVICE_REVISION:0000000}`; mover los valores de conexión locales a `BR/application-local.yml`; crear `BR/application-prod.yml` (`DATABASE_URL`, `ALLOWED_ORIGINS`, `PRIVACY_NOTICE_VERSION`, `MINIMUM_APP_VERSION` sin valor por defecto; `spring.config.import: optional:configtree:/run/secrets/`); `B/ProductionConfigurationGuard.java` con `@Profile("prod")`; los tests existentes pasan a usar `local` o Testcontainers (R6)
- [x] T011 Crear `B/MigrationOnlyRunner.java` (`@Profile("migrate")`, cierra el contexto con `SpringApplication.exit`) y `BR/application-migrate.yml` (`spring.main.web-application-type: none`) (R5)
- [x] T012 [P] Crear `backend/Dockerfile` (multietapa: `eclipse-temurin:25.0.5_8-jdk-noble` (digest fijado) compila, jar por capas sobre `eclipse-temurin:25.0.5_8-jre-noble` (digest fijado), usuario sin privilegios, `tmpfs` en `/tmp`, `ARG` obligatorio `SERVICE_REVISION`, etiqueta OCI `revision`, `healthcheck` a `/actuator/health/readiness`; digests a fijar y tags marcados «a verificar» en research R4 comprobados) y `backend/.dockerignore` (R4)
- [x] T013 Crear `D/compose.yaml` con `postgres:18.7`, `backend`, `migrate` y `bootstrap` (perfil de compose `ops`), redes `db-net` (`internal: true`) y `edge-net` con subred fija, volúmenes con nombre propio, secretos de compose desde `$NEOCAT_HOME/secrets/`, `restart: unless-stopped`, límites y endurecimiento según contracts/operations.md § 3, y `D/env/neocatecumenal.env.example` sin valores (R14)
- [x] T014 Ejecutar en el Mac los tests de imagen y aislamiento (`D/tests/test-backend-image.sh`, `D/tests/test-compose-isolation.sh`), `./mvnw -B verify` con los tests nuevos de esta fase y comprobar que `scripts/local-run.sh` y `docker-compose.yml` siguen sin cambios y funcionando

**Checkpoint**: configuración de producción estricta, imagen del backend y compose base listos; las historias pueden empezar.

---

## Phase 3: User Story 1 - Servicio en producción accesible por HTTPS (Priority: P1) MVP

**Goal**: el servicio responde por una dirección pública cifrada de Funnel, con estado de salud sin detalles y la IP real del cliente resuelta sin permitir suplantación.

**Independent Test**: desde un móvil con datos se abre la dirección pública, se ve el candado y se inicia sesión con el administrador inicial (necesita US2 para tener cuenta; en local, `curl` por `edge` según quickstart § 2).

**Cubre**: FR-004, FR-005, FR-017, SC-001; US1-1 a US1-4.

### Tests (escribirlos primero y verlos fallar)

- [x] T015 [P] [US1] Test `@SpringBootTest` con `prod` en `BT/identity/infrastructure/RemoteIpIT.java`: desde la IP fija de `edge` la cabecera `X-Forwarded-For` y `X-Forwarded-Proto` se respetan; desde otra IP se ignoran y se conserva `remoteAddr` (FR-004, R2)
- [x] T016 [P] [US1] Ampliar `BT/identity/infrastructure/HealthControllerTest.java` y añadir un IT de actuator con `prod`: `/api/health` solo devuelve `{"status":"UP"}`; `/actuator/health/readiness` sin detalles ni componentes (FR-005, US1-2)
- [x] T017 [P] [US1] Test de `edge` en `D/tests/test-edge.sh`: `/actuator/**` → 404; `Forwarded`, `X-Forwarded-Host` y `X-Forwarded-Port` entrantes se descartan y `X-Forwarded-For` llega como valor único; cabeceras `Strict-Transport-Security` (sin `includeSubDomains`), `X-Content-Type-Options`, `Referrer-Policy` y `Content-Security-Policy`; el `access_log` no contiene IP, consulta, cuerpo ni `Authorization` (R1, VIII)

### Implementación

- [x] T018 [US1] Ampliar `BR/application-prod.yml` con `server.forward-headers-strategy: native`, `server.tomcat.remoteip.internal-proxies` (IP fija de `edge`), `remote-ip-header: x-forwarded-for` y `protocol-header: x-forwarded-proto`, y exponer solo `health/readiness` sin detalles en actuator (R2, FR-005)
- [x] T019 [P] [US1] Crear `D/edge/nginx.conf` (`/api/` → `backend:8080`, `/actuator` → 404, cabeceras de seguridad, saneado de cabeceras de reenvío, `access_log` de formato propio) y `D/edge/Dockerfile` (`node:24` ejecuta `npx expo export -p web` sin `EXPO_PUBLIC_API_URL` sobre `nginx:1.30.8-alpine`, digests fijados) (R1, R4)
- [x] T020 [P] [US1] Añadir `edge` y `tailscale` a `D/compose.yaml` (IP fija en `edge-net`, `TS_USERSPACE=true`, `TS_SERVE_CONFIG`, secreto de alta, volumen de estado propio, imagen `tailscale/tailscale:v1.104.7` con digest), `D/tailscale/serve.json` (Funnel 443 → `edge`) y `D/compose.smoke.yaml` (sin `tailscale`, `edge` en `127.0.0.1:8088`, `ALLOWED_ORIGINS=http://localhost:8088`, imágenes por etiqueta local) (R1, R14)
- [x] T021 [US1] Crear `D/tests/smoke.sh` con el humo de quickstart § 2 (`up -d --wait`, `/api/health`, `/actuator/health` → 404, ningún puerto publicado salvo `edge`) y ejecutarlo junto con `test-edge.sh` y los IT de la fase en el Mac
- [ ] T022 [US1] [MANUAL] Funnel real, en el Mac y luego en el servidor (quickstart § 6, pasos 1-3): levantar `D/compose.yaml` completo con `ALLOWED_ORIGINS=https://<nodo>.<tailnet>.ts.net`; desde un móvil con datos, candado y acceso HTTP sin cifrar rechazado (US1-1), inicio de sesión con el administrador de US2 cronometrado < 1 min (SC-001), y la IP guardada en `identity_login_ip_throttle` tras un fallo es la del móvil (FR-004)
- [ ] T023 [US1] [MANUAL] Preparación del servidor (R14, FR-010, FR-017, FR-019): disco cifrado con LUKS2 y desbloqueo por TPM2, clave de recuperación en el gestor de contraseñas, sin reenvíos de puertos en el router, ACL de Tailscale con `tag:neocatecumenal` sin acceso a otros nodos, `$NEOCAT_HOME/secrets` en `700` y `$NEOCAT_CONFIG_DIR/*.env` en `600`; revisión según quickstart § 8 cuando llegue el equipo

**Checkpoint**: US1 verificable en local por `edge`; la parte real queda en las dos tareas manuales.

---

## Phase 4: User Story 2 - Primer administrador sin datos de prueba (Priority: P1)

**Goal**: una carga inicial de un solo uso crea la comunidad del administrador y su cuenta con contraseña generada, mostrada una vez y de cambio obligatorio, y aborta sin cambios si ya hay un administrador.

**Independent Test**: se ejecuta el servicio `bootstrap`, se inicia sesión con el administrador y una segunda ejecución no crea nada.

**Cubre**: FR-003, FR-007, SC-004; US1-4, US2-1 a US2-3.

### Tests (escribirlos primero y verlos fallar)

- [x] T024 [P] [US2] Actualizar `BT/identity/application/BootstrapIdentityServiceTest.java` sin Spring: sin administrador crea exactamente una comunidad y el administrador; con administrador lanza `AdministratorAlreadyExistsException` sin llamar a `CommunityProvisioner` ni a `UserRepository.save`; sin comunidad indicada no crea nada; `lockForBootstrap` se invoca antes de comprobar (data-model.md `BootstrapCommand.administratorName`, `BootstrapCommand.administratorCommunity`)
- [x] T025 [P] [US2] Actualizar `BT/identity/infrastructure/BootstrapRunnerIT.java` (Testcontainers): 1 comunidad y 1 administrador con cambio obligatorio y contraseña solo en consola; segunda ejecución aborta con exit ≠ 0 y sin escrituras; sin comunidad aborta sin crear nada; el log no contiene la contraseña; dos ejecuciones simultáneas crean un solo administrador (bloqueo consultivo)
- [x] T026 [P] [US2] Actualizar `BT/identity/infrastructure/IdentityPropertiesTest.java`: `IdentityProperties.Bootstrap` ya no tiene `communities` y conserva `enabled`, `administratorName` y `administratorCommunity` (data-model.md `IdentityProperties.Bootstrap.administratorName`, `IdentityProperties.Bootstrap.administratorCommunity`)

### Implementación

- [x] T027 [US2] Modificar `BootstrapCommand` y `BootstrapIdentity` (devuelve `CreatedAdministrator`) en `B/identity/application/port/in/`, borrar `BootstrapOutcome`, crear `B/identity/application/service/AdministratorAlreadyExistsException.java`, añadir `lockForBootstrap()` a `UserRepository` y a su adaptador (`pg_advisory_xact_lock`), y reescribir `BootstrapIdentityService` para comprobar bajo el bloqueo y aprovisionar solo la comunidad del administrador (data-model.md `BootstrapCommand.administratorName`, `BootstrapCommand.administratorCommunity`; R7)
- [x] T028 [US2] Adaptar `IdentityBootstrapRunner`, `IdentityBootstrapConfig` e `IdentityProperties` (quitar `communities`): ante `AdministratorAlreadyExistsException` registra «ya existe un administrador» y sale con ≠ 0 sin cambios; la contraseña sale solo por `BootstrapConsoleProvider` (data-model.md `IdentityProperties.Bootstrap.administratorName`, `IdentityProperties.Bootstrap.administratorCommunity`)
- [ ] T029 [US2] Ejecutar en el Mac quickstart § 3 con el servicio `bootstrap` de compose: identificador y contraseña en la terminal, segunda ejecución aborta con exit ≠ 0, recuentos 1 comunidad, 1 usuario, 0 salones y 0 eventos, 0 apariciones de la contraseña en `docker compose logs` y en `journalctl` o equivalente local, inicio de sesión en `http://localhost:8088` con cambio obligatorio (Chrome o Firefox)
- [ ] T030 [US2] [MANUAL] Crear en el servidor real el primer administrador con la comunidad real y comprobar el acceso por Funnel y los recuentos de SC-004 (quickstart § 3 contra producción)

**Checkpoint**: US1 + US2 forman el MVP: servicio público con un administrador y sin datos de prueba.

---

## Phase 5: User Story 3 - Despliegue continuo desde la rama principal (Priority: P2)

**Goal**: cada merge a `master` con los checks en verde publica las imágenes y el servidor las recoge solo: migra con la imagen nueva mientras la vieja atiende y vuelve atrás si la nueva no queda sana.

**Independent Test**: se mergea un cambio menor y en minutos `/api/compatibility` muestra su commit.

**Cubre**: FR-001, FR-006, FR-008, FR-021 (lado servidor: `GET /api/compatibility` identifica el commit desplegado, US3-4), SC-002; US3-1 a US3-4, casos límite de migración fallida y servidor apagado.

### Tests (escribirlos primero y verlos fallar)

- [x] T031 [P] [US3] Test de `neocat-update` en `D/tests/test-update.sh` (quickstart § 5, con servidor de pings local): `apply` sin cambios sale con 0; imagen `:probe` con otro commit queda en `current.env` y la anterior en `previous.env` como `:previous`; migración rota deja `backend` atendiendo con la versión anterior, sale con ≠ 0 y hace ping `/fail`, y una ejecución correcta posterior vuelve a verde; imagen que migra pero no queda `healthy` en 180 s vuelve sola a `previous.env`; nunca imprime secretos
- [x] T032 [P] [US3] Test de workflows en `D/tests/test-workflows.sh` (YAML parseado, sin ejecutar): el job `publish` de `.github/workflows/ci.yml` tiene `needs: [backend, contract, app]`, `if` solo para push a `master`, humo de compose antes de publicar, etiquetas `sha-<commit>` y `production` solo tras el humo, `permissions: packages: write` limitado a ese job; todas las actions fijadas por SHA con la versión en el `name` del paso; sin secretos en claro (FR-008, US3-2); se amplía en la fase de US5 con los workflows móviles
- [x] T033 [P] [US3] Test MockMvc de `ServiceInfoController` en `BT/identity/infrastructure/ServiceInfoControllerTest.java`: `GET /api/compatibility` responde `200` sin sesión con `minimumAppVersion` (`AppVersion` `major.minor.patch`) y `serviceRevision` (`^[0-9a-f]{7,40}$`), sin datos personales (data-model.md `Compatibility.minimumAppVersion`, `Compatibility.serviceRevision`); la respuesta valida contra `api/openapi.yaml`
- [x] T034 [P] [US3] Test sin Spring de `ServiceProperties` en `BT/identity/infrastructure/ServicePropertiesTest.java`: `service.minimum-app-version` obligatoria y en formato `major.minor.patch`; `service.revision` valida el patrón y fuera de imagen vale `0000000` (data-model.md `Compatibility.minimumAppVersion`, `Compatibility.serviceRevision`)

### Implementación

- [x] T035 [US3] Crear `D/bin/neocat-update` (Bash, sin comentarios, mensajes en castellano; rutas `NEOCAT_HOME` y `NEOCAT_CONFIG_DIR` configurables): resuelve los digest de `production`; si cambian, `migrate` con la imagen nueva, etiqueta las imágenes en marcha como `neocatecumenal-{backend,web}:previous` y escribe `previous.env`, escribe `current.env`, `up -d backend edge`, espera `healthy` 180 s y, si no, vuelve a `previous.env`; modo `apply <backend-ref> <web-ref>`; ping de éxito o `/fail` con texto fijo a `deploy` (R3, R5)
- [x] T036 [US3] Crear `B/identity/infrastructure/config/ServiceProperties.java` (`service.minimum-app-version`, `service.revision`) y `B/identity/infrastructure/in/rest/ServiceInfoController.java` implementando `ServiceApi` (adaptador REST que solo lee configuración, como `HealthController`) (data-model.md `Compatibility.minimumAppVersion`, `Compatibility.serviceRevision`; R11)
- [x] T037 [P] [US3] Crear `D/systemd/neocat-update.service` y `D/systemd/neocat-update.timer` (cada 2 min) y `D/env/deploy.env.example` sin valores (R3)
- [x] T038 [US3] Modificar `.github/workflows/ci.yml`: fijar por SHA las actions existentes y añadir el job `publish` (buildx con caché de GHA, login en GHCR con `GITHUB_TOKEN`, build de `ghcr.io/escumartin/neocatecumenal-backend` y `-web` con `SERVICE_REVISION` del commit, `D/compose.yaml` + `D/compose.smoke.yaml` con `D/tests/smoke.sh`, y solo entonces publicar `sha-<commit>` y mover `production`) (R3, R4, contracts/operations.md § 4 y § 8)
- [x] T039 [US3] Ampliar `D/tests/smoke.sh` con `/api/compatibility` (`serviceRevision` = commit local, quickstart § 2) y ejecutar en el Mac `D/tests/test-update.sh`, `D/tests/test-workflows.sh`, `./mvnw -B verify` y repetir quickstart § 5 con imágenes locales; `test-update.sh` comprueba además que `/api/compatibility` muestra el commit tras `apply` (US3-4)
- [ ] T040 [US3] [MANUAL] Primer `publish` real: cambiar cada paquete de GHCR a público (Package settings → Change visibility) y comprobar `docker pull` sin credenciales; crear la comprobación `deploy` de Healthchecks.io (periodo 1 h, gracia 1 h) y guardar su URL en `$NEOCAT_CONFIG_DIR/deploy.env`
- [ ] T041 [US3] [MANUAL] CD real en el servidor (quickstart § 7): merge menor a `master`, `publish` en verde y `production` en `sha-<commit>`, `/api/compatibility` muestra el commit en < 15 min (SC-002); una PR con checks en rojo no llega a `master`; con el servidor apagado durante un merge, al encenderlo aplica la última versión en ≤ 2 min; activar protección de rama de `master` si falta

**Checkpoint**: US3 verificable en local con imágenes locales; la publicación real queda manual.

---

## Phase 6: User Story 4 - Copias de seguridad y restauración (Priority: P2)

**Goal**: copia diaria cifrada en una partición aparte, retención de 30 días, aviso el mismo día si falla, restauración en una base aparte con las supresiones posteriores reaplicadas.

**Independent Test**: se restaura la copia del día anterior en una base aparte y contiene los datos esperados sin los borrados posteriores.

**Cubre**: FR-009, FR-011, FR-020, SC-003, SC-005; US4-1 a US4-3.

### Tests del registro de supresiones (escribirlos primero y verlos fallar)

- [x] T042 [P] [US4] Tests Testcontainers de `V7` y `V8` en `BT/identity/infrastructure/IdentityErasureMigrationIT.java` y `BT/community/infrastructure/CommunityErasureMigrationIT.java`: columnas, tipos y nulabilidad según data-model.md `identity_erasure.id`, `identity_erasure.user_id`, `identity_erasure.erased_at`, `community_erasure.id`, `community_erasure.member_id`, `community_erasure.field` (`varchar(16)` null, valores `MOBILE`, `LANDLINE`, `EMAIL`), `community_erasure.erased_at`; índice por `erased_at`; sin FK; migraciones solo aditivas
- [x] T043 [P] [US4] Tests sin Spring en `BT/identity/application/ErasureRecordingTest.java` y `BT/identity/application/PurgeExpiredErasuresServiceTest.java`: `DeleteUserService` e `IdentityApiService.deleteUser` anotan `recordUser(UserId, Instant)` en la misma transacción que el borrado (un borrado sin su fila falla el test); la purga elimina solo filas con `erased_at` anterior a ahora menos 35 días (data-model.md `identity_erasure.user_id`, `identity_erasure.erased_at`)
- [x] T044 [P] [US4] Tests sin Spring en `BT/community/application/ErasureRecordingTest.java` y `BT/community/application/PurgeExpiredErasuresServiceTest.java`: `DeleteMemberService` anota `recordMember` con `field` nulo; `ChangeMemberContactService` y `ChangeOwnContactService` anotan `recordContactField` solo cuando la retirada del consentimiento deja un campo sin valor; la purga a 35 días (data-model.md `community_erasure.member_id`, `community_erasure.field`, `community_erasure.erased_at`)
- [x] T045 [P] [US4] Tests Testcontainers de los adaptadores `Erasure*` y de los trabajos programados en `BT/identity/infrastructure/ErasurePersistenceIT.java` y `BT/community/infrastructure/ErasurePersistenceIT.java`: la fila se escribe y se revierte con la transacción del borrado; `AccessDataPurgeJob` y `ErasurePurgeJob` invocan la purga; el módulo solo escribe su tabla (III)
- [x] T046 [P] [US4] Ampliar el test de logs existente de `identity` y `community`: ningún log contiene `user_id` ni `member_id` del registro de supresiones (data-model.md, Validación)

- [x] T047 [P] [US4] Test Jest en `A/screens/auth/__tests__/privacy-notice.test.tsx`: el texto provisional del aviso de privacidad indica que lo suprimido desaparece de las copias al caducar a los 30 días (FR-009)

### Tests de los scripts del host (escribirlos primero y verlos fallar)

- [x] T048 [P] [US4] Test de `neocat-backup` en `D/tests/test-backup.sh` (quickstart § 4): instantánea del día en `$NEOCAT_BACKUP_DIR/restic`; con instantáneas sintéticas de 10, 29, 31 y 40 días deja las de 10, 29 y la de hoy; con `forget` desactivado y una de 40 días sale con ≠ 0; con `NEOCAT_BACKUP_MAX_USE=1` sale con ≠ 0 y `/fail` «partición de copias casi llena»; con `NEOCAT_BACKUP_REQUIRE_MOUNT=true` y sin montar falla sin escribir en el disco de datos; repositorio inaccesible sale con ≠ 0 y `/fail`; el volcado excluye `identity_session` y `identity_login_*_throttle`; los pings no llevan salida de comandos
- [x] T049 [P] [US4] Test de `neocat-restore` y `D/sql/apply-erasures.sql` en `D/tests/test-restore.sh` (data-model.md § 3): restaura en una base aparte sin tocar la de producción; reaplica por `erased_at` las filas posteriores a la fecha de la copia (usuario y sesiones; hermano con cascada; campo de contacto a `null`); con choque de FK desvincula y reintenta o anonimiza («Suprimido», apellidos y contacto a `null`); copia las filas aplicadas a la base restaurada; fuente producción o, si no existe, la copia más reciente; el informe lista ids y acciones, nunca nombres; `identity_session` vacía
- [x] T050 [P] [US4] Test de `neocat-erasure-sync` en `D/tests/test-erasure-sync.sh`: con filas en `identity_erasure` o `community_erasure` posteriores a la última instantánea lanza `neocat-backup`; sin ellas no; ping a `erasure-sync`

### Implementación

- [x] T051 [US4] Crear `BR/db/migration/V7__identity_erasure.sql` y `BR/db/migration/V8__community_erasure.sql` (tabla e índice por `erased_at`, sin FK) (data-model.md `identity_erasure.id`, `identity_erasure.user_id`, `identity_erasure.erased_at`, `community_erasure.id`, `community_erasure.member_id`, `community_erasure.field`, `community_erasure.erased_at`)
- [x] T052 [US4] `identity`: puerto de salida `ErasureRecords` (`recordUser`, `purgeBefore`), puerto de entrada `PurgeExpiredErasures` y su servicio, adaptador JPA `Erasure*` en `infrastructure/out/persistence`, anotación en `DeleteUserService` e `IdentityApiService`, y llamada desde `AccessDataPurgeJob` (data-model.md `identity_erasure.user_id`, `identity_erasure.erased_at`; R10)
- [x] T053 [US4] `community`: puerto de salida `ErasureRecords` (`recordMember`, `recordContactField`, `purgeBefore`), puerto de entrada `PurgeExpiredErasures` y su servicio, adaptador JPA `Erasure*`, `ErasurePurgeJob` en `infrastructure/config`, y anotación en `DeleteMemberService`, `ChangeMemberContactService` y `ChangeOwnContactService` (data-model.md `community_erasure.member_id`, `community_erasure.field`, `community_erasure.erased_at`; R10)
- [x] T054 [P] [US4] Añadir esa frase al texto provisional de `A/i18n/es/privacy-notice.ts`; el texto legal definitivo lo aporta la parroquia (fuera del ámbito) y, al no haber aún usuarios en producción, `PRIVACY_NOTICE_VERSION` no se sube por este cambio
- [x] T055 [US4] Crear `D/bin/neocat-backup` (comprobación de montaje, `pg_dump -Fc` por tubería a `restic backup --stdin`, `forget --keep-within-daily 30d --prune`, antigüedad < 31 días, uso < `NEOCAT_BACKUP_MAX_USE`, `restic check` los domingos, pings `/start`, éxito y `/fail` con texto fijo a `backup`) (R8, R9)
- [x] T056 [US4] Crear `D/sql/apply-erasures.sql` y `D/bin/neocat-restore <snapshot> <db-destino>` (`restic dump` desde la partición, `pg_restore` en base aparte, supresiones posteriores, informe por id) (R10, data-model.md § 3)
- [x] T057 [P] [US4] Crear `D/bin/neocat-erasure-sync`, `D/systemd/neocat-backup.{service,timer}` (diario 03:30), `D/systemd/neocat-erasure-sync.{service,timer}` (cada hora) y `D/env/backup.env.example` sin valores (R9, R10)
- [x] T058 [US4] Ejecutar en el Mac los tests de esta fase (`./mvnw -B verify`, `D/tests/test-backup.sh`, `test-restore.sh`, `test-erasure-sync.sh`) y quickstart § 4 completo con datos sintéticos, cronometrando la restauración < 30 min siguiendo `docs/operacion.md` (SC-003)
- [ ] T059 [US4] [MANUAL] En el servidor: partición de copias aparte (segundo disco M.2 si cabe) con LUKS2 + TPM2 montada en `$NEOCAT_BACKUP_DIR`, la contraseña de restic y la clave LUKS también en el gestor de contraseñas del mantenedor (FR-011), comprobaciones `backup` (1 día / 2 h) y `erasure-sync` (1 h / 30 min) de Healthchecks.io con correo real al fallar (FR-020), y restauración probada una vez desde la partición con el volumen de datos borrado (quickstart § 4, última frase; FR-009)

**Checkpoint**: US4 funcional en local; la parte física y los avisos reales quedan manuales.

---

## Phase 7: User Story 5 - App móvil instalable en iPhone y Android (Priority: P2)

**Goal**: las apps se distribuyen en las tiendas con EAS, trabajan contra producción por HTTPS, reciben correcciones por EAS Update y piden actualizar si están por debajo de la versión mínima que declara el servicio.

**Independent Test**: se instala la build en un iPhone y en un Android, se inicia sesión y se crea una reserva que se ve desde la web.

**Cubre**: FR-012, FR-013, FR-016, FR-021 (lado cliente; el endpoint está en US3), SC-006; US5-1 a US5-4.

### Tests de backend (escribirlos primero y verlos fallar)


### Tests del cliente (escribirlos primero y verlos fallar)

- [x] T060 [P] [US5] Tests Jest de la comparación pura en `A/lib/compatibility/__tests__/app-version.test.ts`: `1.2.0 < 1.10.0` es menor (comparación numérica por componentes), igual y mayor no bloquean, versión mal formada no bloquea (data-model.md `Compatibility.minimumAppVersion`)
- [x] T061 [P] [US5] Tests Jest del hook en `A/lib/compatibility/__tests__/use-app-compatibility.test.tsx`: por debajo del mínimo bloquea; igual y por encima no; error de red no bloquea; en web no consulta; consulta al arrancar y al volver a primer plano
- [x] T062 [P] [US5] Tests Jest de `A/lib/api/__tests__/service-api.test.ts` (llamada a `GET /api/compatibility` con el cliente tipado, sin sesión) y de `UpdateRequiredScreen` en `A/screens/__tests__/update-required.test.tsx`: texto ≥ 18 px, botón a la tienda ≥ 48 px, `accessibilityRole="alert"` en el mensaje, escalado de fuente, temas claro y oscuro, textos desde `i18n/es/compatibility.ts` (VII)
- [x] T063 [P] [US5] Test de configuración móvil en `A/__tests__/mobile-config.test.ts`: `app.json` con `ios.bundleIdentifier` y `android.package` = `es.parroquia.neocatecumenal`, `runtimeVersion.policy: "appVersion"`, `updates.url` presente y `expo-updates` en `package.json`; `eas.json` con perfiles `development`, `preview` y `production`, canal `production` con `autoIncrement` y `EXPO_PUBLIC_API_URL` HTTPS, sin secretos (FR-012, FR-013, R12)

### Implementación

- [x] T064 [US5] Cliente: `A/lib/api/service-api.ts` y `A/lib/api/index.ts`, `A/lib/compatibility/app-version.ts`, hook `A/lib/compatibility/use-app-compatibility.ts` (versión propia desde `Constants.expoConfig.version`), `A/screens/update-required.tsx` con componentes `ui/` y tokens de `design-system`, `A/i18n/es/compatibility.ts` y la integración bloqueante en `A/app/_layout.tsx` (R11, VI, VII)
- [x] T065 [US5] Configurar EAS: `expo-updates` con `npx expo install expo-updates` en `app/package.json`, `app/eas.json` (perfiles y canales) y `app/app.json` (`runtimeVersion`, `updates.url` pendiente de `eas init`, `ios.bundleIdentifier`, `android.package`) (R12, R13)
- [x] T066 [P] [US5] Crear `.github/workflows/mobile-build.yml` (EAS Build y Submit) y `.github/workflows/mobile-update.yml` (EAS Update a `production`), ambos `workflow_dispatch`, con `EXPO_TOKEN`, actions fijadas por SHA, y ampliar `D/tests/test-workflows.sh` para comprobarlos antes (R12)
- [x] T067 [US5] Ejecutar en el Mac `cd app && npm run lint && npm run typecheck && npm test && npx expo export -p web`, `./mvnw -B verify`, `D/tests/test-workflows.sh`
- [ ] T068 [US5] [MANUAL] Cuentas y secretos de terceros: `eas init` y `updates.url`, `EXPO_TOKEN` en los secretos de GitHub, Apple Developer y Google Play Console; rellenar `extra.storeUrls` (ios/android) en `app/app.json` con las URL de las fichas; ficha con la política de privacidad pública (FR-016, FR-018) y la prueba cerrada de Play Store (12 testers, 14 días: plazo del mantenedor, no bloquea la feature)
- [ ] T069 [US5] [MANUAL] Builds y dispositivos (quickstart § 6, pasos 4-5): build `preview` con `EXPO_PUBLIC_API_URL` a la URL de Funnel, iniciar sesión y crear una reserva visible en la web en un iPhone y un Android (US5-1); con `MINIMUM_APP_VERSION` por encima de la versión de la build aparece la pantalla de actualización (US5-4); `eas update --channel preview` con un cambio de texto llega al móvil en < 1 h (US5-2, SC-006); build nueva con número de versión mayor para un cambio nativo (US5-3)
- [ ] T070 [US5] [MANUAL] Cuenta de revisor para las tiendas (FR-016): el administrador la crea desde la app en una comunidad propia con datos ficticios y la desactiva al terminar la revisión; si una tienda rechaza la app por uso restringido, publicar como no listada

**Checkpoint**: US5 verificable en local hasta el cliente y la configuración; builds, tiendas y dispositivos quedan manuales.

---

## Phase 8: User Story 6 - Web pública (Priority: P3)

**Goal**: la web se sirve como contenido estático por la misma dirección pública, con rutas internas recargables, política de privacidad pública y orígenes restringidos.

**Independent Test**: desde un ordenador se abre la web, se inicia sesión y se navega por todas las pestañas.

**Cubre**: FR-014, FR-018; US6-1, US6-2.

### Tests (escribirlos primero y verlos fallar)

- [x] T071 [P] [US6] Ampliar `D/tests/test-edge.sh`: una ruta interna como `/communities/x` devuelve `200` con la web y no un error (US6-1); `/privacy` responde `200` sin sesión; caché larga solo en `/_expo/static/`
- [x] T072 [P] [US6] Test `@SpringBootTest` con `prod` en `BT/identity/infrastructure/AllowedOriginsProdIT.java`: una petición que cambia estado con el origen configurado pasa `OriginCheck` y con otro origen se rechaza; no hay filtro CORS en `prod` (FR-014, US6-2, R1)
- [x] T073 [P] [US6] Test Jest de la ruta pública en `A/screens/__tests__/privacy.test.tsx`: `/privacy` se renderiza sin sesión, con texto desde `A/i18n/es/privacy.ts` que incluye la frase de retención de 30 días de las copias, tipografía legible y temas claro y oscuro (VII)

### Implementación

- [x] T074 [US6] `app/app.json` con `web.output: "single"`; `try_files $uri /index.html` y caché de `/_expo/static/` en `D/edge/nginx.conf` (R13)
- [x] T075 [P] [US6] Crear `A/app/privacy.tsx` (pública, fuera del grupo autenticado) y `A/i18n/es/privacy.ts` con el texto provisional que sustituirá el texto legal de la parroquia (fuera del ámbito; R13)
- [ ] T076 [US6] Ejecutar en el Mac `D/tests/test-edge.sh`, el IT de orígenes y los tests Jest, y recorrer la web en `http://localhost:8088` con Chrome o Firefox (quickstart § 2)
- [ ] T077 [US6] [MANUAL] Web real por Funnel (quickstart § 6): recargar una ruta interna, iniciar sesión desde un ordenador y navegar por todas las pestañas; Safari solo por la URL https de Funnel; comprobar que la URL `/privacy` sirve de URL de política en las fichas de las tiendas

**Checkpoint**: las seis historias funcionales en local.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: documentación de operación, reglas del proyecto y revisión final

- [x] T078 [P] Crear `docs/operacion.md` (FR-015): puesta en marcha desde cero, creación del administrador, despliegue y vuelta atrás, restauración con reaplicación de supresiones, rotación de secretos (incluido el procedimiento ante un secreto filtrado), publicación de builds móviles, cifrado LUKS con TPM2, aislamiento y revisión de quickstart § 8; sin secretos ni datos reales
- [x] T079 [P] Modificar `AGENTS.md`: mapa con `deploy/` y `docs/`; reglas nuevas de migraciones compatibles con la versión anterior (R5) y contrato aditivo o subida de `MINIMUM_APP_VERSION` (R11); gates locales ampliados con `D/tests/*.sh`
- [x] T080 [P] Actualizar la documentación de 002 y `.agents/context/dominio.md` donde describan la carga inicial con lista de comunidades (R7)
- [x] T081 Revisión de privacidad y secretos en local: sin secretos en el repo, en `docker compose logs` ni en `docker inspect`; ningún log con ids del registro (SC-005, VIII)
- [x] T082 Gates completos en local: Redocly, `cd backend && ./mvnw -B verify`, `cd app && npm run lint && npm run typecheck && npm test && npx expo export -p web`, todos los `D/tests/*.sh`, y quickstart § 1-5 de punta a punta
- [ ] T083 [MANUAL] Activar secret scanning y push protection en el repositorio (SC-005) y comprobar SC-007: sin servicios de pago (Tailscale y Healthchecks.io en plan gratuito, copias en el propio servidor)
- [ ] T084 [MANUAL] Revisión del servidor real según quickstart § 8 (`lsblk -f`, `findmnt`, `ss -tlnp`, `docker network inspect`, ACL de Tailscale, permisos de secretos) y registro de resultados

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias. La exploración de Funnel (T001) es manual y bloquea solo la decisión del freno por IP (R2, plan B); el resto de la fase y de las fases siguientes puede avanzar en local mientras tanto.
- **Foundational (Phase 2)**: depende del contrato y la generación de Setup; bloquea todas las historias.
- **US1 y US2 (P1)**: tras Foundational, en paralelo (ficheros disjuntos: `edge`/`tailscale` frente a `identity`). La verificación real de US1 necesita la cuenta de US2.
- **US3, US4 y US5 (P2)**: tras Foundational; US3 necesita `edge` de US1 para el humo de `publish` y trae el endpoint `GET /api/compatibility` que US5 consume; US4 necesita el compose de Foundational y las migraciones `V7`/`V8`; US5 necesita el contrato de Setup. 
- **US6 (P3)**: tras US1 (`edge`) y el cliente de US5 si se quiere el humo completo; toca `D/edge/nginx.conf` y `app/app.json`, que comparten US1 y US5: en cola.
- **Polish**: tras las historias que se quieran entregar; `docs/operacion.md` se escribe antes del cronometrado de restauración de US4 (SC-003) si se quiere medir con el documento.

### Within Each Story

- Los tests (JUnit, Testcontainers, Jest, `D/tests/*.sh`) se escriben y se ven fallar antes de la implementación.
- Dominio y aplicación antes que adaptadores; puertos antes que servicios; contrato antes que controladores y cliente.
- Las tareas `[MANUAL]` van al final de su historia y no bloquean las siguientes en local.

### Módulos compartidos (no en paralelo)

- `backend` comparte `target/` entre tareas de Maven: dos tareas de backend a la vez esperan en cola.
- Smoke, compose, puertos y contenedores de `D/tests/*.sh`: de uno en uno.

---

## Parallel Example: Foundational

```bash
Task: "Test sin Spring de ProductionConfigurationGuard en BT/ProductionConfigurationGuardTest.java"
Task: "Test Testcontainers del perfil migrate en BT/MigrationOnlyRunnerIT.java"
Task: "Test de imagen en D/tests/test-backend-image.sh"
Task: "Test de aislamiento en D/tests/test-compose-isolation.sh"
```

## Parallel Example: User Story 4

```bash
Task: "Tests de V7 y V8 en BT/identity/infrastructure/IdentityErasureMigrationIT.java y BT/community/infrastructure/CommunityErasureMigrationIT.java"
Task: "Test de neocat-backup en D/tests/test-backup.sh"
Task: "Test de neocat-restore en D/tests/test-restore.sh"
Task: "Test de neocat-erasure-sync en D/tests/test-erasure-sync.sh"
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Setup y Foundational.
2. US1 y US2: servicio público con un administrador y sin datos de prueba.
3. **STOP y VALIDAR** en local (quickstart § 2 y § 3) y, cuando llegue el servidor, con las tareas manuales de ambas.
4. La primera puesta en marcha real puede hacerse a mano sin US3 (spec, prioridad de US3).

### Incremental Delivery

1. US3 (CD) y US4 (copias): se activan antes de abrir la app a hermanos reales, porque protegen los datos.
2. US5 (apps y versión mínima) y después US6 (web pública).
3. El camino a producción real depende de tareas manuales: llegada del servidor, cuentas de Tailscale, GHCR, Healthchecks, Expo, Apple y Google.

### Tareas manuales (no cerrables en local)

Exploración de Funnel, Funnel real, preparación del servidor, administrador en producción, GHCR y Healthchecks `deploy`, CD real, partición de copias y avisos reales, cuentas de Expo y tiendas, builds y dispositivos, cuenta de revisor, web real, secret scanning y SC-007, y revisión final del servidor.

---

## Notes

- [P] = ficheros distintos, sin dependencias
- [Story] mapea la tarea con su historia para trazabilidad
- Verificar que los tests fallan antes de implementar
- Sin comentarios en código ni configuración; sin secretos en el repo ni en logs
- El asistente nunca ve los secretos de terceros
