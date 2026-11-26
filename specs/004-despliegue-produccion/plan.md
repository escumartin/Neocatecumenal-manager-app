# Implementation Plan: Despliegue a producción

**Branch**: `feat/004-despliegue-produccion` | **Date**: 2026-11-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-despliegue-produccion/spec.md`

## Summary

El servicio, la base de datos y la web se publican desde un mini PC de casa con Docker, compartido con Immich y Home
Assistant, por un subdominio `*.ts.net` de Tailscale Funnel, sin abrir puertos. Cada merge a `master` con los checks en
verde publica imágenes en GHCR y el servidor las recoge solo. Copias diarias cifradas en una partición aparte del servidor, con aviso de fallo y de partición llena,
primer administrador de un solo uso, apps en App Store y Play Store con EAS y versión mínima de app declarada por el
servicio. Todo se valida antes en el Mac con Rancher Desktop.

Enfoque técnico:

- Mismo origen: `tailscale` (Funnel 443, modo `userspace`) → `edge` (nginx con la web estática; `/api/` → backend) →
  `backend` → `postgres`, en redes propias sin puertos publicados. Sin CORS en producción; la cookie `__Host-session`
  sigue igual (R1).
- IP real del cliente para el freno de inicio de sesión: cadena de confianza `tailscale` → `edge` → Tomcat
  `RemoteIpValve`, cada salto solo acepta la cabecera de su vecino (R2). Se valida primero (T001).
- CD por pull: job `publish` en `ci.yml` con `needs` de todos los checks y humo de compose; el host sondea la etiqueta
  `production`, migra con la imagen nueva mientras la vieja atiende y vuelve atrás si la nueva no queda sana (R3, R5).
- Perfil `prod` sin valores por defecto y con guarda; secretos en ficheros de compose (R6). Perfil `migrate` (R5).
- Carga inicial: aborta sin cambios si hay administrador y solo crea la comunidad del administrador (R7).
- Copias con `pg_dump` + `restic` en una partición aparte del servidor (`$NEOCAT_BACKUP_DIR`), 30 días por calendario,
  sin copia fuera de casa; avisos con Healthchecks.io, y registro de supresiones por módulo
  para volver a aplicarlas al restaurar sin sacar datos de la base (R8, R9, R10).
- Contrato primero: `GET /api/compatibility` (versión mínima de app y commit desplegado); la app bloquea con una pantalla
  de actualización si está por debajo (R11).
- EAS Build/Submit/Update con canal `production`, disparados a mano desde GitHub Actions; web en modo `single` con
  `/privacy` pública (R12, R13).
- Runbook en `docs/operacion.md`: cifrado LUKS con TPM2, aislamiento, secretos, restauración y rotación (R14).

## Technical Context

**Language/Version**: Java 25 (backend); TypeScript strict (cliente); Bash (órdenes del host); YAML de compose y GitHub
Actions

**Primary Dependencies**:

- Backend: Spring Boot 4.2, Spring Modulith 2.2, Flyway, Spring Security. Sin dependencias Maven nuevas.
- Contenedores (digest a fijar en la implementación): `postgres:18.7`, `eclipse-temurin:25.0.5_8-jre-noble` (la `jdk`
  equivalente, a verificar), `nginx:1.30.8-alpine`, `node:24` (a verificar), `tailscale/tailscale:v1.104.7`,
  `restic/restic:0.20.0`.
- Actions (SHA a fijar; versión en el `name` del paso, como en `pr-review.yml`): `actions/checkout` v7.0.1,
  `docker/setup-buildx-action` v4.4.1, `docker/login-action` v4.8.0, `docker/metadata-action` v6.2.0,
  `docker/build-push-action` v7.4.0, `expo/expo-github-action` 9.0.2.
- Cliente: `expo-updates` (`npx expo install`, 58.0.22 comprobada), `eas-cli` 27.11.0. Sin más dependencias.
- Servicios externos (cuentas del mantenedor): Tailscale, GHCR, Healthchecks.io, Expo, Apple
  Developer, Google Play Console.

**Storage**: PostgreSQL 18; migraciones nuevas `V7__identity_erasure.sql` y `V8__community_erasure.sql`, solo aditivas.
Volúmenes con nombre propio en un disco LUKS2.

**Testing**:

- JUnit sin Spring: carga inicial (aborta con administrador sin tocar puertos de escritura; solo una comunidad),
  registro de supresiones en borrar usuario, borrar hermano y retirar consentimiento, purga a 35 días.
- Testcontainers: `V7`/`V8`; perfil `migrate` sale con 0 y con una migración rota sale con ≠ 0 sin cambios;
  `prod` sin `ALLOWED_ORIGINS` no arranca y el log no muestra valores; `prod` con `local` no arranca; `RemoteIpValve`
  con IP confiable y no confiable; `/api/compatibility` público; `BootstrapRunnerIT` ampliado (segunda ejecución).
- Cliente (Jest): comparación de versiones, hook de compatibilidad (menor, igual, mayor, error de red, web),
  `resolveApiUrl` sin cambios.
- Humo de compose en CI y en el Mac (quickstart § 2); scripts del host probados en el Mac (quickstart § 3-5).

**Target Platform**: Linux x86-64 con Docker Engine (mini PC GEEKOM, pendiente de compra); Mac con Rancher Desktop para
validar; iOS, Android y navegadores

**Project Type**: web-service + cliente móvil/web + infraestructura de despliegue

**Performance Goals**: SC-001 < 1 min hasta la agenda; SC-002 < 15 min del merge a producción; SC-003 restauración
< 30 min; SC-006 OTA < 1 h

**Constraints**: ≤ 5 €/mes (SC-007); datos en la UE (FR-010); sin puertos de entrada; sin socket de Docker en
contenedores; sin secretos en el repo ni en logs; imágenes y actions fijadas por digest o SHA

**Scale/Scope**: una parroquia (decenas a pocos cientos de usuarios); una instancia; corte de segundos por despliegue
aceptado

## Dependencies

- 002 (`identity`): carga inicial, cookie `__Host-session`, `OriginCheck`, freno por IP, `AccessDataPurgeJob`.
- 003 (`community`): borrado de hermanos y retirada de consentimiento (R7 de 003), `CommunityProvisioner`.
- Cuentas de terceros y secretos: los crea el mantenedor; el asistente nunca los ve.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo se cumple | Antes de R | Tras diseño |
|---|---|---|---|
| I. Dominio puro | sin cambios en `domain` salvo, si hace falta, el enum de campo de contacto ya existente; `AdministratorAlreadyExistsException` vive en `application` | PASS | PASS |
| II. Puertos y adaptadores | `ErasureRecords` (out) en `identity` y `community`; `PurgeExpiredErasures` (in); `lockForBootstrap` en `UserRepository`; adaptadores JPA y programados en `infrastructure`; `ServiceInfoController` es adaptador REST que solo lee configuración, como `HealthController`; `MigrationOnlyRunner` y `ProductionConfigurationGuard` en el paquete raíz, como `ClockConfig` | PASS | PASS |
| III. Un módulo por contexto | sin módulo nuevo; `identity_erasure` solo la escribe `identity` y `community_erasure` solo `community`; sin tabla compartida ni FK entre módulos | PASS | PASS |
| IV. Tests primero | cada regla (aborto de la carga inicial, anotación de supresiones, purga, versión mínima) nace de un test que falla; adaptadores y perfiles con Testcontainers; [quickstart.md](./quickstart.md) § 1 mapea escenarios | PASS | PASS |
| V. Contrato primero | borrador [contracts/service.openapi.yaml](./contracts/service.openapi.yaml) validado con Redocly 2.76.0; primera tarea de código: fusión aditiva y generación; 0.3.0 → 0.4.0 | PASS | PASS |
| VI. Separación en cliente y tokens | `lib/compatibility/` (comparación pura) y hook `use-app-compatibility`; `lib/api/service-api.ts`; `UpdateRequiredScreen` y `/privacy` con componentes `ui/` y tokens de `design-system` | PASS | PASS |
| VII. Accesible para todas las edades | pantalla de actualización: texto ≥ 18 px, botón ≥ 48 px, `accessibilityRole="alert"` en el mensaje, escalado de fuente, temas claro y oscuro, sin animación propia | PASS | PASS |
| VIII. Privacidad por diseño | registro de supresiones solo con ids y fecha, 35 días, justificado por FR-009; copias cifradas con `restic` en una partición aparte del servidor (en España), sin sesiones ni IPs; disco cifrado; contraseña inicial solo por consola sin log de Docker; nginx sin cuerpos ni `Authorization` en logs; pings sin datos; el log de acceso de `edge` no guarda IP, consulta, cuerpo ni `Authorization` y sustituye los UUID del path por `:id`; los logs de borrado y anonimización no llevan ids, y todos los contenedores rotan con el driver `local` (`max-size: 10m`, `max-file: 3`) | PASS | PASS |
| IX. Higiene de código | Dockerfiles, compose, nginx, systemd, workflows y scripts sin comentarios; identificadores en inglés; textos en `app/src/i18n/es`; mensajes de scripts y logs en castellano | PASS | PASS |
| Secrets never enter the repository | solo `deploy/env/*.example` sin valores; secretos en `/srv` y `/etc` del servidor y en GitHub; secret scanning y push protection activos | PASS | PASS |

**Resultado**: sin violaciones. No hace falta *Complexity Tracking*.

## Project Structure

### Documentation (this feature)

```text
specs/004-despliegue-produccion/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── service.openapi.yaml
│   └── operations.md
├── checklists/
└── tasks.md
```

### Source Code (repository root)

```text
api/openapi.yaml                                         MODIFY
backend/
├── Dockerfile                                           CREATE
├── .dockerignore                                        CREATE
└── src/main/
    ├── java/io/github/escumartin/neocatecumenal/
    │   ├── MigrationOnlyRunner.java                     CREATE
    │   ├── ProductionConfigurationGuard.java            CREATE
    │   ├── identity/
    │   │   ├── application/port/in/{BootstrapCommand,BootstrapIdentity,PurgeExpiredErasures}.java
    │   │   ├── application/port/in/BootstrapOutcome.java                    DELETE
    │   │   ├── application/port/out/{ErasureRecords,UserRepository}.java
    │   │   ├── application/service/{BootstrapIdentity,DeleteUser,IdentityApi,PurgeExpiredErasures}Service.java
    │   │   ├── application/service/AdministratorAlreadyExistsException.java CREATE
    │   │   ├── infrastructure/config/{IdentityProperties,ServiceProperties,AccessDataPurgeJob}.java
    │   │   ├── infrastructure/in/bootstrap/IdentityBootstrapRunner.java
    │   │   ├── infrastructure/in/rest/ServiceInfoController.java           CREATE
    │   │   └── infrastructure/out/persistence/Erasure*.java                 CREATE
    │   └── community/
    │       ├── application/port/in/PurgeExpiredErasures.java               CREATE
    │       ├── application/port/out/ErasureRecords.java                    CREATE
    │       ├── application/service/{DeleteMember,ChangeMemberContact,ChangeOwnContact,PurgeExpiredErasures}Service.java
    │       └── infrastructure/{config/ErasurePurgeJob,out/persistence/Erasure*}.java CREATE
    └── resources/
        ├── application.yml                              MODIFY
        ├── application-local.yml                        MODIFY
        ├── application-prod.yml                         CREATE
        ├── application-migrate.yml                      CREATE
        └── db/migration/V7__identity_erasure.sql, V8__community_erasure.sql   CREATE
app/
├── app.json                                             MODIFY
├── eas.json                                             CREATE
├── package.json                                         MODIFY
└── src/
    ├── app/_layout.tsx                                  MODIFY
    ├── app/privacy.tsx                                  CREATE
    ├── lib/api/{service-api.ts,index.ts}                CREATE / MODIFY
    ├── lib/compatibility/{app-version.ts,use-app-compatibility.ts}   CREATE
    ├── screens/update-required.tsx                      CREATE
    └── i18n/es/{compatibility,privacy}.ts               CREATE
deploy/
├── compose.yaml, compose.smoke.yaml                     CREATE
├── edge/{Dockerfile,nginx.conf}                         CREATE
├── tailscale/serve.json                                 CREATE
├── bin/{neocat-update,neocat-backup,neocat-erasure-sync,neocat-restore}   CREATE
├── sql/apply-erasures.sql                               CREATE
├── systemd/neocat-{update,backup,erasure-sync}.{service,timer}            CREATE
└── env/{neocatecumenal,backup,deploy}.env.example       CREATE
docs/operacion.md                                        CREATE
.github/workflows/ci.yml                                 MODIFY
.github/workflows/{mobile-build,mobile-update}.yml       CREATE
AGENTS.md                                                MODIFY
```

**Structure Decision**: web-service en `backend/` sin módulo Modulith nuevo; cliente Expo en `app/`; infraestructura de
despliegue en `deploy/` (nueva) y runbook en `docs/` (nueva). Ambas se añaden al mapa de `AGENTS.md`.

## Tabla de cambios

| Ruta | Acción | Motivo |
|---|---|---|
| `api/openapi.yaml` | MODIFY | fusión aditiva de `contracts/service.openapi.yaml`; 0.3.0 → 0.4.0 (R11) |
| `application.yml` | MODIFY | quita los valores por defecto de conexión; añade `service.minimum-app-version: 1.0.0` y `service.revision: ${SERVICE_REVISION:0000000}` (cumple el patrón del contrato; en imagen, el commit real) (R6) |
| `application-local.yml` | MODIFY | recibe los valores por defecto de conexión locales (R6) |
| `application-prod.yml` | CREATE | variables sin defecto, `configtree`, `forward-headers-strategy: native` y `server.tomcat.remoteip.*` (R2, R6) |
| `application-migrate.yml` | CREATE | `spring.main.web-application-type: none` (R5) |
| `MigrationOnlyRunner`, `ProductionConfigurationGuard` | CREATE | R5, R6 |
| `identity/.../BootstrapIdentityService`, `BootstrapCommand`, `BootstrapIdentity`, `IdentityBootstrapRunner`, `IdentityProperties` | MODIFY | aborto con administrador, sin lista de comunidades, bloqueo consultivo (R7) |
| `identity/.../BootstrapOutcome` | DELETE | el caso de uso devuelve `CreatedAdministrator` (R7) |
| `identity/.../UserRepository` y su adaptador | MODIFY | `lockForBootstrap` (R7) |
| `identity/.../{DeleteUserService,IdentityApiService}` | MODIFY | anotan la supresión (R10) |
| `identity/.../ErasureRecords`, `PurgeExpiredErasures*`, `Erasure*` de persistencia; `AccessDataPurgeJob` | CREATE / MODIFY | R10 |
| `identity/.../ServiceInfoController`, `ServiceProperties` | CREATE | R11 |
| `community/.../{DeleteMember,ChangeMemberContact,ChangeOwnContact}Service` | MODIFY | anotan hermano o campo suprimido (R10) |
| `community/.../ErasureRecords`, `PurgeExpiredErasures*`, `ErasurePurgeJob`, `Erasure*` de persistencia | CREATE | R10 |
| `V7__identity_erasure.sql`, `V8__community_erasure.sql` | CREATE | R10 |
| tests de `identity` y `community` afectados (`BootstrapRunnerIT`, `IdentityPropertiesTest`, logs sin datos) | MODIFY | R7, R10 |
| `backend/Dockerfile`, `.dockerignore` | CREATE | R4 |
| `deploy/**` | CREATE | R1-R5, R8-R10, R14 |
| `.github/workflows/ci.yml` | MODIFY | job `publish` (R3, R4); actions existentes fijadas por SHA |
| `.github/workflows/mobile-{build,update}.yml` | CREATE | R12 |
| `app/app.json` | MODIFY | `web.output: "single"`, `runtimeVersion`, `updates.url`, `ios.bundleIdentifier`, `android.package` (R12, R13) |
| `app/eas.json`, `app/package.json` | CREATE / MODIFY | perfiles y canales; `expo-updates` (R12) |
| `app/src/lib/compatibility/**`, `lib/api/service-api.ts`, `screens/update-required.tsx`, `app/_layout.tsx` | CREATE / MODIFY | R11 |
| `app/src/app/privacy.tsx`, `i18n/es/{compatibility,privacy}.ts` | CREATE | R13 |
| `docs/operacion.md` | CREATE | FR-015 (R14) |
| `AGENTS.md` | MODIFY | mapa (`deploy/`, `docs/`) y reglas: migraciones compatibles con la versión anterior; contrato aditivo o subida de `MINIMUM_APP_VERSION` |
| `scripts/local-run.sh`, `docker-compose.yml` | sin cambios | el entorno local sigue igual |

## Regla de fusión del contrato

1. `servers`, `security` y `components.securitySchemes` se toman de `api/openapi.yaml`.
2. Se añaden la etiqueta `service`, el path `/api/compatibility` y los esquemas `AppVersion` y `Compatibility`; ningún
   nombre choca. `getHealth` no cambia (sigue sin etiqueta, en `DefaultApi`). `x-merge` se elimina.
3. Resultado aditivo; `info.version` 0.3.0 → 0.4.0. Después: Redocly con `api/redocly.yaml`,
   `./mvnw -B generate-sources` (genera `ServiceApi`) y `npm run api:generate`.

## Decisiones principales

Detalle, alternativas y consecuencias en [research.md](./research.md).

1. **Mismo origen tras un proxy propio**: Funnel 443 → nginx con la web y `/api/` → backend; sin CORS en producción
   (R1). Trade-off: un contenedor más a cambio de cabeceras y rutas bajo control.
2. **IP real por cadena de confianza** `tailscale` → `edge` → `RemoteIpValve` (R2). Trade-off: depende de la cabecera
   de Funnel; se comprueba antes de nada (T001).
3. **CD por pull** con script propio en el host y GHCR público (R3). Trade-off: código propio frente a Watchtower
   (archivado y con socket de Docker) o runner self-hosted (código remoto en casa).
4. **Dockerfile multietapa** y humo de compose antes de mover `production` (R4). Trade-off: compila dos veces en CI.
5. **Migrar antes con la imagen nueva** mientras la vieja atiende; vuelta atrás a las imágenes `:previous` guardadas en `previous.env` (R5). Trade-off: obliga a
   migraciones compatibles con la versión anterior.
6. **`prod` sin valores por defecto** y secretos por `configtree` (R6). Trade-off: local depende del perfil `local`.
7. **Carga inicial de un solo uso** sin lista de comunidades (R7). Trade-off: en local ya no hay alta en lote.
8. **`pg_dump` + `restic` en una partición aparte** con `--keep-within-daily 30d` y aviso si no está montada o pasa
   del 80 % (R8, R9). Trade-off: cifrado y deduplicación propios frente a ficheros sueltos más simples; sin copia fuera
   de casa, perder el equipo entero es perder los datos (riesgo aceptado por el usuario).
9. **Healthchecks.io** como interruptor de hombre muerto (R9). Trade-off: un tercero más, solo con pings.
10. **Registro de supresiones por módulo** con retención de 35 días, atada a que no haya instantánea de 31 días o más, y copia extra horaria si hay supresiones (R10).
    Trade-off: dos tablas y un script SQL de operación frente a disparadores que no distinguen borrados accidentales.
11. **`GET /api/compatibility`** y pantalla de actualización obligatoria (R11). Trade-off: el servidor no impide el uso
    de apps viejas; lo hace la app.
12. **EAS con workflows manuales** y canal `production` (R12). Trade-off: `EXPO_TOKEN` en GitHub.
13. **Web `single`** con `/privacy` pública (R13). Trade-off: la URL de privacidad cae si cae la casa.
14. **LUKS2 con desbloqueo automático por TPM2** (decidido por el usuario) y aislamiento por compose (R14). Trade-off: arranque desatendido frente a robo del equipo entero.

## Decisiones tomadas

- Identificador `es.parroquia.neocatecumenal` (iOS y Android, R12), decidido por el usuario.
- Desbloqueo automático por TPM2 (R14), decidido por el usuario.
- Copias solo en una partición aparte del servidor, sin copia fuera de casa (R8), decidido por el usuario.
- Healthchecks.io (plan gratuito, solo pings sin datos) como canal de aviso (R9).
- La partición de copias va en un segundo disco M.2 si el equipo lo admite; si no, en una partición del mismo disco (R8).
- Si T001 demuestra que Funnel no entrega la IP real: se desactiva el freno por IP en producción y queda el freno por cuenta (R2, plan B).

## Criterios de éxito

| Criterio | Cómo se mide | Dónde |
|---|---|---|
| SC-001 (< 1 min hasta la agenda) | cronometrado desde un móvil con datos por Funnel | [quickstart.md](./quickstart.md) § 6 |
| SC-002 (< 15 min del merge) | marca de tiempo del merge frente a `serviceRevision` de `/api/compatibility` | quickstart § 7 |
| SC-003 (restauración < 30 min) | cronometrado siguiendo `docs/operacion.md` en el Mac | quickstart § 4 |
| SC-004 (0 registros de prueba) | consultas de recuento tras la puesta en marcha: 1 comunidad, 1 usuario, 0 salones | quickstart § 3 |
| SC-005 (0 secretos) | secret scanning de GitHub + búsqueda de la contraseña inicial y de los secretos en `docker logs` | quickstart § 3 y § 8 |
| SC-006 (OTA < 1 h) | publicación en `production` y apertura en un móvil de prueba | quickstart § 6 |
| SC-007 (≤ 5 €/mes) | sin servicios de pago: copias en el propio servidor (0 €), Tailscale y Healthchecks en plan gratuito | runbook |

## Riesgos

- Funnel sin IP real del cliente → freno por IP común a todos (R2); se valida en T001 antes de seguir.
- Una migración no compatible con la versión anterior rompe la vuelta atrás (R5); regla en `AGENTS.md` y revisión.
- Corte de luz o de conexión en casa: servicio caído; aceptado en la spec.
- Robo del equipo entero con TPM2: el disco arranca hasta la pantalla de acceso (R14).
- Robo, incendio o avería total del equipo: se pierden datos y copias (aceptado en la spec). Avería del disco de
  datos: se restaura desde la partición, con pérdida de hasta un día (1 h para supresiones, R10).
- Límites de Funnel (ancho de banda, cambios de política de Tailscale): sin alternativa sin dominio propio.
- Revisión de las tiendas por app de uso restringido: app no listada (spec, casos límite).

## Pendientes fuera del ámbito de este plan

- Texto legal de la política de privacidad y del aviso: lo aporta la parroquia.
- Atestación de procedencia de imágenes y su verificación en el host.
- Copia fuera de casa: descartada por el usuario; si se pierde el equipo entero, se pierden los datos.

## Complexity Tracking

Sin violaciones de la constitución. El principio VIII se cumple sin excepción: el proxy no registra IPs; la única IP
que se guarda es la del freno de inicio de sesión de 002, ya justificada allí y excluida de las copias.
