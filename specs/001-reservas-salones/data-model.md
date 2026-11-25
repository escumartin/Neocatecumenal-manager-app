# Data Model: Reservas de salones

**Feature**: `001-reservas-salones` | **Fecha**: 2026-11-24 | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

Módulos `spaces` y `agenda` (mínimo). Nombres de dominio en inglés; entre paréntesis, el término del glosario.

Identificadores: UUID como clave interna de todas las entidades. Usuarios y comunidades se referencian solo por su UUID;
el número de comunidad y el id numérico de acceso son atributos únicos de identidad y no se guardan aquí
([research.md](./research.md) R11).

## Dominio (`spaces.domain`, Java puro)

### Value objects

| Tipo | Campos | Reglas |
|---|---|---|
| `RoomId`, `BookingRequestId`, `BookingId` | `UUID value` | no nulos |
| `CommunityId`, `UserId`, `EventId` | `UUID value` | ids de otros contextos, opacos |
| `TimeSlot` (franja) | `LocalTime start`, `LocalTime end` | `end > start`; mismo día; precisión de minutos; `overlaps(other)` semiabierto: `a.start < b.end && b.start < a.end` |
| `WeeklyRecurrence` | `LocalDate until` | obligatoria la fecha fin; `until >= firstDate`; `until <= firstDate.plusYears(1)`; `dates(firstDate)` devuelve `firstDate`, `+7 días`... hasta `until` (≤ 53) |
| `RoomName` | `String value` | 1..80 caracteres, recortado |
| `Reason` (motivo) | `String value` | 1..500 caracteres, recortado (FR-023) |
| `RejectionReason` | `String value` | 1..500 caracteres, opcional (FR-023) |
| `Actor` | `UserId userId`, `ActorRole role`, `CommunityId ownCommunityId` | lo construye el adaptador REST desde `IdentityApi.currentUser()`; sin nombre ni `accessId` |
| `ActorRole` | `REGISTERED`, `MODERATOR`, `ADMINISTRATOR` | `REGISTERED` no opera en esta feature; el administrador puede todo lo del moderador |
| `Conflict` | `LocalDate date`, `TimeSlot slot` | fecha y franja ocupadas que impiden la solicitud |

### Política de comunidad efectiva (`BookingPolicy.actingCommunity(actor, requested)`)

| Actor | `requested` | Resultado |
|---|---|---|
| `REGISTERED` | cualquiera | `SpacesForbidden` (`403`) |
| `MODERATOR` | ausente o la suya | su comunidad |
| `MODERATOR` | otra | `SpacesForbidden` (`403`) |
| `ADMINISTRATOR` | presente | la elegida (su existencia la comprueba el adaptador con `IdentityApi.community`) |
| `ADMINISTRATOR` | ausente | `SpacesFieldInvalid(communityId, COMMUNITY_REQUIRED)` (`422`) |

Se aplica al solicitar (FR-001), consultar ocupación (FR-008) y dar de alta eventos (FR-020, en `agenda` con su propia
política equivalente). En listados (FR-006), el administrador puede omitir `communityId` y ve todas.

### Agregado `BookingRequest` (solicitud; serie si tiene repetición)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `BookingRequestId` | |
| `roomId` | `RoomId` | salón |
| `communityId` | `CommunityId` | propietaria |
| `requestedBy` | `UserId?` | solicitante; nulo tras anonimizar (FR-022) |
| `eventId` | `EventId?` | evento de Agenda, opcional |
| `reason` | `Reason` | rectificable mientras `PENDING` |
| `firstDate` | `LocalDate` | fecha de la primera ocurrencia |
| `lastDate` | `LocalDate` | fecha de la última ocurrencia; base de la purga |
| `slot` | `TimeSlot` | misma franja en todas las ocurrencias |
| `recurrence` | `WeeklyRecurrence?` | nula en solicitudes simples |
| `status` | `BookingRequestStatus` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `rejectionReason` | `RejectionReason?` | solo si `REJECTED` |
| `bookings` | `List<Booking>` | 1..53 ocurrencias |
| `history` | `List<StatusChange>` | historial (FR-010) |

Fábrica `BookingRequest.request(actor, roomId, actingCommunityId, date, slot, reason, recurrence?, eventId?, now)`:

- Primera ocurrencia con `date + slot.start > now` en `Europe/Madrid` (FR-002), si no `START_NOT_IN_FUTURE`.
- El salón existe y está activo (FR-019); el servicio lo bloquea con `FOR SHARE` durante el alta (R10).
- Expande las ocurrencias (`PENDING`) y añade `StatusChange(null → PENDING)`.

Comportamiento (`now` lo pasa el servicio desde el `Clock`):

