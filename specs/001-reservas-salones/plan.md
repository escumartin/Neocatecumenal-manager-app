# Implementation Plan: Reservas de salones

**Branch**: `001-reservas-salones-plan` | **Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-reservas-salones/spec.md`

## Summary

El administrador gestiona los salones parroquiales. Los moderadores solicitan un salón para una fecha y franja (una vez
o semanal con fecha fin) o dan de alta un evento con salón, que crea su solicitud. El administrador aprueba o rechaza.
Las solicitudes pendientes y aprobadas bloquean la franja; nunca hay dos activas solapadas en el mismo salón.

Enfoque técnico:

- Módulo Spring Modulith `spaces` («Espacios y reservas»): agregados `Room` (con alta, edición y baja lógica) y
  `BookingRequest` (solicitud o serie) con sus ocurrencias `Booking`.
- Módulo `agenda` mínimo: alta y consulta de un evento con salón; crea la solicitud con `SpacesApi.requestForEvent` en la
  misma transacción.
- Garantía de no solape con una restricción de exclusión GiST de PostgreSQL sobre las ocurrencias activas, más una
  consulta previa para informar de las fechas en conflicto.
- Contrato OpenAPI primero: borrador en `contracts/room-bookings.openapi.yaml`.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript strict (cliente)

**Primary Dependencies**:

- Backend: Spring Boot 3.5, Spring Modulith 1.4, Spring Data JPA, Flyway, Bean Validation.
- Generación: `openapi-generator-maven-plugin` (generador `spring`, `interfaceOnly`), nuevo en `backend/pom.xml`.
- Cliente: Expo 58, Expo Router, NativeWind, React Native Reusables; `openapi-typescript` y `openapi-fetch`, nuevos.

**Storage**: PostgreSQL 17 con la extensión `btree_gist` (restricción de exclusión); migraciones Flyway `V3__spaces.sql`
y `V4__agenda.sql`. Claves internas UUID (R11)

**Testing**:

- JUnit 5 y AssertJ sin Spring para dominio y aplicación, con puertos falsos en memoria.
- Testcontainers PostgreSQL para persistencia, incluidas pruebas concurrentes: dos solicitudes, dos resoluciones, y
  baja frente a solicitud.
- MockMvc para los adaptadores REST, con `CurrentActorProvider` sustituido por un doble de test.
- ArchUnit y `ApplicationModules.verify()`.

**Target Platform**: servidor Linux (JVM) y cliente Expo web, iOS y Android

**Project Type**: web-service + cliente móvil/web

**Performance Goals**: escala de parroquia (decenas de comunidades, < 10 salones, < 50 solicitudes/día); alta de
una serie de 53 ocurrencias < 500 ms p95

**Constraints**:

- Cero solapes activos, también con concurrencia (SC-002).
- Zona horaria única `Europe/Madrid`.
- Franja dentro de un solo día.
- Serie semanal con fecha fin ≤ 1 año (≤ 53 ocurrencias).
- Sin datos personales en reservas, eventos ni logs.

**Scale/Scope**:

- Backend: 2 módulos nuevos (`spaces`, `agenda` mínimo) y 14 operaciones HTTP.
- Cliente: 6 pantallas: solicitar, mis solicitudes, pendientes del administrador, ocupación por día, gestión de salones
  y alta de evento.

## Dependencies

- **Previa: feature «Identidad y acceso»**. Se especifica e implementa antes que esta. Aporta:
  - autenticación, rol del usuario y su comunidad (UUID interno, con número de comunidad e id numérico de acceso como
    atributos únicos);
  - la lista de comunidades que elige el administrador.
  
  Esta feature solo define el puerto `CurrentActorProvider` en los adaptadores REST de `spaces` y `agenda`, que se
  implementa sobre la API pública de identidad. No hay adaptador provisional ([research.md](./research.md) R6).
- Agenda completa (edición, borrado, listados, otros tipos de evento): feature posterior. Aquí solo el mínimo de R12.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple el plan | Pre | Post |
|---|---|---|---|
| I. Dominio puro | `spaces.domain` y `agenda.domain` sin Spring/JPA/Jackson; `Clock` se pasa como argumento | OK | OK |
| II. Puertos y adaptadores | puertos en `application.port.{in,out}` de cada módulo; REST y JPA en `infrastructure`; la traducción de `23P01` vive en el adaptador | OK | OK |
| III. Un módulo por contexto | `spaces` y `agenda` separados; `agenda → spaces` solo vía `SpacesApi` y tipos de su raíz; `spaces` no depende de `agenda`; sin tablas compartidas ni FK entre módulos; ids externos opacos | OK | OK |
| IV. Tests primero | tests de dominio y aplicación sin Spring antes del código; persistencia y concurrencia con Testcontainers; [quickstart.md](./quickstart.md) mapea cada escenario a su nivel de test | OK | OK |
| V. Contrato primero | borrador en `contracts/room-bookings.openapi.yaml`; primer paso de implementación: fusionarlo en `api/openapi.yaml` y generar interfaces Spring y tipos TS; nada generado se edita a mano | OK | OK |
| VI. Separación en cliente y tokens | acceso a datos y mapeo en `app/src/lib/api/` y hooks `app/src/hooks/{bookings,rooms,events}/`; pantallas solo con tokens de `design-system` | OK | OK |
| VII. Accesible para todas las edades | FR-012; selector de fecha y hora nativo con etiquetas, objetivos ≥ 48 px, texto ≥ 18 px, confirmaciones accesibles para cancelar y dar de baja; checklist de accesibilidad en la definición de hecho | OK | OK |
| VIII. Privacidad por diseño | solo los campos de FR-011; ids opacos; la ocupación no expone motivos ni comunidad ajena; el evento guarda título, sin personas; logs solo con ids y códigos ([research.md](./research.md) R8) | OK | OK |
| IX. Higiene de código | sin comentarios; identificadores en inglés; textos de usuario en ficheros de recursos (códigos de error del contrato → mensajes localizados en el cliente) | OK | OK |

Restricciones adicionales: stack conforme; `btree_gist` es una extensión estándar de PostgreSQL (no es una dependencia
nueva de terceros); sin secretos en el repo.

Coherencia con `dominio.md`:

- La baja de salón es definitiva y pide confirmación («borrar es definitivo»).
- El administrador elige comunidad (`communityId` obligatorio para él).
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
└── openapi.yaml                                  # se le fusiona contracts/room-bookings.openapi.yaml

backend/
├── pom.xml                                       # + openapi-generator-maven-plugin
└── src/
    ├── main/java/io/github/escumartin/neocatecumenal/
    │   ├── spaces/
    │   │   ├── SpacesApi.java                    # requestForEvent, bookingRequestStatus
    │   │   ├── EventBookingCommand.java, EventBookingResult.java, ActorRef.java
    │   │   ├── BookingRequestApproved.java, BookingRequestRejected.java, BookingRequestCancelled.java
    │   │   ├── domain/                           # Room, RoomName, BookingRequest, Booking, StatusChange, TimeSlot,
    │   │   │                                     # WeeklyRecurrence, Actor, Role, Conflict, ids, excepciones
    │   │   ├── application/
    │   │   │   ├── port/in/                      # RequestBooking, Approve/Reject/CancelBookingRequest, CancelBooking,
    │   │   │   │                                 # ListBookingRequests, GetBookingRequest, GetRoomOccupancy, ListRooms,
    │   │   │   │                                 # CreateRoom, RenameRoom, DeactivateRoom
    │   │   │   ├── port/out/                     # BookingRequestRepository, RoomRepository, BookingCounter,
    │   │   │   │                                 # RoomOccupancyQuery, SpacesEventPublisher
    │   │   │   └── service/                      # implementaciones transaccionales + SpacesApi
    │   │   └── infrastructure/
    │   │       ├── in/rest/                      # RoomsController, BookingRequestsController,
    │   │       │                                 # CurrentActorProvider (puerto), ProblemDetails handler, mappers
    │   │       ├── out/persistence/              # entidades JPA, repos Spring Data, adaptadores, mappers
    │   │       └── config/                       # Clock Europe/Madrid, wiring
    │   └── agenda/
    │       ├── domain/                           # Event, EventType, EventTitle, TimeSlot, ids
    │       ├── application/
    │       │   ├── port/in/                      # CreateEvent, GetEvent
    │       │   ├── port/out/                     # EventRepository
    │       │   └── service/                      # CreateEventService (transaccional, usa SpacesApi), GetEventService
    │       └── infrastructure/
    │           ├── in/rest/                      # EventsController, CurrentActorProvider (puerto), mappers
    │           └── out/persistence/              # entidad JPA, repo, adaptador
    ├── main/resources/db/migration/V3__spaces.sql, V4__agenda.sql
    └── test/java/io/github/escumartin/neocatecumenal/{spaces,agenda}/
        ├── domain/
        ├── application/                          # servicios con fakes en memoria
        └── infrastructure/                       # Testcontainers (incl. concurrencia) y MockMvc

app/src/
├── lib/api/                                      # cliente generado + openapi-fetch
├── hooks/{bookings,rooms,events}/                # consultas y mutaciones
├── screens/{bookings,rooms,events}/              # pantallas
└── app/(app)/{reservas,salones,eventos}/         # rutas Expo Router
```

