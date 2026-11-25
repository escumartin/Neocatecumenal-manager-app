# Tasks: Reservas de salones

**Input**: Design documents from `/specs/001-reservas-salones/`

**Prerequisites**: plan.md, spec.md, research.md (R1-R14), data-model.md, contracts/room-bookings.openapi.yaml, quickstart.md

**Tests**: obligatorios (constitución IV, regla 3 del proyecto): cada regla de negocio nace de un test que falla. Dentro de cada historia el orden es capa a capa: dominio, aplicación, adaptadores, contrato/API, cliente; en cada capa el test va antes que su implementación y debe verse fallar. Cada FR-001 a FR-023 tiene al menos una tarea de test con su ID; los casos de error siguen quickstart.md § 2.

**Organization**: tareas agrupadas por historia de usuario (US1-US6). Cero comentarios en código y configuración. `spaces` no publica eventos de dominio.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede ejecutar en paralelo (ficheros distintos, sin dependencias)
- **[Story]**: US1 a US6
- Las citas `data-model.md Entidad.campo` indican el campo restringido que la tarea implementa o valida

## Path Conventions

- `B/` = `backend/src/main/java/io/github/escumartin/neocatecumenal/` (módulos `B/spaces/` y `B/agenda/`)
- `BT/` = `backend/src/test/java/io/github/escumartin/neocatecumenal/` (`BT/spaces/`, `BT/agenda/`)
- `BR/` = `backend/src/main/resources/`
- `A/` = `app/src/`
- Contrato: `api/openapi.yaml` (fuente de verdad; borrador en `specs/001-reservas-salones/contracts/room-bookings.openapi.yaml`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: contrato primero y esqueleto de módulos; sin dependencias nuevas ni cambios en `backend/pom.xml`

- [x] T001 Fusionar `specs/001-reservas-salones/contracts/room-bookings.openapi.yaml` en `api/openapi.yaml` según la regla de fusión de plan.md (aditiva: `securitySchemes`, `Problem`, `ValidationProblem` y respuestas compartidas de `api/openapi.yaml`; referencias externas a locales; unión de `x-merge.problemCodes` y `x-merge.validationCodes` en los enums de `code`; respuestas `BookingNotFound` y `BookingStateConflict`; eliminar `x-merge`; `info.version` 0.2.0) y validar con Redocly (`api/redocly.yaml`)
- [x] T002 [P] Generar las interfaces del backend (`RoomsApi`, `BookingRequestsApi`, `EventsApi`) con `cd backend && ./mvnw -B generate-sources` y comprobar que compilan sin editar nada generado
- [x] T003 [P] Regenerar los tipos del cliente en `A/api/schema.d.ts` (`cd app && npm run api:generate` y `npm run api:check`)
- [x] T004 [P] Crear el esqueleto del módulo `spaces` en `B/spaces/` (`package-info.java` con `@ApplicationModule`; paquetes `domain`, `application/port/in`, `application/port/out`, `application/service`, `infrastructure/{in/rest,in/events,out/persistence,config}`)
- [x] T005 [P] Crear el esqueleto del módulo `agenda` en `B/agenda/` (`package-info.java` con `@ApplicationModule`; paquetes `domain`, `application/port/in`, `application/port/out`, `application/service`, `infrastructure/{in/rest,in/events,out/persistence,config}`)
- [x] T006 Comprobar, sin cambiar `BT/ArchitectureTest.java` (sus reglas ya usan `..domain..` y `..application..`), que cubre `spaces.domain` y `agenda.domain` (sin Spring, JPA ni Jackson) y `application` sin dependencia de `infrastructure`, y que `BT/ArchitectureTest.java` y `BT/ModularityTest.java` (`ApplicationModules.verify()`) quedan verdes con los esqueletos (principios I a III)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: migraciones, tipos de actor, política de comunidad efectiva, resolución del actor y manejadores de errores por módulo. Bloquea todas las historias.

**CRITICAL**: ninguna historia empieza hasta cerrar esta fase.

### Tests (escribir primero, ver fallar)

- [x] T108 Mover el bean `Clock` (`Clock.systemUTC()`) de `B/identity/infrastructure/config/IdentityConfig.java` a `B/ClockConfig.java` (paquete raíz `io.github.escumartin.neocatecumenal`, fuera de los módulos); `identity` sigue inyectándolo por tipo y `spaces` y `agenda` lo inyectan sin declarar otro (R9); los tests de `identity` y `ApplicationContextTest` siguen verdes; antes de T013 y T020 (ID fuera de orden para no renumerar)
- [x] T007 [P] Test de Flyway y restricciones SQL de `V3__spaces.sql` con Testcontainers en `BT/spaces/infrastructure/SpacesSchemaMigrationIT.java`: extensión `btree_gist`; exclusión GiST `room_id =, period &&` solo para `PENDING` y `APPROVED`, con contiguas y otro salón permitidos y `REJECTED`/`CANCELLED` que no bloquean; `check (end_time > start_time)`, `check (last_date >= first_date)`, `status in (...)`; índice único parcial `lower(name) WHERE active`; `event_id` único; cascada de `booking` y `booking_status_change` al borrar `booking_request` (FR-003, FR-013, FR-017, FR-022; data-model.md `Room.name`, `Room.active`, `BookingRequest.slot`, `BookingRequest.lastDate`, `BookingRequest.status`, `BookingRequest.eventId`, `Booking.status`)
- [x] T008 [P] Test de Flyway y restricciones SQL de `V4__agenda.sql` con Testcontainers en `BT/agenda/infrastructure/AgendaSchemaMigrationIT.java`: `check (type in ('WORD','CONVIVENCE'))`, `title` varchar(120), `check (end_time > start_time)`, `booking_request_id` único, sin FK hacia `spaces` (FR-020; data-model.md `Event.type`, `Event.title`, `Event.slot`, `Event.bookingRequestId`)
- [x] T009 [P] Tests de `BookingPolicy.actingCommunity` en `BT/spaces/domain/BookingPolicyTest.java` con la tabla completa de data-model.md (`REGISTERED` siempre `SpacesForbidden`; moderador con comunidad ausente o propia; moderador con otra comunidad `SpacesForbidden`; administrador con comunidad presente; administrador sin comunidad `SpacesFieldInvalid(communityId, COMMUNITY_REQUIRED)`) (FR-001, FR-008, FR-009)
- [x] T010 [P] Tests de `ActorResolver` de `spaces` en `BT/spaces/infrastructure/ActorResolverTest.java` con `@MockitoBean IdentityApi`: copia solo `userId`, rol y `communityId` (sin nombre ni `accessId`), sin `CurrentUser` responde `401`, comunidad elegida por el administrador inexistente según `IdentityApi.community(id)` da `422 COMMUNITY_NOT_FOUND` (FR-001, FR-011)
- [x] T011 [P] Tests de `SpacesProblemHandler` en `BT/spaces/infrastructure/SpacesProblemHandlerTest.java`: cada excepción de R13 a su `status` y `code` estable (`ROOM_OCCUPIED` con `conflicts`, `BOOKING_REQUEST_ALREADY_RESOLVED` incluido `OptimisticLockingFailureException`, `BOOKING_REQUEST_NOT_ACTIVE`, `BOOKING_ALREADY_STARTED`, `ROOM_NAME_TAKEN`, `ROOM_INACTIVE`, `ROOM_HAS_ACTIVE_BOOKINGS` con `activeBookings`, `404 NOT_FOUND`, `403 FORBIDDEN`, `422 VALIDATION_FAILED` con `errors`), limitado a los controladores de `spaces`, sin manejadores genéricos y con logs solo de `code` y `status` (FR-004, FR-005, FR-011)
- [x] T012 [P] Tests de `ActorResolver` y `AgendaProblemHandler` de `agenda` en `BT/agenda/infrastructure/AgendaInfrastructureBaseTest.java`: mismo comportamiento de actor que en `spaces`, `RoomOccupied` a `409 ROOM_OCCUPIED` con `conflicts`, `RoomInactive` a `409 ROOM_INACTIVE`, `RoomNotFound` y `EventNotFound` a `404`, `AgendaForbidden` a `403`, `AgendaFieldInvalid` a `422` (FR-014, FR-020, FR-021)
- [x] T013 [P] Tests del reloj en `BT/spaces/infrastructure/SpacesClockWiringTest.java` y `BT/agenda/infrastructure/AgendaClockWiringTest.java`: el contexto sigue teniendo un único bean `Clock` (el de `ClockConfig`, compartido con `identity`) y el reloj que reciben los servicios de cada módulo tiene zona `Europe/Madrid` (R9; FR-002)

### Implementation

- [x] T014 Migración `BR/db/migration/V3__spaces.sql` con `CREATE EXTENSION IF NOT EXISTS btree_gist`, `room`, `booking_request`, `booking` (columna generada `period`, exclusión GiST parcial) y `booking_status_change`, con sus checks e índices, según data-model.md Persistencia (data-model.md `Room.name`, `Room.active`, `BookingRequest.reason`, `BookingRequest.rejectionReason`, `BookingRequest.firstDate`, `BookingRequest.lastDate`, `BookingRequest.slot`, `BookingRequest.recurrence`, `BookingRequest.status`, `BookingRequest.eventId`, `Booking.status`, `StatusChange.changedBy`); `V1` y `V2` no se tocan
- [x] T015 Migración `BR/db/migration/V4__agenda.sql` con la tabla `event` e índices, sin FK hacia `spaces` (data-model.md `Event.type`, `Event.title`, `Event.date`, `Event.slot`, `Event.bookingRequestId`, `Event.createdBy`)
- [x] T016 [P] Tipos base de dominio de `spaces` en `B/spaces/domain/`: `RoomId`, `BookingRequestId`, `BookingId`, `CommunityId`, `UserId`, `EventId`, `Actor`, `ActorRole`, `BookingPolicy.actingCommunity` (data-model.md `Actor.role`, `BookingRequest.communityId`) y las excepciones `SpacesForbidden`, `SpacesFieldInvalid`, `RoomOccupied`, `BookingRequestNotPending`, `BookingRequestNotActive`, `BookingAlreadyStarted`, `RoomNameTaken`, `RoomInactive`, `RoomHasActiveBookings`, `RoomNotFound`, `BookingRequestNotFound`
- [x] T017 [P] Tipos base de dominio de `agenda` en `B/agenda/domain/`: `EventId`, `CommunityId`, `UserId`, `RoomId`, `BookingRequestId`, `Actor`, `ActorRole` (data-model.md `Actor.role`) y las excepciones `AgendaForbidden`, `AgendaFieldInvalid`, `RoomOccupied`, `RoomInactive`, `RoomNotFound`, `EventNotFound`
- [x] T018 [P] `ActorResolver` y `SpacesProblemHandler` (`@RestControllerAdvice` con `@Order(Ordered.HIGHEST_PRECEDENCE)` limitado a los controladores de `spaces`) en `B/spaces/infrastructure/in/rest/`, apoyados en `IdentityApi.currentUser()` e `IdentityApi.community(id)`, sin puertos nuevos de identidad
- [x] T019 [P] `ActorResolver` y `AgendaProblemHandler` en `B/agenda/infrastructure/in/rest/`, con el mismo criterio y limitado a los controladores de `agenda`
- [x] T020 [P] Cableado en `B/spaces/infrastructure/config/` y `B/agenda/infrastructure/config/` sin bean `Clock` nuevo y tras T108: los servicios reciben el `Clock` de `ClockConfig` con `withZone(ZoneId.of("Europe/Madrid"))` (R9)

**Checkpoint**: `cd backend && ./mvnw -B verify` en verde con la base; las historias pueden empezar.

---

## Phase 3: User Story 6 - Gestionar los salones (Priority: P1) 🎯 MVP

**Goal**: el administrador da de alta, renombra y da de baja salones; el resto lista los activos. Va primero dentro de P1 porque sin salones no hay nada que solicitar.

**Independent Test**: el administrador crea un salón, lo renombra y lo da de baja; deja de aparecer en `GET /api/rooms` y su historial se conserva.

**Cubre**: FR-017, FR-018, FR-019 (listado).

### Tests de dominio

- [x] T021 [P] [US6] Tests de `RoomName` en `BT/spaces/domain/RoomNameTest.java`: 1 a 80 caracteres y recortado (data-model.md `Room.name`) (FR-017)
- [x] T022 [P] [US6] Tests de `Room` y `RoomPolicy` en `BT/spaces/domain/RoomTest.java`: `create`, `rename` solo activo (`RoomInactive`), `deactivate` con `activeFutureBookings == 0` (solo cuentan aprobadas y pendientes con franja futura) y si no `RoomHasActiveBookings(count)`, baja definitiva, solo administrador (`SpacesForbidden`) (FR-017, FR-018, FR-019; data-model.md `Room.name`, `Room.active`)

### Implementación de dominio

- [x] T023 [US6] `RoomName`, `Room` y `RoomPolicy` en `B/spaces/domain/` (data-model.md `Room.name`, `Room.active`)

### Tests de aplicación

- [x] T024 [P] [US6] Tests de `CreateRoomService`, `RenameRoomService`, `DeactivateRoomService` y `ListRoomsService` con fakes en `BT/spaces/application/RoomServicesTest.java`: nombre repetido entre activos sin distinguir mayúsculas da `RoomNameTaken`, la baja usa `findByIdForUpdate` y `BookingCounter.activeFutureBookings(roomId, now)`, el listado solo devuelve activos (FR-017, FR-018, FR-019; data-model.md `Room.name`)

### Implementación de aplicación

- [x] T025 [US6] Puertos de entrada `CreateRoom`, `RenameRoom`, `DeactivateRoom`, `ListRooms` y de salida `RoomRepository` (`findById`, `findByIdForUpdate`, `findByIdForShare`, `findAllActive`, `existsActiveByName`, `save`) y `BookingCounter` en `B/spaces/application/port/`, y sus servicios transaccionales en `B/spaces/application/service/`

### Tests de adaptadores

- [x] T026 [P] [US6] Tests Testcontainers de `RoomRepository` y `BookingCounter` en `BT/spaces/infrastructure/RoomPersistenceIT.java`: índice único parcial entre activos sin distinguir mayúsculas, un nombre de salón dado de baja se puede reutilizar, `findByIdForUpdate` y `findByIdForShare`, el contador solo cuenta ocurrencias `PENDING` y `APPROVED` con `date + startTime > now`: una pendiente pasada o ya empezada no cuenta (FR-017, FR-018; data-model.md `Room.name`, `Room.active`)
- [x] T027 [P] [US6] Tests MockMvc de `RoomsController` en `BT/spaces/infrastructure/RoomsControllerTest.java`: `POST /api/rooms` `201` con `Location`, `PUT` `200`, `POST .../deactivation` `204`, `GET /api/rooms` solo activos; nombre vacío `422 REQUIRED`, repetido `409 ROOM_NAME_TAKEN`, moderador o `REGISTERED` `403`, baja con reservas activas `409 ROOM_HAS_ACTIVE_BOOKINGS` con `activeBookings`, renombrar o dar de baja uno ya dado de baja `409 ROOM_INACTIVE`, inexistente `404` (FR-017, FR-018, FR-019; data-model.md `Room.name`)

### Implementación de adaptadores

- [x] T028 [US6] Entidad JPA `RoomEntity` con `@Version`, repositorio Spring Data con bloqueos `FOR UPDATE` y `FOR SHARE`, adaptadores `RoomRepository` y `BookingCounter` (consulta nativa sobre `booking`) y mappers en `B/spaces/infrastructure/out/persistence/` (data-model.md `Room.name`, `Room.active`)
- [x] T029 [US6] `RoomsController` implementando las operaciones de salones de `RoomsApi` (`listRooms`, `createRoom`, `renameRoom`, `deactivateRoom`) con mappers en `B/spaces/infrastructure/in/rest/`

### Cliente

- [x] T030 [P] [US6] Tests de componente de la pantalla de gestión de salones en `A/screens/rooms/__tests__/RoomsAdminScreen.test.tsx`: cada control con `accessibilityRole` y `accessibilityLabel`, botones de altura ≥ 48 px y textos ≥ 18 px con la variante de `design-system`, el diálogo de baja es accesible (rol `alertdialog`, foco, etiquetas) y avisa de que es definitivo; y test de mapeo en `A/lib/api/__tests__/rooms-api.test.ts` (FR-012, FR-017, FR-018)
- [x] T031 [US6] `A/lib/api/rooms-api.ts`, hooks de salones en `A/hooks/rooms/`, pantalla de gestión con confirmación de baja en `A/screens/rooms/`, ruta en `A/app/(app)/salones/` y mensajes localizados de `ROOM_NAME_TAKEN`, `ROOM_INACTIVE` y `ROOM_HAS_ACTIVE_BOOKINGS` en `A/i18n/` solo con tokens de `design-system`

**Checkpoint**: US6 funcional de punta a punta; ya hay salones para las demás historias.

---

## Phase 4: User Story 1 - Solicitar un salón para una fecha y franja (Priority: P1)

**Goal**: el moderador, o el administrador para la comunidad que elige, solicita un salón para una fecha y franja con motivo; queda pendiente, la ve en su lista y puede rectificar el motivo.

**Independent Test**: un moderador crea una solicitud para un salón libre y la ve en su lista como pendiente, con salón, fecha, franja y motivo.

**Cubre**: FR-001, FR-002, FR-006, FR-010 (alta), FR-011, FR-012 (solicitar), FR-019 (solicitar), FR-023, SC-001.

### Tests de dominio

- [x] T032 [P] [US1] Tests de `TimeSlot` en `BT/spaces/domain/TimeSlotTest.java`: `end > start`, mismo día, precisión de minutos (`END_NOT_AFTER_START`) (FR-002; data-model.md `BookingRequest.slot`)
- [x] T033 [P] [US1] Tests de `Reason` en `BT/spaces/domain/ReasonTest.java`: 1 a 500 caracteres y recortado, 501 es `TOO_LONG` (FR-023; data-model.md `BookingRequest.reason`)
- [x] T034 [P] [US1] Tests de `BookingRequest.request` y `rectifyReason` en `BT/spaces/domain/BookingRequestTest.java`: solicitud simple con una ocurrencia `PENDING`, `StatusChange(null a PENDING)` con `changedBy` y `changedAt`, `START_NOT_IN_FUTURE` con fecha pasada o inicio ya pasado hoy, hoy con franja que empieza dentro de una hora válido, rectificar solo en `PENDING` (`BookingRequestNotPending`) sin tocar el historial de estado (FR-001, FR-002, FR-010, FR-023; data-model.md `BookingRequest.firstDate`, `BookingRequest.lastDate`, `BookingRequest.slot`, `BookingRequest.reason`, `BookingRequest.status`)

### Implementación de dominio

- [x] T035 [US1] `TimeSlot`, `Reason`, `BookingRequestStatus`, `BookingStatus`, `Booking`, `StatusChange` y `BookingRequest` (`request`, `rectifyReason`) en `B/spaces/domain/` (data-model.md `BookingRequest.requestedBy`, `BookingRequest.reason`, `BookingRequest.firstDate`, `BookingRequest.lastDate`, `BookingRequest.slot`, `BookingRequest.status`, `Booking.date`, `Booking.slot`, `Booking.status`, `StatusChange.bookingId`, `StatusChange.from`, `StatusChange.changedBy`)

### Tests de aplicación

- [x] T036 [P] [US1] Tests de `RequestBookingService` con fakes y reloj fijo en `BT/spaces/application/RequestBookingServiceTest.java`: alta como moderador y como administrador con comunidad elegida, salón bloqueado con `findByIdForShare`, `RoomNotFound`, `RoomInactive`, moderador con otra comunidad y `REGISTERED` `SpacesForbidden`, administrador sin comunidad `COMMUNITY_REQUIRED`, nada guardado ante un error (FR-001, FR-002, FR-019; data-model.md `BookingRequest.requestedBy`)
- [x] T037 [P] [US1] Tests de `RectifyBookingReasonService`, `ListBookingRequestsService` y `GetBookingRequestService` con fakes en `BT/spaces/application/BookingRequestQueriesTest.java`: moderador solo ve y rectifica las de su comunidad, administrador cualquiera, `communityId` de otra comunidad y `GET` de una ajena dan `SpacesForbidden`, listado paginado filtrado solo por `status` y `communityId` (FR-006, FR-023)

### Implementación de aplicación

- [x] T038 [US1] Puertos de entrada `RequestBooking`, `RectifyBookingReason`, `ListBookingRequests`, `GetBookingRequest`, puerto de salida `BookingRequestRepository` (`save`, `findById`, `search(communityId?, statuses, page)`) y servicios transaccionales en `B/spaces/application/`

### Tests de adaptadores

- [x] T039 [P] [US1] Tests Testcontainers de `BookingRequestRepository` en `BT/spaces/infrastructure/BookingRequestPersistenceIT.java`: ida y vuelta con ocurrencias e historial, `booking.room_id` fijado por el adaptador, `search` por comunidad y estado paginado, motivo de 500 caracteres, `@Version`, la tabla solo guarda las columnas de data-model.md y ningún dato personal adicional (FR-011, FR-023; data-model.md `BookingRequest.requestedBy`, `BookingRequest.reason`, `BookingRequest.status`)
- [x] T040 [P] [US1] Test de concurrencia baja contra solicitud en `BT/spaces/infrastructure/RoomDeactivationVsRequestIT.java`: dos hilos, la baja con `FOR UPDATE` y el alta con `FOR SHARE` nunca dejan una solicitud activa en un salón dado de baja (FR-018, FR-019)
- [x] T041 [P] [US1] Tests MockMvc de `BookingRequestsController` en `BT/spaces/infrastructure/BookingRequestsControllerTest.java`: `POST` `201 PENDING` con `Location` como moderador A y como administrador con `communityId=B`; administrador sin `communityId` `422 COMMUNITY_REQUIRED`; comunidad inexistente `422 COMMUNITY_NOT_FOUND`; moderador A con `communityId=B` y `REGISTERED` `403`; `END_NOT_AFTER_START` y `START_NOT_IN_FUTURE` `422` sin guardar nada; motivo de 500 `201` y de 501 `422 TOO_LONG`; `GET` lista y detalle por rol (moderador A solo A, administrador A y B, `communityId=B` ajeno `403`, detalle ajeno `403`); `PUT .../reason` `200`, sobre una `APPROVED` `409 BOOKING_REQUEST_ALREADY_RESOLVED`, de otra comunidad `403` (FR-001, FR-002, FR-006, FR-023; data-model.md `BookingRequest.reason`, `BookingRequest.slot`)

### Implementación de adaptadores

- [x] T042 [US1] Entidades JPA `BookingRequestEntity`, `BookingEntity` (sin mapear `period`) y `BookingStatusChangeEntity`, repositorio Spring Data, adaptador `BookingRequestRepository` y mappers en `B/spaces/infrastructure/out/persistence/` (data-model.md `BookingRequest.reason`, `BookingRequest.firstDate`, `BookingRequest.lastDate`, `BookingRequest.slot`, `BookingRequest.status`, `BookingRequest.requestedBy`, `StatusChange.changedBy`)
- [x] T043 [US1] `BookingRequestsController` implementando `createBookingRequest`, `listBookingRequests`, `getBookingRequest` y `rectifyBookingRequestReason` de `BookingRequestsApi` con mappers en `B/spaces/infrastructure/in/rest/`, con `ActorResolver` y comprobación de la comunidad elegida con `IdentityApi.community`

### Cliente

- [x] T044 [P] [US1] Tests de componente en `A/screens/bookings/__tests__/RequestBookingScreen.test.tsx` y `MyRequestsScreen.test.tsx`: roles y etiquetas en todos los controles, botones ≥ 48 px y textos ≥ 18 px, el campo de motivo avisa de no incluir datos personales y corta en 500 caracteres, el administrador debe elegir comunidad antes de solicitar, el moderador corrige el motivo mientras la solicitud es `PENDING`; y test de mapeo en `A/lib/api/__tests__/booking-requests-api.test.ts` (FR-001, FR-006, FR-012, FR-023)
- [x] T045 [US1] `A/lib/api/booking-requests-api.ts`, hooks en `A/hooks/bookings/`, pantallas de solicitar y de mis solicitudes en `A/screens/bookings/`, selector de comunidad para el administrador con `A/lib/api/communities-api.ts`, rutas en `A/app/(app)/reservas/` y mensajes localizados de `START_NOT_IN_FUTURE`, `END_NOT_AFTER_START`, `COMMUNITY_REQUIRED` y `COMMUNITY_NOT_FOUND` en `A/i18n/`

**Checkpoint**: US1 funcional; solicitud de menos de 2 minutos desde el móvil (SC-001) comprobable en la prueba manual.

---

## Phase 5: User Story 2 - Evitar solapamientos de salón (Priority: P1)

**Goal**: dos reservas activas nunca ocupan el mismo salón en franjas que se solapan, ni con concurrencia; el moderador ve antes las franjas ocupadas.

**Independent Test**: con una reserva activa de 18:00 a 20:00, 19:00-21:00, 17:00-18:30 y 17:00-21:00 se rechazan con `ROOM_OCCUPIED`; 20:00-21:00, 16:00-18:00 y otro salón se aceptan.

**Cubre**: FR-003, FR-008, FR-012 (ocupación), FR-013, FR-019 (ocupación), SC-002.

### Tests de dominio

- [x] T046 [P] [US2] Tests de `TimeSlot.overlaps` y `Conflict` en `BT/spaces/domain/TimeSlotOverlapTest.java`: semiabierto `a.start < b.end && b.start < a.end`, inicio dentro, fin dentro, envolvente y contiguas (FR-003, SC-002; data-model.md `BookingRequest.slot`)
- [x] T047 [P] [US2] Tests en `BT/spaces/domain/BookingPolicyOccupancyTest.java` de la comunidad efectiva al consultar ocupación (administrador sin comunidad `COMMUNITY_REQUIRED`, moderador con otra comunidad `SpacesForbidden`) (FR-008)

### Implementación de dominio

- [x] T048 [US2] `TimeSlot.overlaps` y `Conflict(date, slot)` en `B/spaces/domain/`

### Tests de aplicación

- [x] T049 [P] [US2] Tests en `BT/spaces/application/RequestBookingConflictTest.java`: `findConflicts` con choque da `RoomOccupied` con la fecha y la franja y no guarda nada, contiguas y otro salón se aceptan, una ocurrencia `REJECTED` o `CANCELLED` en el repositorio falso no bloquea una nueva solicitud; las transiciones llegan en T060 y T089 (FR-003, FR-013)
- [x] T050 [P] [US2] Tests de `GetRoomOccupancyService` con fakes en `BT/spaces/application/GetRoomOccupancyServiceTest.java`: solo salones activos (inexistente o dado de baja `RoomNotFound`), `ownCommunity` según la comunidad efectiva, nunca motivos ni ids de personas (FR-008, FR-019)

### Implementación de aplicación

- [x] T051 [US2] Puerto de entrada `GetRoomOccupancy`, puertos de salida `RoomOccupancyQuery` (`occupiedSlots(roomId, date)`) y `BookingRequestRepository.findConflicts(roomId, dates, slot)`, la comprobación previa de conflictos en `RequestBookingService` y `GetRoomOccupancyService` en `B/spaces/application/`

### Tests de adaptadores

- [x] T052 [P] [US2] Tests Testcontainers de solape en `BT/spaces/infrastructure/BookingOverlapIT.java`: 19-21, 17-18:30 y 17-21 contra 18-20 dan `RoomOccupied`; 20-21, 16-18 y otro salón se guardan; el `23P01` de la carrera se traduce a `RoomOccupied(conflicts = [])` y deshace la transacción; filas `REJECTED` o `CANCELLED` insertadas directamente no bloquean la franja (FR-003, FR-013, SC-002; data-model.md `BookingRequest.slot`)
- [x] T053 [P] [US2] Test de concurrencia en `BT/spaces/infrastructure/BookingConcurrencyIT.java`: dos hilos piden la misma franja y salón, una `201` y otra `409 ROOM_OCCUPIED` (posiblemente con `conflicts` vacío), una sola fila activa (FR-013, SC-002)
- [x] T054 [P] [US2] Tests MockMvc en `BT/spaces/infrastructure/RoomOccupancyControllerTest.java` y ampliación de `BookingRequestsControllerTest.java`: `GET /api/rooms/{id}/occupancy` devuelve franjas con `ownCommunity=false` para otra comunidad y la respuesta no contiene `reason` ni ids de personas (aserción sobre el JSON); administrador sin `communityId` `422 COMMUNITY_REQUIRED`; moderador A con `communityId=B` `403`; salón dado de baja `404`; `POST` solapado `409 ROOM_OCCUPIED` con el conflicto; solicitar un salón dado de baja `409 ROOM_INACTIVE` (FR-003, FR-008, FR-019)

### Implementación de adaptadores

- [x] T055 [US2] Consulta `findConflicts`, traducción de `23P01` a `RoomOccupied` en `save` con flush, y adaptador `RoomOccupancyQuery` (solo `PENDING` y `APPROVED`, con indicador de comunidad propia, sin motivo) en `B/spaces/infrastructure/out/persistence/`
- [x] T056 [US2] `getRoomOccupancy` en `RoomsController` y mapper de `RoomOccupancy` y `OccupiedSlot` en `B/spaces/infrastructure/in/rest/`

### Cliente

- [x] T057 [P] [US2] Tests de componente en `A/screens/bookings/__tests__/RoomOccupancyScreen.test.tsx`: la pantalla de ocupación por día muestra las franjas con roles y etiquetas, el administrador debe elegir comunidad, y un `ROOM_OCCUPIED` muestra un mensaje claro con las fechas en conflicto; y mapeo en `A/lib/api/__tests__/rooms-api.test.ts` (FR-003, FR-008, FR-012)
- [x] T058 [US2] Hook de ocupación en `A/hooks/rooms/`, pantalla de ocupación por día en `A/screens/rooms/`, franjas ocupadas visibles en el formulario de solicitar, mensajes de `ROOM_OCCUPIED` con fechas y de conflicto sin fechas (pide recargar) en `A/i18n/`

**Checkpoint**: US2 funcional; ninguna solicitud solapa, con concurrencia incluida (SC-002).

---

## Phase 6: User Story 3 - Aprobar o rechazar solicitudes (Priority: P2)

**Goal**: el administrador ve las pendientes de todas las comunidades y aprueba o rechaza cada solicitud o serie entera; el solicitante ve el resultado.

**Independent Test**: el administrador aprueba una solicitud pendiente y rechaza otra con motivo; ambos moderadores ven el nuevo estado.

**Cubre**: FR-004 (aprobar y rechazar), FR-005, FR-009, FR-010 (resolución), FR-012 (pendientes), FR-013 (rechazo), FR-023 (motivo de rechazo), SC-003.

### Tests de dominio

- [x] T059 [P] [US3] Tests de `RejectionReason` en `BT/spaces/domain/RejectionReasonTest.java`: opcional, 1 a 500 caracteres y recortado (data-model.md `BookingRequest.rejectionReason`) (FR-023)
- [x] T060 [P] [US3] Tests de `approve` y `reject` en `BT/spaces/domain/BookingRequestResolutionTest.java`: solo administrador (`SpacesForbidden`), solo desde `PENDING` (`BookingRequestNotPending`), todas las ocurrencias `PENDING` de la serie pasan a `APPROVED` o `REJECTED` (la parte de series, tras T073), el rechazo libera franjas y guarda el motivo, aprobar una solicitud o serie cuya primera ocurrencia ya empezó da `StartNotInFuture` y rechazarla sí se permite, cada cambio añade `StatusChange`, una transición fallida no añade historial (FR-004, FR-005, FR-009, FR-010, FR-013; data-model.md `BookingRequest.status`, `BookingRequest.rejectionReason`, `Booking.status`)

### Implementación de dominio

- [x] T061 [US3] `RejectionReason`, `BookingRequest.approve` y `BookingRequest.reject` en `B/spaces/domain/` (data-model.md `BookingRequest.rejectionReason`, `BookingRequest.status`)

### Tests de aplicación

- [x] T062 [P] [US3] Tests de `ApproveBookingRequestService` y `RejectBookingRequestService` con fakes en `BT/spaces/application/ResolveBookingRequestServiceTest.java`: administrador aprueba y rechaza, moderador y `REGISTERED` `SpacesForbidden`, solicitud inexistente `BookingRequestNotFound`, estado ya resuelto `BookingRequestNotPending` y `OptimisticLockingFailureException` propagada sin capturar (la traduce `SpacesProblemHandler`, T011), el listado `status=PENDING` sin `communityId` incluye todas las comunidades (FR-005, FR-009)

### Implementación de aplicación

- [x] T063 [US3] Puertos de entrada `ApproveBookingRequest`, `RejectBookingRequest` y servicios transaccionales en `B/spaces/application/`

### Tests de adaptadores

- [x] T064 [P] [US3] Tests Testcontainers en `BT/spaces/infrastructure/BookingResolutionIT.java` (la parte de series, tras T073 y T085): aprobar una serie deja todas sus ocurrencias `APPROVED`; invariante: no se pueden insertar ocurrencias activas solapadas con una serie pendiente, así que aprobarla nunca choca; dos administradores a la vez, uno `200` y otro conflicto por `@Version`; aprobar una serie pendiente con la primera ocurrencia ya empezada da `422 START_NOT_IN_FUTURE` y rechazarla sí vale; rechazar libera la franja (FR-004, FR-005, FR-013; data-model.md `BookingRequest.status`, `Booking.status`)
- [x] T065 [P] [US3] Tests MockMvc en `BT/spaces/infrastructure/ResolutionControllerTest.java`: `POST .../approval` y `.../rejection` `200` con y sin cuerpo, rechazo con motivo de 500 `200` y de 501 `422 TOO_LONG`, moderador o `REGISTERED` `403`, repetir `409 BOOKING_REQUEST_ALREADY_RESOLVED`, aprobar una pendiente con la primera ocurrencia ya empezada `422 START_NOT_IN_FUTURE` (rechazarla `200`), aprobar una `REJECTED` o `CANCELLED` `409`, el administrador lista `status=PENDING` y ve las de A y B (FR-004, FR-005, FR-009, FR-023; data-model.md `BookingRequest.rejectionReason`)

### Implementación de adaptadores

- [x] T066 [US3] `approveBookingRequest` y `rejectBookingRequest` en `BookingRequestsController`, persistencia del motivo de rechazo y del historial en el adaptador de `B/spaces/infrastructure/out/persistence/` (data-model.md `BookingRequest.rejectionReason`)

### Cliente

- [x] T067 [P] [US3] Tests de componente en `A/screens/bookings/__tests__/PendingRequestsScreen.test.tsx`: lista de pendientes del administrador con roles y etiquetas, aprobar y rechazar con botones ≥ 48 px, el campo de motivo de rechazo avisa de no incluir datos personales y corta en 500, el solicitante ve estado y motivo de rechazo en su lista (FR-005, FR-009, FR-012, FR-023)
- [x] T068 [US3] Mutaciones de aprobar y rechazar en `A/hooks/bookings/`, pantalla de pendientes en `A/screens/bookings/`, ruta en `A/app/(app)/reservas/`, estado y motivo de rechazo en la pantalla de mis solicitudes y mensaje localizado de `BOOKING_REQUEST_ALREADY_RESOLVED` en `A/i18n/`

**Checkpoint**: US3 funcional; el administrador resuelve una solicitud en menos de 30 segundos (SC-003) comprobable en la prueba manual.

---

## Phase 7: User Story 5 - Reservas periódicas y ligadas a eventos (Priority: P2)

**Goal**: serie semanal con fecha fin obligatoria (≤ 1 año, ≤ 53 ocurrencias), todo o nada ante choques, y alta de evento con salón que crea su solicitud en la misma operación.

**Independent Test**: una serie semanal de 8 semanas con una ocurrencia chocando se rechaza y lista esa fecha; sin choque se registran 8 ocurrencias. Un evento en un salón ocupado no guarda ni evento ni solicitud. La cancelación de ocurrencias y de la serie (escenarios 3, 4 y 6 de la historia) se entrega en la fase de US4.

**Cubre**: FR-014, FR-015, FR-020, FR-021, FR-012 (alta de evento).

### Tests de dominio

- [x] T069 [P] [US5] Tests de `WeeklyRecurrence` en `BT/spaces/domain/WeeklyRecurrenceTest.java`: `until` obligatoria, `until >= firstDate` (`RECURRENCE_UNTIL_BEFORE_DATE`), `until <= firstDate.plusYears(1)` (`RECURRENCE_TOO_LONG`), `dates(firstDate)` cada 7 días y exactamente un año da 53 fechas (FR-015; data-model.md `WeeklyRecurrence.until`, `BookingRequest.recurrence`)
- [x] T070 [P] [US5] Tests de expansión de series en `BT/spaces/domain/BookingRequestSeriesTest.java`: 8 semanas dan 8 ocurrencias `PENDING` con la misma franja, `lastDate` es la última, la solicitud simple sigue siendo una serie de una (FR-015; data-model.md `BookingRequest.recurrence`, `BookingRequest.lastDate`, `BookingRequest.firstDate`)
- [x] T071 [P] [US5] Tests de `EventTitle`, `EventType` y `Event` en `BT/agenda/domain/EventTest.java`: título de 1 a 120, tipos `WORD` y `CONVIVENCE`, `START_NOT_IN_FUTURE` y `END_NOT_AFTER_START` con la franja propia de `agenda` (FR-020; data-model.md `Event.title`, `Event.type`, `Event.date`, `Event.slot`)
- [x] T072 [P] [US5] Tests de `EventPolicy.actingCommunity` en `BT/agenda/domain/EventPolicyTest.java`: misma tabla que `BookingPolicy` (moderador con la suya, otra comunidad `AgendaForbidden`, administrador sin comunidad `COMMUNITY_REQUIRED`, `REGISTERED` prohibido) (FR-020)

### Implementación de dominio

- [x] T073 [US5] `WeeklyRecurrence`, `BookingRequest.request` con recurrencia opcional y evento opcional (`eventId`) en `B/spaces/domain/` (data-model.md `WeeklyRecurrence.until`, `BookingRequest.recurrence`, `BookingRequest.bookings`, `BookingRequest.eventId`)
- [x] T074 [US5] `Event`, `EventType`, `EventTitle`, `TimeSlot` y `EventPolicy` en `B/agenda/domain/` (data-model.md `Event.communityId`, `Event.title`, `Event.type`, `Event.date`, `Event.slot`, `Event.roomId`, `Event.bookingRequestId`, `Event.createdBy`)

### Tests de aplicación

- [x] T075 [P] [US5] Tests de series en `BT/spaces/application/RequestBookingSeriesTest.java`: todo o nada, si alguna ocurrencia choca se rechaza la solicitud entera con todas las fechas en conflicto y nada guardado (FR-015)
- [x] T076 [P] [US5] Tests de `RequestForEventService` y `bookingRequestStatus` en `BT/spaces/application/SpacesApiTest.java`: `Booked(bookingRequestId)`, `Occupied(conflicts)`, `RoomUnavailable(NOT_FOUND | INACTIVE)`, el salón se bloquea con `findByIdForShare` como en `RequestBookingService` (R10), el motivo de la solicitud es el título, la solicitud lleva el `eventId`, estado de una solicitud inexistente vacío (FR-014, FR-021)
- [x] T077 [P] [US5] Tests de `CreateEventService` con `SpacesApi` falso y repositorio en memoria en `BT/agenda/application/CreateEventServiceTest.java`: `Booked` guarda el evento con `bookingRequestId`, `Occupied` da `RoomOccupied` y `RoomUnavailable` da `RoomInactive` o `RoomNotFound` sin guardar nada, política de comunidad, administrador sin comunidad `COMMUNITY_REQUIRED` (FR-014, FR-020)
- [x] T078 [P] [US5] Tests de `GetEventService` en `BT/agenda/application/GetEventServiceTest.java`: moderador solo de su comunidad (`AgendaForbidden`), administrador cualquiera, `bookingStatus` actual también tras rechazar o cancelar la solicitud, solicitud purgada `EventNotFound`, evento inexistente `EventNotFound` (FR-021)

### Implementación de aplicación

- [x] T079 [US5] `SpacesApi`, `EventBookingCommand`, `EventBookingResult` (sellado), `EventBookingConflict` y `BookingRequestState` en la raíz de `B/spaces/` según plan.md, `RequestForEventService` y la expansión de series en `RequestBookingService` en `B/spaces/application/service/`
- [x] T080 [US5] Puertos de entrada `CreateEvent` y `GetEvent`, puerto de salida `EventRepository` (`save`, `findById`) y servicios `CreateEventService` (transaccional, usa `SpacesApi.requestForEvent`) y `GetEventService` en `B/agenda/application/`

### Tests de adaptadores

- [x] T081 [P] [US5] Tests Testcontainers de series en `BT/spaces/infrastructure/SeriesPersistenceIT.java`: 8 semanas sin choques guardan 8 ocurrencias, una que choca no guarda ninguna, `until` justo a un año guarda 53 (FR-015; data-model.md `WeeklyRecurrence.until`, `BookingRequest.recurrence`)
- [x] T082 [P] [US5] Tests Testcontainers de atomicidad en `BT/agenda/infrastructure/EventCreationAtomicityIT.java` con `SpacesApi` real: salón libre guarda evento y solicitud `PENDING` con su `eventId`; salón ocupado o dado de baja no deja ni evento ni solicitud en base de datos (FR-014, FR-020; data-model.md `Event.bookingRequestId`)
- [x] T083 [P] [US5] Tests MockMvc de series en `BT/spaces/infrastructure/BookingSeriesControllerTest.java`: serie de 8 semanas `201` con `occurrenceCount = 8`, una ocurrencia que choca `409` con esa fecha, sin `until` `422 REQUIRED`, `until` a más de un año `422 RECURRENCE_TOO_LONG`, `until` anterior `422 RECURRENCE_UNTIL_BEFORE_DATE`, `frequency` distinta de `WEEKLY` `422 INVALID_FORMAT` (FR-015; data-model.md `WeeklyRecurrence.until`)
- [x] T084 [P] [US5] Tests MockMvc de `EventsController` en `BT/agenda/infrastructure/EventsControllerTest.java`: `POST /api/events` `201` como moderador A y como administrador con `communityId=B`, administrador sin `communityId` `422 COMMUNITY_REQUIRED`, moderador A con `communityId=B` `403`, tipo no admitido `422 INVALID_FORMAT`, título de 121 `422 TOO_LONG`, salón ocupado `409 ROOM_OCCUPIED` con `conflicts`, salón dado de baja `409 ROOM_INACTIVE`, salón inexistente `404`; `GET /api/events/{id}` `200` con `bookingStatus`, de otra comunidad `403`, inexistente `404` (FR-014, FR-020, FR-021; data-model.md `Event.title`, `Event.type`)

### Implementación de adaptadores

- [x] T085 [US5] Soporte de series en `BookingRequestRepository` y sus mappers (`recurrence_until`, 1 a 53 ocurrencias) en `B/spaces/infrastructure/out/persistence/` (data-model.md `BookingRequest.recurrence`, `BookingRequest.lastDate`)
- [x] T086 [US5] Entidad JPA `EventEntity`, repositorio, adaptador `EventRepository`, `EventsController` implementando `EventsApi` con mappers y cableado de `CreateEventService` y `GetEventService` en `B/agenda/infrastructure/` (data-model.md `Event.title`, `Event.type`, `Event.date`, `Event.slot`, `Event.roomId`, `Event.bookingRequestId`, `Event.createdBy`)

### Cliente

- [x] T087 [P] [US5] Tests de componente en `A/screens/bookings/__tests__/RecurrenceFields.test.tsx` y `A/screens/events/__tests__/CreateEventScreen.test.tsx`: controles de repetición semanal con fecha fin obligatoria, roles y etiquetas, botones ≥ 48 px y textos ≥ 18 px, el alta de evento muestra el estado de su solicitud y el conflicto con sus fechas; y mapeo en `A/lib/api/__tests__/events-api.test.ts` (FR-012, FR-015, FR-020, FR-021)
- [x] T088 [US5] Controles de repetición semanal en el formulario de solicitar, `A/lib/api/events-api.ts`, hooks en `A/hooks/events/`, pantallas de alta y consulta de evento en `A/screens/events/`, rutas en `A/app/(app)/eventos/` y mensajes de `RECURRENCE_TOO_LONG`, `RECURRENCE_UNTIL_BEFORE_DATE`, `REQUIRED` y `INVALID_FORMAT` en `A/i18n/`

**Checkpoint**: US5 funcional en series y eventos; la cancelación llega con US4.

---

## Phase 8: User Story 4 - Consultar disponibilidad y cancelar (Priority: P3)

**Goal**: cancelar una solicitud, una ocurrencia o una serie entera con confirmación y solo mientras la franja no haya empezado; la ocupación por día ya llega con US2.

**Independent Test**: un moderador cancela una reserva aprobada de su comunidad tras confirmar y la franja vuelve a quedar libre; una de otra comunidad o ya empezada se impide.

**Cubre**: FR-004 (cancelar), FR-007, FR-010 (cancelación), FR-012 (confirmación), FR-013 (cancelación), FR-016.

### Tests de dominio

- [x] T089 [P] [US4] Tests de `cancel` y `cancelBooking` en `BT/spaces/domain/BookingRequestCancellationTest.java`: moderador de la comunidad y administrador pueden, otro moderador y `REGISTERED` `SpacesForbidden`; `PENDING` y `APPROVED` a `CANCELLED`; `cancel` cancela las activas no empezadas y deja las pasadas y empezadas, sin ninguna no empezada `BookingAlreadyStarted`; `cancelBooking` solo esa ocurrencia, empezada `BookingAlreadyStarted`, la solicitud pasa a `CANCELLED` si no queda ninguna activa; `REJECTED` o `CANCELLED` `BookingRequestNotActive`; cada cambio añade `StatusChange` con `bookingId` en la ocurrencia suelta (FR-004, FR-007, FR-010, FR-013, FR-016; data-model.md `BookingRequest.status`, `Booking.status`, `Booking.date`)

### Implementación de dominio

- [x] T090 [US4] `BookingRequest.cancel`, `BookingRequest.cancelBooking` y `Booking.hasStarted(now)` en `B/spaces/domain/` (data-model.md `BookingRequest.status`, `Booking.status`)

### Tests de aplicación

- [x] T091 [P] [US4] Tests de `CancelBookingRequestService` y `CancelBookingService` con fakes y reloj fijo en `BT/spaces/application/CancelServicesTest.java`: cancelar una reserva aprobada de la propia comunidad, una ajena `SpacesForbidden`, administrador cualquiera, franja empezada `BookingAlreadyStarted`, inexistente `BookingRequestNotFound`, la franja queda libre para una nueva solicitud (FR-007, FR-013, FR-016)

### Implementación de aplicación

- [x] T092 [US4] Puertos de entrada `CancelBookingRequest` y `CancelBooking` y servicios transaccionales en `B/spaces/application/`

### Tests de adaptadores

- [x] T093 [P] [US4] Tests Testcontainers en `BT/spaces/infrastructure/CancellationIT.java`: cancelar una serie deja las futuras `CANCELLED` y las pasadas y empezadas intactas, cancelar una ocurrencia no afecta a las demás, la franja cancelada admite una solicitud nueva (FR-013, FR-016; data-model.md `Booking.status`)
- [x] T094 [P] [US4] Tests MockMvc en `BT/spaces/infrastructure/CancellationControllerTest.java`: `POST .../cancellation` `200` de moderador A sobre una de A y de administrador sobre una de B, moderador A sobre una de B `403`, franja empezada `409 BOOKING_ALREADY_STARTED`, serie sin ocurrencias futuras `409 BOOKING_ALREADY_STARTED`, cancelar una `REJECTED` `409 BOOKING_REQUEST_NOT_ACTIVE`, `POST .../bookings/{bookingId}/cancellation` `200` con el detalle y su historial con `bookingId`, ocurrencia inexistente `404` (FR-004, FR-007, FR-010, FR-016)

### Implementación de adaptadores

- [x] T095 [US4] `cancelBookingRequest` y `cancelBooking` en `BookingRequestsController` y persistencia de los cambios de estado de ocurrencias en `B/spaces/infrastructure/` (data-model.md `Booking.status`, `StatusChange.changedBy`)

### Cliente

- [x] T096 [P] [US4] Tests de componente en `A/screens/bookings/__tests__/CancelConfirmation.test.tsx`: el diálogo de confirmación es accesible (rol `alertdialog`, foco y etiquetas), cancelar exige confirmar, una franja empezada no ofrece cancelar, se puede elegir una ocurrencia o la serie (FR-007, FR-012, FR-016)
- [x] T097 [US4] Mutaciones de cancelar en `A/hooks/bookings/`, componente de confirmación y acciones de cancelar ocurrencia o serie en `A/screens/bookings/`, y mensajes de `BOOKING_ALREADY_STARTED` y `BOOKING_REQUEST_NOT_ACTIVE` en `A/i18n/`

**Checkpoint**: US4 funcional; con ella las historias 1 a 6 están completas, incluidos los escenarios de cancelación de US5.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: RGPD (anonimización y purga), privacidad en logs, accesibilidad manual y gates.

### Tests (escribir primero, ver fallar)

- [x] T098 [P] Tests de `AnonymizeRequesterService` y `AnonymizeCreatorService` con fakes en `BT/spaces/application/AnonymizeRequesterServiceTest.java` y `BT/agenda/application/AnonymizeCreatorServiceTest.java`, y de `UserDeletedListener` de cada módulo en `BT/{spaces,agenda}/infrastructure/UserDeletedListenerTest.java`: `@EventListener` síncrono que llama al caso de uso (FR-022)
- [x] T099 [P] Test Testcontainers en `BT/spaces/infrastructure/AnonymizationIT.java` y `BT/agenda/infrastructure/EventAnonymizationIT.java`: al publicar `UserDeleted` sus `requested_by`, `changed_by` y `created_by` quedan a `NULL`, la reserva sigue y `changedBy` sale ausente; si la anonimización falla, el borrado del usuario se deshace en la misma transacción (FR-022; data-model.md `BookingRequest.requestedBy`, `StatusChange.changedBy`, `Event.createdBy`)
- [x] T100 [P] Tests de `PurgeExpiredBookingsService`, `PurgeExpiredEventsService`, `BookingPurgeJob` y `EventPurgeJob` en `BT/spaces/infrastructure/PurgeIT.java`, `BT/agenda/infrastructure/EventPurgeIT.java`, `BT/spaces/infrastructure/config/BookingPurgeJobTest.java` y `BT/agenda/infrastructure/config/EventPurgeJobTest.java`: solicitud con `last_date` hace un año y un día se borra con ocurrencias e historial, con `last_date` hace un año justo se conserva, evento con más de un año se borra, jobs diarios programados (FR-022; data-model.md `BookingRequest.lastDate`, `Event.date`)
- [x] T101 [P] Test de logs en `BT/spaces/infrastructure/BookingLogsTest.java`: con el logger capturado, crear, rechazar y cancelar con un motivo centinela no emite el motivo, nombres ni `accessId`; solo ids y códigos (FR-011; data-model.md `BookingRequest.reason`)
- [x] T102 [P] Test de rendimiento en `BT/spaces/infrastructure/SeriesPerformanceIT.java`: alta de una serie de 53 ocurrencias por debajo de 500 ms p95 (R4)

### Implementation

- [x] T103 [P] Puertos de entrada `AnonymizeRequester` y `PurgeExpiredBookings`, `BookingRequestRepository.anonymize(userId)` y `deleteWithLastDateBefore(date)`, servicios, `UserDeletedListener` en `B/spaces/infrastructure/in/events/` y `BookingPurgeJob` en `B/spaces/infrastructure/config/` (FR-022; data-model.md `BookingRequest.requestedBy`, `BookingRequest.lastDate`, `StatusChange.changedBy`)
- [x] T104 [P] Puertos de entrada `AnonymizeCreator` y `PurgeExpiredEvents`, `EventRepository.anonymize(userId)` y `deleteWithDateBefore(date)`, servicios, `UserDeletedListener` en `B/agenda/infrastructure/in/events/` y `EventPurgeJob` en `B/agenda/infrastructure/config/` (FR-022; data-model.md `Event.createdBy`, `Event.date`)
- [ ] T105 Revisión manual de accesibilidad (FR-012) según quickstart.md § 4 en las seis pantallas: contraste AA en claro y oscuro, escalado de fuente al 200 % sin cortes, movimiento reducido, etiquetas, objetivos ≥ 48 px y texto ≥ 18 px
- [ ] T106 Ejecutar la prueba manual contra la API (quickstart.md § 3, pasos 0 a 13) y en el cliente (quickstart.md § 4), y repasar uno a uno FR-001 a FR-023 y SC-001 a SC-005
- [x] T107 Gates finales: `cd backend && ./mvnw -B verify` (con `TESTCONTAINERS_RYUK_DISABLED=true` si hace falta), `ArchitectureTest` y `ModularityTest` verdes, `cd app && npm run lint && npm run typecheck && npx expo export -p web && npm test`, `npm run api:check` y Redocly sobre `api/openapi.yaml`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias; la fusión del contrato va primero. T108 (mover el `Clock`) va antes de T013 y T020 aunque su ID sea el último.
- **Foundational (Phase 2)**: depende de Setup y bloquea todas las historias.
- **User Stories (Phase 3+)**: dependen de Foundational. Orden de entrega: US6, US1, US2 (P1), US3, US5 (P2), US4 (P3).
- **Polish (Phase 9)**: depende de las historias deseadas; anonimización y purga necesitan US1 y US5 (solicitudes y eventos).

### User Story Dependencies

- **US6**: solo Foundational; es requisito de todas las demás porque crea los salones.
- **US1**: US6.
- **US2**: US1 (amplía la solicitud con comprobación de conflictos y añade la ocupación).
- **US3**: US1; probar series aprobadas requiere US5.
- **US5**: US1 y US2; sus escenarios de cancelación (3, 4 y 6) requieren US4.
- **US4**: US1; cancelar series y ocurrencias se prueba con US5.

### Within Each User Story

- Capa a capa: dominio, aplicación, adaptadores, contrato/API, cliente.
- En cada capa el test va antes que la implementación y se ve fallar.
- Modelo antes que servicio, servicio antes que controlador.

### Parallel Opportunities

- En Setup, las generaciones del backend y del cliente y los dos esqueletos de módulo.
- En Foundational, todos los tests y los pares `spaces`/`agenda` de tipos, resolución de actor y configuración.
- Dentro de cada historia, los tests de una misma capa (ficheros distintos).
- `agenda` y `spaces` en US5 tras cerrar sus tipos de dominio, salvo que `CreateEventService` espera a `SpacesApi`.
- Backend y cliente de una historia, una vez fijado el contrato.

---

## Parallel Example: User Story 2

```bash
Task: "Tests de TimeSlot.overlaps y Conflict en BT/spaces/domain/TimeSlotOverlapTest.java"
Task: "Tests en BT/spaces/domain/BookingPolicyOccupancyTest.java de la comunidad efectiva al consultar ocupación"

Task: "Tests Testcontainers de solape en BT/spaces/infrastructure/BookingOverlapIT.java"
Task: "Test de concurrencia en BT/spaces/infrastructure/BookingConcurrencyIT.java"
Task: "Tests MockMvc en BT/spaces/infrastructure/RoomOccupancyControllerTest.java"
```

---

## Implementation Strategy

### MVP First (US6 + US1 + US2)

1. Phase 1 Setup y Phase 2 Foundational.
2. Phase 3 US6 (salones), Phase 4 US1 (solicitar) y Phase 5 US2 (sin solapes): las tres son P1 y juntas sustituyen el reparto por WhatsApp.
3. **STOP y validar**: quickstart.md § 3 pasos 0 a 5 y 8, `./mvnw -B verify`.
4. Demo con un moderador y un administrador.

### Incremental Delivery

1. Setup + Foundational lista.
2. US6, US1 y US2: MVP.
3. US3: el administrador resuelve solicitudes.
4. US5: series y eventos.
5. US4: cancelación con confirmación.
6. Polish: RGPD, logs, accesibilidad manual y gates.

### Parallel Team Strategy

1. Un equipo cierra Setup + Foundational.
2. Con US6 cerrada: backend y cliente de US1 en paralelo, y `agenda` (US5) en paralelo con US3 desde que existe `SpacesApi`.

---

## Notes

- [P] = ficheros distintos, sin dependencias.
- [Story] enlaza la tarea con su historia.
- Verificar que cada test falla antes de implementar.
- Cada historia es entregable por separado salvo las dependencias indicadas.
- Nada generado se edita a mano; `backend/pom.xml` no cambia.