| Método | Quién | Desde | Hacia | Efecto en ocurrencias |
|---|---|---|---|---|
| `approve(actor)` | administrador | `PENDING` | `APPROVED` | las `PENDING` pasan a `APPROVED` (toda la serie) |
| `reject(actor, reason?)` | administrador | `PENDING` | `REJECTED` | las `PENDING` pasan a `REJECTED` (toda la serie; libera franjas) |
| `cancel(actor, now)` | moderador de la comunidad o administrador | `PENDING`, `APPROVED` | `CANCELLED` | las activas no empezadas pasan a `CANCELLED`; las pasadas y empezadas no cambian; si no hay ninguna no empezada → `BookingAlreadyStarted` |
| `cancelBooking(actor, bookingId, now)` | moderador de la comunidad o administrador | `PENDING`, `APPROVED` | igual; `CANCELLED` si ya no queda ninguna activa | solo esa ocurrencia, si no ha empezado, pasa a `CANCELLED`; si ha empezado → `BookingAlreadyStarted` |
| `rectifyReason(actor, reason)` | moderador de la comunidad o administrador | `PENDING` | `PENDING` | ninguno |

Transición no permitida: `approve`, `reject` o `rectifyReason` fuera de `PENDING` → `BookingRequestNotPending`;
`cancel*` fuera de `PENDING`/`APPROVED` → `BookingRequestNotActive`. Sin permiso → `SpacesForbidden`. Cada cambio de
estado añade un `StatusChange`.

Aprobar no vuelve a comprobar solapes: las ocurrencias `PENDING` ya ocupan su franja por la restricción de exclusión, así
que una serie pendiente nunca solapa con otra reserva activa ([research.md](./research.md) R4).

### Entidad `Booking` (ocurrencia)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `BookingId` | |
| `date` | `LocalDate` | |
| `slot` | `TimeSlot` | copia de la del agregado |
| `status` | `BookingStatus` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`; activa = `PENDING` o `APPROVED` |

`hasStarted(now)`: `date + slot.start <= now`.

### Entidad `StatusChange` (historial)

| Campo | Tipo | Notas |
|---|---|---|
| `bookingId` | `BookingId?` | nulo si el cambio es de toda la solicitud |
| `from` | estado? | nulo en el alta |
| `to` | estado | |
| `changedBy` | `UserId?` | nulo tras anonimizar (FR-022) |
| `changedAt` | `Instant` | |

### Agregado `Room` (salón)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `RoomId` | |
| `name` | `RoomName` | 1..80, recortado; único entre salones activos (sin distinguir mayúsculas) |
| `active` | `boolean` | solo los activos admiten solicitudes y salen en la disponibilidad |

| Método | Quién | Reglas |
|---|---|---|
| `Room.create(actor, name)` | administrador | nombre válido; unicidad comprobada por el servicio y garantizada por índice |
| `rename(actor, name)` | administrador | solo activo (`RoomInactive`) |
| `deactivate(actor, activeFutureBookings)` | administrador | solo activo; `activeFutureBookings == 0`, si no `RoomHasActiveBookings(count)`; definitiva |

Hoteles y restaurantes quedan fuera del modelo.

### Puertos (`spaces.application.port`)

- `in`: `RequestBooking`, `ApproveBookingRequest`, `RejectBookingRequest`, `CancelBookingRequest`, `CancelBooking`,
  `RectifyBookingReason`, `ListBookingRequests`, `GetBookingRequest`, `GetRoomOccupancy`, `ListRooms`, `CreateRoom`,
  `RenameRoom`, `DeactivateRoom`, `AnonymizeRequester`, `PurgeExpiredBookings`.
- `out`:
  - `BookingRequestRepository`: `save`, `findById`, `findConflicts(roomId, dates, slot)`,
    `search(communityId?, statuses, page)`, `anonymize(userId)`, `deleteWithLastDateBefore(date)`.
  - `RoomRepository`: `findById`, `findByIdForUpdate`, `findByIdForShare`, `findAllActive`, `existsActiveByName`,
    `save`.
  - `BookingCounter`: `activeFutureBookings(roomId, now)`.
  - `RoomOccupancyQuery`: `occupiedSlots(roomId, date)`.
- `save` hace flush y traduce `23P01` a `RoomOccupied(conflicts = [])`; el servicio no ve SQL.

### API pública del módulo (`spaces`, raíz)

- `SpacesApi.requestForEvent(EventBookingCommand) → EventBookingResult`, sellado: `Booked(bookingRequestId)`,
  `Occupied(List<EventBookingConflict>)` o `RoomUnavailable(reason: NOT_FOUND | INACTIVE)`.
- `SpacesApi.bookingRequestStatus(UUID bookingRequestId) → Optional<BookingRequestState>`; `BookingRequestState` es un
  enum exportado en la raíz, para no exponer tipos de `domain`.
- `EventBookingCommand(eventId, roomId, communityId, requestedBy, date, startTime, endTime, reason)`. La autorización
  del actor la hace `agenda` antes de llamar; `spaces` confía en la comunidad recibida.
- Sin eventos publicados.

## Persistencia (PostgreSQL, Flyway `V3__spaces.sql`)

```text
room
  id            uuid        PK
  name          varchar(80) not null
  active        boolean     not null default true
  version       bigint      not null
  created_at    timestamptz not null
  índice único parcial: (lower(name)) WHERE active