**Structure Decision**: web-service en `backend/` (un módulo Modulith por contexto: `spaces` y `agenda` nuevos) más
cliente Expo en `app/`. El contrato vive en `api/`. Se mantiene la convención de `hexagonal-ddd`.

## Decisiones principales

Detalle y alternativas en [research.md](./research.md).

1. **Módulo `spaces`** para salones y reservas (R1).
2. **Agenda mínima → Spaces síncrono**: `CreateEventService` llama a `SpacesApi.requestForEvent` en su transacción; si
   el salón está ocupado, no se guarda nada (R2, R12).
3. **`BookingRequest` con ocurrencias `Booking`**: simple = serie de una; aprobar y rechazar son de la solicitud;
   cancelar admite una ocurrencia o la serie (solo futuras) (R3).
4. **Exclusión GiST** `room_id =, period &&` con `[)`, parcial sobre `PENDING`/`APPROVED`. `23P01` → `409
   ROOM_OCCUPIED`; serie todo o nada en una transacción (R4).
5. **Bloqueo optimista** con `version` para resoluciones concurrentes (R5).
6. **Baja de salón lógica y definitiva**, bloqueada si hay reservas activas de hoy en adelante; nombre único entre
   activos; bloqueo de fila del salón frente a solicitudes concurrentes (R10).
7. **Actor en el comando** vía puerto `CurrentActorProvider`, implementado por Identidad (R6). UUID internos (R11).
8. **Problem Details** con `code` estable para mensajes localizados (R7).

## Pendientes fuera del ámbito de este plan

- Fusionar `contracts/room-bookings.openapi.yaml` en `api/openapi.yaml` (primera tarea de implementación).
- `dominio.md`: añadir el flujo de solicitud y aprobación, los estados, la baja lógica de salones y el alcance de la
  agenda mínima.

## Complexity Tracking

Sin violaciones de la constitución.
