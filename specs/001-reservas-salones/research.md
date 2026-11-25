# Research: Reservas de salones

**Feature**: `001-reservas-salones` | **Fecha**: 2026-11-24 | **Plan**: [plan.md](./plan.md)

Fase 0 del plan. Cada apartado resuelve una incógnita del contexto técnico con decisión, motivo y alternativas.

## R1. Bounded context y módulo Modulith

- **Decisión**: un único módulo `spaces` (paquete `io.github.escumartin.neocatecumenal.spaces`) para el bounded context
  «Espacios y reservas» de `dominio.md`. Contiene los salones (`Room`, con su gestión por el administrador) y las
  solicitudes de reserva. API pública en la raíz del módulo: `SpacesApi` y sus tipos de entrada y salida.
- **Motivo**: `dominio.md` agrupa lugares y reservas en el mismo contexto. La regla de solape depende del salón, y la
  baja de un salón con reservas futuras («debe resolver antes esas reservas») se resuelve dentro del mismo módulo sin
  llamadas cruzadas.
- **Alternativas descartadas**:
  - Módulo `bookings` separado de `rooms`: exige una API entre ambos para algo que es una sola consistencia (salón y
    ocupación) y duplica el concepto de lugar.
  - Reservas dentro de Agenda de eventos: hay reservas sin evento (US1) y el dominio las separa.

## R2. Relación con Agenda de eventos (FR-014, FR-020)

- **Decisión**: dependencia en un solo sentido, `agenda → spaces`, síncrona.
  - Agenda llama a `SpacesApi.requestForEvent(...)` dentro de su transacción de alta de evento. El resultado es
    `Booked(bookingRequestId)`, `Occupied(conflicts)` o `RoomUnavailable` (no existe o dado de baja). En los dos últimos,
    Agenda lanza su propia excepción y la transacción entera se deshace: ni evento ni solicitud.
  - `spaces` guarda `eventId` como identificador opaco (`EventId`, UUID) y no conoce el modelo de Agenda.
  - `spaces` no publica eventos de dominio. El estado de la solicitud se lee al consultar el evento con
    `SpacesApi.bookingRequestStatus(id)`.
- **Motivo**: «si el salón está ocupado, el evento no se guarda» exige respuesta síncrona; un evento de dominio
  asíncrono no puede impedir el alta. El sentido único evita ciclos (`ApplicationModules.verify()`). Publicar eventos que
  nadie consume añade código sin requisito: la spec solo pide mostrar el estado de la solicitud al consultar (FR-021).
- **Alternativas descartadas**:
  - Agenda publica `EventCreated` y `spaces` reacciona: asíncrono, no puede impedir el alta y crea `spaces → agenda`.
  - `spaces` publica `BookingRequestApproved/Rejected/Cancelled` para que Agenda los guarde: sin consumidor en esta
    feature; se añadirán con la Agenda completa si hace falta.
  - Reserva creada por el cliente antes del evento (dos llamadas HTTP): rompe la atomicidad y deja reservas huérfanas.

## R3. Modelo de solicitud y serie (FR-005, FR-009, FR-015, FR-016)