booking_request
  id               uuid         PK
  room_id          uuid         not null FK room(id)
  community_id     uuid         not null
  requested_by     uuid         null
  event_id         uuid         null, unique
  reason           varchar(500) not null
  first_date       date         not null
  last_date        date         not null, check (last_date >= first_date)
  start_time       time         not null
  end_time         time         not null, check (end_time > start_time)
  recurrence_until date         null
  status           varchar(16)  not null check in (PENDING, APPROVED, REJECTED, CANCELLED)
  rejection_reason varchar(500) null
  version          bigint       not null
  created_at       timestamptz  not null
  índices: (community_id, status), (status, first_date), (last_date), (requested_by)

booking
  id                 uuid        PK
  booking_request_id uuid        not null FK booking_request(id) ON DELETE CASCADE
  room_id            uuid        not null
  booking_date       date        not null
  start_time         time        not null
  end_time           time        not null, check (end_time > start_time)
  status             varchar(16) not null check in (PENDING, APPROVED, REJECTED, CANCELLED)
  period             tsrange     generated always as (tsrange(booking_date + start_time, booking_date + end_time, '[)')) stored
  restricción: EXCLUDE USING gist (room_id WITH =, period WITH &&) WHERE (status IN ('PENDING','APPROVED'))
  índices: (booking_request_id), (room_id, booking_date)

booking_status_change
  id                 bigserial   PK
  booking_request_id uuid        not null FK booking_request(id) ON DELETE CASCADE
  booking_id         uuid        null FK booking(id) ON DELETE CASCADE
  from_status        varchar(16) null
  to_status          varchar(16) not null
  changed_by         uuid        null
  changed_at         timestamptz not null
  índice: (changed_by)
```

- `CREATE EXTENSION IF NOT EXISTS btree_gist` al inicio de la migración.
- `booking.room_id` duplica el de la solicitud para que la restricción sea de una sola tabla; lo fija el adaptador.
- `period` no se mapea en JPA.
- Ninguna tabla se comparte con otros módulos; `community_id`, `requested_by`, `changed_by` y `event_id` no llevan FK a
  otros contextos.
- Justificación de campos (principio VIII): `requested_by` y `changed_by` sirven al historial (FR-010) y son ids
  opacos, anulables para anonimizar; `reason` lo pide FR-001 con límite de FR-023; no hay otros datos de personas.
- `ON DELETE CASCADE` solo para la purga a un año (FR-022).

## Módulo `agenda` (mínimo)

### Agregado `Event` (evento)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `EventId` | generado en `agenda` antes de pedir la reserva |
| `communityId` | `CommunityId` | moderador: la suya; administrador: la elegida (`422 COMMUNITY_REQUIRED` si falta) |
| `type` | `EventType` | `WORD` (Palabra), `CONVIVENCE` (Convivencia de un día) |
| `title` | `EventTitle` | 1..120 |
| `date` | `LocalDate` | con `slot.start` posterior a `now` |
| `slot` | `TimeSlot` | propio de `agenda` (no se comparte el de `spaces`) |
| `roomId` | `RoomId` | referencia opaca a `spaces` |
| `bookingRequestId` | `BookingRequestId` | devuelto por `SpacesApi.requestForEvent` |
| `createdBy` | `UserId?` | nulo tras anonimizar |

Casos de uso:

- `CreateEvent` (transaccional): política de actor y comunidad, llamada a `SpacesApi.requestForEvent`; `Occupied` →
  `RoomOccupied`, `RoomUnavailable` → `RoomInactive` o `RoomNotFound`; cualquiera de ellos, rollback.
- `GetEvent`: moderador solo de su comunidad (`AgendaForbidden`), administrador cualquiera; añade el estado de la
  solicitud con `SpacesApi.bookingRequestStatus`; si ya no existe (purgada), `EventNotFound`.
- `AnonymizeCreator(userId)` y `PurgeExpiredEvents(today)` (R14).

Puerto `out`: `EventRepository` (`save`, `findById`, `anonymize(userId)`, `deleteWithDateBefore(date)`).

```text
event
  id                 uuid         PK
  community_id       uuid         not null
  type               varchar(16)  not null check in (WORD, CONVIVENCE)
  title              varchar(120) not null
  event_date         date         not null
  start_time         time         not null
  end_time           time         not null, check (end_time > start_time)
  room_id            uuid         not null
  booking_request_id uuid         not null, unique
  created_by         uuid         null
  created_at         timestamptz  not null
  índices: (event_date), (created_by)
```

Migración propia de `agenda` (`V4__agenda.sql`); sin FK hacia tablas de `spaces` (contextos separados, solo ids).

## Transiciones

```text
BookingRequest:  (alta) → PENDING ─approve→ APPROVED
                          PENDING ─reject──→ REJECTED
                          PENDING ─rectifyReason→ PENDING
                 PENDING|APPROVED ─cancel (alguna no empezada)──→ CANCELLED
                 PENDING|APPROVED ─cancelBooking (última activa)→ CANCELLED

Booking:         PENDING → APPROVED | REJECTED | CANCELLED (si no ha empezado)
                 APPROVED → CANCELLED (si no ha empezado)

Room:            (alta) → activo ─rename→ activo
                 activo ─deactivate (sin reservas activas futuras)→ dado de baja (definitivo)
```
