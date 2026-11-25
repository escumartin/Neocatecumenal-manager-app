# Implementation Plan: Reservas de salones

**Branch**: `feat/001-reservas-salones-impl` (implementación); plan en `001-reservas-salones-plan` | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-reservas-salones/spec.md`

## Summary

El administrador gestiona los salones parroquiales. Los moderadores, para su comunidad, y los administradores, para la
comunidad que eligen, solicitan un salón para una fecha y franja (una vez o semanal con fecha fin ≤ 1 año) o dan de alta
un evento con salón, que crea su solicitud en la misma operación. El administrador aprueba o rechaza cada solicitud o
serie entera. Las solicitudes pendientes y aprobadas bloquean la franja; nunca hay dos activas solapadas en el mismo
salón.

Enfoque técnico:

- Módulo Spring Modulith `spaces` («Espacios y reservas»): agregados `Room` (alta, edición y baja lógica) y
  `BookingRequest` (solicitud o serie) con sus ocurrencias `Booking`.
- Módulo `agenda` mínimo: alta y consulta de un evento con salón; crea la solicitud con `SpacesApi.requestForEvent` en la
  misma transacción.
- Garantía de no solape con una restricción de exclusión GiST de PostgreSQL sobre las ocurrencias activas, más una
  consulta previa para informar de las fechas en conflicto.
- Actor obtenido en los adaptadores REST con `IdentityApi.currentUser()`; sin puertos nuevos de identidad.
- Contrato OpenAPI primero: borrador en `contracts/room-bookings.openapi.yaml`, cambio solo aditivo sobre `api/openapi.yaml`. El borrador ya está fusionado; la fuente de verdad es `api/openapi.yaml`.

## Technical Context

**Language/Version**: Java 25 (backend); TypeScript strict (cliente)

**Primary Dependencies** (sin dependencias nuevas):

- Backend: Spring Boot 4.2, Spring Modulith 2.x, Spring Data JPA, Flyway, Bean Validation, Spring Security (ya presentes).
- Generación backend: `openapi-generator-maven-plugin` ya configurado en `backend/pom.xml` (`spring`, `interfaceOnly`,
  `useSpringBoot4`, `useTags`, paquete `io.github.escumartin.neocatecumenal.generated.api`). Genera desde
  `api/openapi.yaml` entero, así que las interfaces `RoomsApi`, `BookingRequestsApi` y `EventsApi` salen sin tocar el pom.
- Cliente: Expo 58, Expo Router, NativeWind, React Native Reusables; `openapi-typescript`, `openapi-fetch`, el script
  `api:generate` y `app/src/lib/api/client.ts` ya existen.

**Storage**: PostgreSQL 18 con la extensión `btree_gist`; migraciones Flyway nuevas `V3__spaces.sql` y `V4__agenda.sql`
(las aplicadas `V1` y `V2` no se tocan). Claves internas UUID (R11).

**Testing**:

- JUnit 5 y AssertJ sin Spring para dominio y aplicación, con puertos falsos en memoria.
- Testcontainers PostgreSQL para persistencia, incluidas pruebas concurrentes: dos solicitudes, dos resoluciones, y
  baja frente a solicitud.
- MockMvc para los adaptadores REST con `@MockitoBean IdentityApi` que devuelve el `CurrentUser` de cada caso (sin
  tocar internos de `identity`).
- ArchUnit (`ArchitectureTest`) y `ApplicationModules.verify()` (`ModularityTest`), ya existentes.
- Cliente: Jest + `@testing-library/react-native` (ya presentes) para los tests de componente de accesibilidad (FR-012).

**Target Platform**: servidor Linux (JVM) y cliente Expo web, iOS y Android

**Project Type**: web-service + cliente móvil/web

**Performance Goals**: escala de parroquia (decenas de comunidades, < 10 salones, < 50 solicitudes/día); alta de
una serie de 53 ocurrencias < 500 ms p95

**Constraints**:

- Cero solapes activos, también con concurrencia (SC-002).
- Zona horaria única `Europe/Madrid`; la franja empieza después de ahora y no cruza la medianoche.
- Serie solo semanal con fecha fin ≤ 1 año (≤ 53 ocurrencias).
- Motivo y motivo de rechazo ≤ 500 caracteres; sin datos personales en reservas, eventos ni logs.

**Scale/Scope**:

- Backend: 2 módulos nuevos (`spaces`, `agenda` mínimo) y 15 operaciones HTTP.
- Cliente: 6 pantallas: solicitar, mis solicitudes, pendientes del administrador, ocupación por día, gestión de salones
  y alta de evento.

## Dependencies

- **Previa: feature «Identidad y acceso»** (ya implementada en `identity`). Esta feature usa solo su API pública:
  - `IdentityApi.currentUser()` → `CurrentUser(userId, accessId, name, role, communityId)`; el adaptador REST copia solo
    `userId`, `role` y `communityId` al `Actor` propio de `spaces`/`agenda` (el nombre y el `accessId` no entran).
  - `IdentityApi.community(id)` para comprobar que la comunidad elegida por el administrador existe.
  - Evento publicado `UserDeleted(userId)` para anonimizar (FR-022).
- Agenda completa (edición, borrado, listados, otros tipos de evento): feature posterior. Aquí solo el mínimo de R12.

## Reutilización

| Pieza | Acción | Justificación |
|---|---|---|
| `api/openapi.yaml` | UPDATE | se le fusiona el borrador con la regla de fusión de abajo; cambio solo aditivo |
| `backend/pom.xml` (`openapi-generator-maven-plugin`) | REUSE | ya genera interfaces de todo el contrato con `useSpringBoot4` y `useTags`; no hace falta cambiarlo |
| `identity.IdentityApi`, `CurrentUser`, `Role`, `UserDeleted` | REUSE | actor, comunidad y borrado de usuario por la API pública del módulo |
| `identity...ProblemDetailsHandler` | REUSE | sigue siendo el manejador global (validación de entrada, 401, 403, 404 de recursos, 500) |
| `IdentitySecurityConfig` (`anyRequest().authenticated()`) y `PendingStepsAuthorization` | REUSE | ya cubren `/api/rooms`, `/api/booking-requests` y `/api/events` |
| `ArchitectureTest`, `ModularityTest` | REUSE | validan los módulos nuevos sin cambios |
| `app/package.json` (`openapi-typescript`, `openapi-fetch`, `api:generate`, `api:check`) | REUSE | se ejecuta `npm run api:generate` tras fusionar el contrato |
| `app/src/api/schema.d.ts` | UPDATE (generado) | lo regenera `api:generate`; no se edita a mano |
| `app/src/lib/api/client.ts` | REUSE | cliente `openapi-fetch` con token, 401 y pasos pendientes |
| `app/src/lib/api/{rooms,booking-requests,events}-api.ts` | CREATE | llamadas y mapeo de cada recurso, siguiendo `users-api.ts` |
| `app/src/hooks/{rooms,bookings,events}/`, `app/src/screens/{rooms,bookings,events}/`, rutas en `app/src/app/(app)/` | CREATE | pantallas nuevas con tokens de `design-system` |
| `spaces/**`, `agenda/**`, `V3__spaces.sql`, `V4__agenda.sql` | CREATE | módulos y migraciones nuevos |

## Regla de fusión del contrato

El borrador `contracts/room-bookings.openapi.yaml` se fusiona en `api/openapi.yaml` como primera tarea:

1. `servers`, `security` (`bearerAuth` + `cookieAuth`) y `components.securitySchemes` se toman de `api/openapi.yaml`; los
   del borrador son referencias a ese fichero y se descartan.
2. Respuestas compartidas `Unauthorized`, `Forbidden` y `ValidationFailed`, y esquemas `Problem` y `ValidationProblem`:
   se toman de `api/openapi.yaml`. Las referencias externas del borrador (`../../../api/openapi.yaml#/components/...`)
   pasan a locales (`#/components/...`).
3. Enums `Problem.code` y `ValidationProblem.errors[].code`: unión; solo se añaden los valores de
   `x-merge.problemCodes` y `x-merge.validationCodes` del borrador, sin quitar ni renombrar ninguno.
4. Respuestas propias de reservas con nombre propio (`BookingNotFound`, `BookingStateConflict`); `NotFound` y
   `StateConflict` de identidad no se redefinen.
5. Se añaden los `tags`, `paths`, `parameters` y `schemas` nuevos; ningún nombre choca con los existentes. `x-merge` se
   elimina en la fusión.
6. Resultado: cambio solo aditivo (operaciones, esquemas y valores de enum nuevos), compatible hacia atrás; `info.version`
   pasa de `0.1.0` a `0.2.0`. Tras fusionar: `./mvnw -B generate-sources` y `npm run api:generate`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple el plan | Pre | Post |
|---|---|---|---|
| I. Dominio puro | `spaces.domain` y `agenda.domain` sin Spring/JPA/Jackson; `Clock` e instante actual se pasan como argumento; `Actor` es un value object propio de cada módulo | OK | OK |
| II. Puertos y adaptadores | puertos en `application.port.{in,out}`; REST, JPA, programador y escucha de `UserDeleted` en `infrastructure`. El actor lo resuelve el adaptador REST con `IdentityApi.currentUser()` y entra en el comando; `application` no conoce identidad ni Spring Security. La traducción de `23P01` vive en el adaptador de persistencia | OK | OK |
| III. Un módulo por contexto | `spaces` y `agenda` separados; `agenda → spaces` solo vía `SpacesApi` y tipos de su raíz; `spaces → identity` y `agenda → identity` solo vía `IdentityApi`, `CurrentUser`, `Role` y `UserDeleted`; `spaces` no depende de `agenda`; sin tablas compartidas ni FK entre módulos | OK | OK |
| IV. Tests primero | tests de dominio y aplicación sin Spring antes del código; cada FR con camino feliz y de error ([quickstart.md](./quickstart.md) § 2); persistencia y concurrencia con Testcontainers | OK | OK |
| V. Contrato primero | borrador en `contracts/`; primera tarea: fusión según la regla anterior y generación; nada generado se edita a mano; cambio solo aditivo | OK | OK |
| VI. Separación en cliente y tokens | acceso a datos y mapeo en `app/src/lib/api/` y hooks; pantallas solo con tokens de `design-system` | OK | OK |
| VII. Accesible para todas las edades | FR-012: texto ≥ 18 px, objetivos ≥ 48 px, AA, etiquetas, escalado de fuente y movimiento reducido; test de componente con roles y etiquetas más revisión manual | OK | OK |
| VIII. Privacidad por diseño | solo los campos de FR-011; motivo ≤ 500 con aviso; ocupación sin motivos ni comunidad ajena; anonimización al borrar usuario y purga a un año (FR-022); logs solo con ids y códigos (R8, R14) | OK | OK |
| IX. Higiene de código | sin comentarios; identificadores en inglés; códigos de error del contrato → mensajes localizados en `app/src/i18n` | OK | OK |

Restricciones adicionales: stack conforme; sin dependencias nuevas (`btree_gist` es una extensión estándar de
PostgreSQL); sin secretos en el repo.

Coherencia con `dominio.md`:

- La baja de salón es definitiva y pide confirmación («borrar es definitivo»).
- El administrador elige comunidad (`communityId` obligatorio para él al solicitar, consultar ocupación y dar de alta
  eventos).
- El moderador solo opera sobre su comunidad.
- La regla de solape se mantiene tal cual.

**Resultado**: sin violaciones. No hace falta *Complexity Tracking*.

## Project Structure

### Documentation (this feature)

```text
specs/001-reservas-salones/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── room-bookings.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md                # lo genera /speckit.tasks
```

### Source Code (repository root)

```text
api/
└── openapi.yaml                                  # UPDATE: fusión aditiva del borrador

backend/
├── pom.xml                                       # REUSE, sin cambios
└── src/
    ├── main/java/io/github/escumartin/neocatecumenal/
    │   ├── ClockConfig.java                      # MOVE desde identity: único bean Clock (UTC), R9
    │   ├── spaces/                               # CREATE
    │   │   ├── package-info.java                 # @ApplicationModule
    │   │   ├── SpacesApi.java                    # requestForEvent, bookingRequestStatus
    │   │   ├── EventBookingCommand.java, EventBookingResult.java, EventBookingConflict.java, BookingRequestState.java
    │   │   ├── domain/                           # Room, RoomName, BookingRequest, Booking, StatusChange, TimeSlot,
    │   │   │                                     # WeeklyRecurrence, Reason, RejectionReason, Actor, ActorRole,
    │   │   │                                     # Conflict, ids, excepciones de dominio
    │   │   ├── application/
    │   │   │   ├── port/in/                      # RequestBooking, Approve/Reject/CancelBookingRequest, CancelBooking,
    │   │   │   │                                 # RectifyBookingReason, ListBookingRequests, GetBookingRequest,
    │   │   │   │                                 # GetRoomOccupancy, ListRooms, CreateRoom, RenameRoom, DeactivateRoom,
    │   │   │   │                                 # AnonymizeRequester, PurgeExpiredBookings
    │   │   │   ├── port/out/                     # BookingRequestRepository, RoomRepository, BookingCounter,
    │   │   │   │                                 # RoomOccupancyQuery
    │   │   │   └── service/                      # implementaciones transaccionales + SpacesApi
    │   │   └── infrastructure/
    │   │       ├── in/rest/                      # RoomsController, BookingRequestsController, ActorResolver
    │   │       │                                 # (IdentityApi → Actor), SpacesProblemHandler, mappers
    │   │       ├── in/events/                    # UserDeletedListener → AnonymizeRequester
    │   │       ├── out/persistence/              # entidades JPA, repos Spring Data, adaptadores, mappers
    │   │       └── config/                       # wiring (Clock de ClockConfig con zona Europe/Madrid, R9), BookingPurgeJob
    │   └── agenda/                               # CREATE
    │       ├── package-info.java
    │       ├── domain/                           # Event, EventType, EventTitle, TimeSlot, Actor, ids, excepciones
    │       ├── application/
    │       │   ├── port/in/                      # CreateEvent, GetEvent, AnonymizeCreator, PurgeExpiredEvents
    │       │   ├── port/out/                     # EventRepository
    │       │   └── service/                      # CreateEventService (transaccional, usa SpacesApi), GetEventService
    │       └── infrastructure/
    │           ├── in/rest/                      # EventsController, ActorResolver, AgendaProblemHandler, mappers
    │           ├── in/events/                    # UserDeletedListener → AnonymizeCreator
    │           ├── out/persistence/              # entidad JPA, repo, adaptador
    │           └── config/                       # wiring (Clock de ClockConfig con zona Europe/Madrid, R9), EventPurgeJob
    ├── main/resources/db/migration/V3__spaces.sql, V4__agenda.sql   # CREATE
    └── test/java/io/github/escumartin/neocatecumenal/{spaces,agenda}/
        ├── domain/
        ├── application/                          # servicios con fakes en memoria
        └── infrastructure/                       # Testcontainers (incl. concurrencia) y MockMvc

app/src/
├── api/schema.d.ts                               # UPDATE generado por npm run api:generate
├── lib/api/client.ts                             # REUSE
├── lib/api/{rooms,booking-requests,events}-api.ts  # CREATE
├── hooks/{rooms,bookings,events}/                # CREATE: consultas y mutaciones
├── screens/{rooms,bookings,events}/              # CREATE: pantallas y __tests__ de accesibilidad
└── app/(app)/{reservas,salones,eventos}/         # CREATE: rutas Expo Router
```

**Structure Decision**: web-service en `backend/` (un módulo Modulith por contexto: `spaces` y `agenda` nuevos) más
cliente Expo en `app/`. El contrato vive en `api/`. Se mantiene la convención de `hexagonal-ddd`.

## Decisiones principales

Detalle y alternativas en [research.md](./research.md).

1. **Módulo `spaces`** para salones y reservas (R1).
2. **Agenda mínima → Spaces síncrono**: `CreateEventService` llama a `SpacesApi.requestForEvent` en su transacción; si
   el salón está ocupado o no está disponible, no se guarda nada (R2, R12). `spaces` no publica eventos de dominio: nadie
   los consume en esta feature y el estado de la solicitud se lee al consultar el evento.
3. **`BookingRequest` con ocurrencias `Booking`**: simple = serie de una; aprobar y rechazar son de la solicitud entera;
   cancelar admite una ocurrencia o la serie, solo ocurrencias que no han empezado (R3, R9).
4. **Exclusión GiST** `room_id =, period &&` con `[)`, parcial sobre `PENDING`/`APPROVED`. `23P01` → `409
   ROOM_OCCUPIED`; serie todo o nada en una transacción (R4). Como las pendientes ya bloquean, aprobar una serie
   pendiente nunca choca: la restricción lo garantiza por construcción.
5. **Bloqueo optimista** con `version` para resoluciones concurrentes (R5).
6. **Baja de salón lógica y definitiva**, bloqueada si hay reservas activas con franja futura; nombre único entre
   activos; bloqueo de fila del salón frente a solicitudes concurrentes (R10).
7. **Actor en el comando**: el adaptador REST llama a `IdentityApi.currentUser()`, construye el `Actor` propio del
   módulo (`userId`, rol, comunidad propia) y lo pasa en el comando junto con la comunidad elegida; sin puerto nuevo
   (R6). UUID internos (R11).
8. **Errores**: un `@RestControllerAdvice` por módulo limitado a sus controladores, con prioridad máxima; el global de
   identidad queda para lo demás (R13).
9. **RGPD**: anonimización síncrona al recibir `UserDeleted` y purga programada a un año (R14).
10. **Listado mínimo**: `listBookingRequests` filtra solo por `status` y `communityId` (FR-005, FR-006), paginado; sin
    filtros por salón ni fechas, que ninguna pantalla necesita.

## Validación

```bash
cd backend && ./mvnw -B verify
cd app && npm run lint && npm run typecheck && npx expo export -p web
```

Con Rancher Desktop, `export TESTCONTAINERS_RYUK_DISABLED=true` antes de `verify`. Tests de cliente: `cd app && npm test`.

## Pendientes fuera del ámbito de este plan

- Fusionar `contracts/room-bookings.openapi.yaml` en `api/openapi.yaml` (primera tarea de implementación).
- `dominio.md`: añadir los estados, la baja lógica de salones y el alcance de la agenda mínima.

## Complexity Tracking

Sin violaciones de la constitución.