- **Decisión**: agregado `BookingRequest` (la solicitud, simple o periódica) con 1..n entidades hijas `Booking` (las
  ocurrencias). Una solicitud simple es una serie de una ocurrencia.
  - El estado de la solicitud (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`) se aprueba o rechaza como un todo.
  - Cada `Booking` lleva su propio estado, que se usa para la restricción de solape y para cancelar ocurrencias sueltas.
  - Regla de repetición `WeeklyRecurrence(until)`: solo semanal, fecha fin obligatoria, `until >= firstDate`,
    `until <= firstDate + 1 año`. Se expande en el dominio a fechas concretas (máximo 53 ocurrencias).
- **Motivo**: un solo modelo para simple y serie; el agregado es pequeño (≤ 53 hijos) y la consistencia de aprobar o
  rechazar la serie entera queda dentro de un agregado.
- **Alternativas descartadas**:
  - Guardar la regla y calcular ocurrencias al vuelo: impide la restricción de exclusión y cancelar ocurrencias sueltas.
  - `Booking` como agregado y `Series` como agrupador: aprobar la serie tocaría varios agregados en una transacción.
  - RRULE (RFC 5545) completa: v1 solo necesita semanal con fecha fin; se puede añadir después sin romper el modelo.

## R4. Sin solapes con concurrencia en PostgreSQL (FR-003, FR-013, SC-002)

- **Decisión**: restricción de exclusión GiST en la tabla `booking`, como garantía final, más comprobación previa en la
  aplicación para informar de las fechas en conflicto.
  - Extensión `btree_gist` (es *trusted* desde PostgreSQL 13: la crea el propietario de la base sin superusuario).
  - Columna generada `period tsrange GENERATED ALWAYS AS (tsrange(booking_date + start_time, booking_date + end_time, '[)')) STORED`.
  - `EXCLUDE USING gist (room_id WITH =, period WITH &&) WHERE (status IN ('PENDING', 'APPROVED'))`.
  - Intervalo semiabierto `[)`: las franjas contiguas no se solapan; inicio dentro, fin dentro y envolvente sí.
  - Hora local sin zona (`timestamp` sin `tz`): zona única `Europe/Madrid`.
  - Flujo de alta: (1) el dominio valida y expande; (2) consulta de ocupación de las fechas implicadas y, si hay choque,
    `RoomOccupied` con las fechas; (3) inserción de todas las ocurrencias con `saveAndFlush` en el adaptador; (4) si otra
    transacción ganó la carrera, PostgreSQL lanza `23P01`, el adaptador lo traduce a `RoomOccupied` sin fechas (la
    transacción ya está abortada y no admite otra consulta) y la transacción se deshace entera. El contrato admite
    `conflicts` vacío en ese caso; el cliente vuelve a pedir la ocupación.
  - Rechazar o cancelar cambia `status` y libera la franja al instante, porque la restricción es parcial.
  - Aprobar una serie pendiente no puede chocar: sus ocurrencias ya ocupan la franja desde el alta. La regla de FR-005
    («si alguna ocurrencia solapa, la serie no se puede aprobar») se cumple por construcción y se prueba como invariante
    de persistencia.
- **Motivo**: la restricción es atómica y no depende del nivel de aislamiento; cubre «gana quien solicita antes» y la
  serie todo o nada sin bloqueos explícitos.
- **Alternativas descartadas**:
  - Solo comprobación en la aplicación: con `READ COMMITTED` dos transacciones concurrentes pasan ambas.
  - `SERIALIZABLE`: reintentos y falsos positivos; más complejo para el mismo resultado.
  - Recalcular las fechas en conflicto tras `23P01` en una transacción nueva (`REQUIRES_NEW`): más código para un caso
    de carrera raro.
  - Tramos fijos con `UNIQUE`: la spec fija franjas libres.

## R5. Resolución concurrente (dos administradores)

- **Decisión**: bloqueo optimista con `version` en `booking_request` (`@Version` en la entidad JPA) y transición
  validada en el dominio (`approve`/`reject` solo desde `PENDING`). La segunda resolución recibe `409` con código
  `BOOKING_REQUEST_ALREADY_RESOLVED`, tanto si el dominio ve el estado ya resuelto como si salta
  `OptimisticLockingFailureException` (mapeo en R13).
- **Alternativas descartadas**: bloqueo pesimista (innecesario a esta escala); `If-Match`/ETag en la API (se puede añadir
  después sin cambiar el modelo).

## R6. Identidad y autorización (FR-001, FR-006, FR-008, FR-020)

- **Decisión**: el adaptador REST de cada módulo (`ActorResolver` en `infrastructure/in/rest`) llama a
  `IdentityApi.currentUser()` (API pública de `identity`) y construye el value object `Actor(userId, role,
  ownCommunityId)` propio del módulo, que entra en cada comando junto con la comunidad elegida (`communityId` del cuerpo
  o de la consulta). Sin `CurrentUser` (no autenticado o con pasos pendientes) responde `401`; los filtros de identidad
  ya cortan antes en casi todos los casos.
  - La autorización por rol y comunidad vive en el dominio (`BookingPolicy`, `RoomPolicy`, `EventPolicy`):
    - `REGISTERED` → `403` en todas las operaciones de esta feature.
    - Moderador: comunidad efectiva = la suya; si envía otra → `403`.
    - Administrador: `communityId` obligatorio al solicitar, consultar ocupación y dar de alta un evento → `422
      COMMUNITY_REQUIRED` si falta; si no existe (`IdentityApi.community(id)` vacío, comprobado en el adaptador) → `422
      COMMUNITY_NOT_FOUND`.
  - El nombre y el `accessId` de `CurrentUser` no se copian al `Actor` (minimización).
- **Motivo**: las reglas de rol y comunidad son de negocio y se prueban sin Spring; reutiliza la API pública existente
  sin crear puertos nuevos; `application` no depende de `identity` ni de Spring Security.
- **Alternativas descartadas**:
  - Puerto `CurrentActorProvider` implementado por identidad: un puerto más que solo envuelve `IdentityApi`.
  - Leer el `SecurityContext` en los servicios: acopla `application` a Spring Security.
- Rol evaluado en el momento de la acción: quien deja de ser moderador conserva su historial (`requested_by`) pero no
  puede crear ni cancelar.

## R7. Contrato OpenAPI y generación

- **Decisión**: borrador en `contracts/room-bookings.openapi.yaml` (OpenAPI 3.1) que se fusiona en `api/openapi.yaml`
  con la regla de fusión de [plan.md](./plan.md) § Regla de fusión del contrato, como primer paso de la implementación.
  - Backend: el `openapi-generator-maven-plugin` existente (generador `spring`, `interfaceOnly=true`,
    `useSpringBoot4=true`, `useTags=true`, `apiPackage` `io.github.escumartin.neocatecumenal.generated.api`,
    `modelPackage` `io.github.escumartin.neocatecumenal.generated.model`) genera `RoomsApi`, `BookingRequestsApi` y
    `EventsApi`; los controladores de `spaces` y `agenda` las implementan. Sin cambios en el pom.
  - Cliente: `npm run api:generate` regenera `app/src/api/schema.d.ts`; llamadas con el `openapi-fetch` de
    `app/src/lib/api/client.ts`.
  - Errores con Problem Details (`application/problem+json`) y un `code` estable para que el cliente traduzca.
  - Acciones de estado como subrecursos `POST .../approval`, `.../rejection`, `.../cancellation`; rectificar el motivo,
    `PUT .../reason`.
- **Alternativas descartadas**: `PATCH` con `status` (mezcla permisos y transiciones en un endpoint); generación del
  contrato desde el código (contradice el principio V); redefinir `Problem`, `NotFound` o `StateConflict` en el
  borrador (rompería el contrato de identidad).

## R8. Privacidad (FR-008, FR-011, FR-023, principio VIII)

- **Decisión**: `booking_request` guarda solo salón, comunidad, solicitante (`requested_by`, id opaco), evento opcional,
  motivo, estado y motivo de rechazo; nada de nombres, teléfonos ni correos. La consulta de ocupación devuelve solo
  franjas y un indicador de si es de la comunidad efectiva del actor. Los logs registran ids y códigos de error, nunca
  motivos ni datos de personas.
  - Motivo y motivo de rechazo: máximo 500 caracteres; el formulario avisa de no incluir datos personales.
  - Rectificación: el moderador (y el administrador) corrige el motivo mientras la solicitud está `PENDING`
    (`PUT /api/booking-requests/{id}/reason`). Acceso: el moderador ve sus solicitudes con motivo e historial.

## R9. Fechas y validaciones de tiempo (FR-002, FR-004, FR-007, FR-016)

- **Decisión**: el único bean `java.time.Clock` (`Clock.systemUTC()`) se mueve de `identity.infrastructure.config.IdentityConfig`
  a `ClockConfig` en el paquete raíz de la aplicación, fuera de los módulos; `identity`, `spaces` y `agenda` lo inyectan y
  ninguno declara otro, porque un segundo bean `Clock` rompe la inyección por tipo. El cableado de cada módulo pasa a sus
  servicios `clock.withZone(ZoneId.of("Europe/Madrid"))`
  y el servicio pasa `now` (`LocalDateTime` local) al dominio.
  - Alta: `startTime` de la primera ocurrencia posterior a `now` (`START_NOT_IN_FUTURE`); franja en minutos (`HH:mm`),
    `end > start`, mismo día (no hay `24:00`).
  - Aprobar: la primera ocurrencia de la solicitud o serie debe tener `date + startTime > now`; si no,
    `422 START_NOT_IN_FUTURE` y solo cabe rechazar.
  - Cancelar: solo ocurrencias cuya franja no ha empezado (`date + startTime > now`). Una ocurrencia empezada → `409
    BOOKING_ALREADY_STARTED`. Cancelar la serie cancela las ocurrencias activas no empezadas y deja tal cual las pasadas
    y las empezadas; si no queda ninguna por cancelar → `409 BOOKING_ALREADY_STARTED`.

## R10. Baja de salones (FR-017, FR-018, FR-019)

- **Decisión**: la baja es siempre lógica y definitiva (`active = false`, sin reactivación). Se bloquea con `409
  ROOM_HAS_ACTIVE_BOOKINGS` (con el número de reservas) mientras el salón tenga ocurrencias `PENDING` o `APPROVED` con
  franja futura (`date + startTime > now`); las pendientes cuya franja ya pasó no cuentan. Una pendiente o serie cuya primera ocurrencia ya empezó no se aprueba (`422 START_NOT_IN_FUTURE`) y solo se rechaza; no hay estado de caducidad. El nombre es único entre salones activos (índice único parcial `WHERE active`).
  - Un salón dado de baja: no admite solicitudes (`409 ROOM_INACTIVE`), no sale en `GET /api/rooms` y su ocupación da
    `404`.
- **Motivo**: es la regla más simple y segura. Las reservas pasadas conservan la FK al salón; la spec ya exige resolver
  antes las reservas futuras.
- **Carrera baja contra solicitud**: la baja toma `SELECT ... FOR UPDATE` sobre la fila del salón y el alta de
  solicitudes toma `FOR SHARE` sobre la misma fila antes de insertar.
- **Alternativas descartadas**:
  - Borrado físico si no hay reservas y lógico si las hay: dos comportamientos para la misma acción.
  - Cancelar en cascada las reservas futuras al dar de baja: deja a comunidades sin salón sin que nadie lo decida.

## R11. Identificadores

- **Decisión**: UUID como clave interna de todas las entidades (`room`, `booking_request`, `booking`, `event`). Los
  módulos referencian usuarios y comunidades solo por su UUID. El número de comunidad y el id numérico de acceso
  pertenecen a identidad; esta feature no los guarda.
- **Motivo**: las claves internas no dependen de reglas de negocio que pueden cambiar y no revelan información en las
  URL.

## R12. Agenda mínima (FR-014, FR-020, FR-021)

- **Decisión**: módulo `agenda` con el agregado `Event` y los casos de uso `CreateEvent` y `GetEvent`, más
  `AnonymizeCreator` y `PurgeExpiredEvents` por RGPD (R14).
  - Tipos admitidos: `WORD` (Palabra) y `CONVIVENCE` (Convivencia de un día). Campos: comunidad, tipo, título
    (1..120), fecha, franja, salón, `bookingRequestId` y creador.
  - `CreateEventService` es transaccional: valida actor y evento, genera el `eventId`, llama a
    `SpacesApi.requestForEvent` (el motivo de la solicitud es el título del evento) y guarda el evento. `Occupied` →
    `RoomOccupied` (`409 ROOM_OCCUPIED`); `RoomUnavailable` → `409 ROOM_INACTIVE` (o `404` si no existe); en ambos casos
    rollback.
  - `GetEvent`: moderador solo de su comunidad (`403` si es de otra), administrador cualquiera; devuelve el evento y el
    estado de su solicitud.
- **Fuera de alcance**: edición y borrado de eventos, listados, consulta por los registrados, otros tipos, eventos
  periódicos, lecturas, cuerpo, precio y reglas de día de la semana por tipo.
- **Alternativas descartadas**: guardar el evento dentro de `spaces` (mezcla contextos); crear primero la solicitud
  desde el cliente (rompe la atomicidad).

## R13. Errores HTTP por módulo

- **Decisión**: cada módulo tiene un `@RestControllerAdvice(basePackageClasses = <un controlador de su in/rest>)` con
  `@Order(Ordered.HIGHEST_PRECEDENCE)` (`SpacesProblemHandler`, `AgendaProblemHandler`) que solo declara manejadores de
  sus excepciones de dominio y de aplicación, más `OptimisticLockingFailureException` en `spaces`:
  - `spaces`: `RoomOccupied` → `409 ROOM_OCCUPIED` (+ `conflicts`); `BookingRequestNotPending` y
    `OptimisticLockingFailureException` → `409 BOOKING_REQUEST_ALREADY_RESOLVED`; `BookingRequestNotActive` → `409
    BOOKING_REQUEST_NOT_ACTIVE`; `BookingAlreadyStarted` → `409 BOOKING_ALREADY_STARTED`; `RoomNameTaken` → `409
    ROOM_NAME_TAKEN`; `RoomInactive` → `409 ROOM_INACTIVE`; `RoomHasActiveBookings` → `409 ROOM_HAS_ACTIVE_BOOKINGS`
    (+ `activeBookings`); `*NotFound` → `404 NOT_FOUND`; `SpacesForbidden` → `403 FORBIDDEN`; `SpacesFieldInvalid` →
    `422 VALIDATION_FAILED` con `errors`.
  - `agenda`: `RoomOccupied` → `409 ROOM_OCCUPIED` (+ `conflicts`); `RoomInactive` → `409 ROOM_INACTIVE`; `RoomNotFound` y `EventNotFound` → `404`;
    `AgendaForbidden` → `403`; `AgendaFieldInvalid` → `422`.
  - Sin manejadores genéricos (`Exception`, `RuntimeException`): validación de entrada, `401`, `403` de seguridad y
    `500` siguen en `ProblemDetailsHandler` de identidad.
  - El cuerpo (`type`, `title`, `status`, `code`) se construye en cada advice; no se usa `ProblemDetailsHandler` porque
    está en `identity.infrastructure` y no es API pública. Log con `code` y `status`, sin datos personales.
- **Motivo**: el global de identidad ya mapea `OptimisticLockingFailureException` a `CONCURRENT_MODIFICATION`; el advice
  del módulo, con más prioridad y limitado a sus controladores, lo sobrescribe solo para reservas sin tocar identidad.
- **Alternativas descartadas**: añadir los casos al handler de identidad (acopla identidad a `spaces` y rompe el
  principio III); exponer un constructor de Problem en `IdentityApi` (mezcla responsabilidades por 15 líneas).

## R14. RGPD: anonimización y retención (FR-022)

- **Decisión**:
  - Base legal: interés legítimo de la parroquia en organizar sus espacios (FR-022).
  - Anonimización: `spaces` y `agenda` escuchan `UserDeleted(userId)` con un `@EventListener` síncrono
    (`infrastructure/in/events`), que corre en la transacción de borrado de identidad y llama a su caso de uso
    (`AnonymizeRequester`, `AnonymizeCreator`). Pone a `NULL` `requested_by`, `changed_by` y `created_by` de ese usuario;
    el contrato devuelve `changedBy` ausente y el cliente muestra «usuario eliminado». La reserva se conserva.
  - Retención: `BookingPurgeJob` (diario, `infrastructure/config`) llama a `PurgeExpiredBookings`, que borra las
    solicitudes con `last_date < hoy - 1 año`, con sus ocurrencias e historial. `EventPurgeJob` borra los eventos con
    `event_date < hoy - 1 año` (su única ocurrencia es la de la fecha del evento, así que caducan a la vez que su
    solicitud).
- **Motivo**: sin registro de publicación de eventos (no hay `spring-modulith-events-*` en el pom), un
  `@ApplicationModuleListener` asíncrono podría perder la anonimización si falla; el síncrono hace el borrado y la
  anonimización todo o nada sin dependencias nuevas. `NULL` evita inventar un UUID centinela.
- **Alternativas descartadas**:
  - `@ApplicationModuleListener` con `spring-modulith-starter-jpa`: dependencia y tabla nuevas para un único consumidor.
  - UUID fijo «usuario eliminado»: valor mágico compartido entre módulos.
  - Purga por consulta (`max(booking_date)` al vuelo): sin índice; `last_date` se fija al crear y no cambia.
