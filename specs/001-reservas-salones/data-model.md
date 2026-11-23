# Data Model: Reservas de salones

**Feature**: `001-reservas-salones` | **Fecha**: 2026-11-23 | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

Módulos `spaces` y `agenda` (mínimo). Nombres de dominio en inglés; entre paréntesis, el término del glosario.

Identificadores: UUID como clave interna de todas las entidades. Usuarios y comunidades se referencian solo por su UUID;
el número de comunidad y el id numérico de acceso son atributos únicos de la feature de identidad y no se guardan aquí
([research.md](./research.md) R11).

## Dominio (`spaces.domain`, Java puro)

### Value objects

| Tipo | Campos | Reglas |
|---|---|---|
| `RoomId`, `BookingRequestId`, `BookingId` | `UUID value` | no nulos |
| `CommunityId`, `UserId`, `EventId` | `UUID value` | ids de otros contextos, opacos |
| `TimeSlot` (franja) | `LocalTime start`, `LocalTime end` | `end > start`; mismo día; precisión de minutos; `overlaps(other)` semiabierto: `a.start < b.end && b.start < a.end` |
| `WeeklyRecurrence` | `LocalDate until` | `until >= firstDate`; `until <= firstDate.plusYears(1)`; `dates(firstDate)` devuelve `firstDate`, `+7 días`... hasta `until` (≤ 53) |
| `RoomName` | `String value` | 1..80 caracteres, recortado |
| `Reason` (motivo) | `String value` | 1..200 caracteres, recortado |
| `RejectionReason` | `String value` | 0..500 caracteres, opcional |
| `Actor` | `UserId userId`, `Role role`, `CommunityId communityId` | `communityId` obligatorio para `MODERATOR` |
| `Role` | `MODERATOR`, `ADMINISTRATOR` (otros roles no operan) | herencia: el administrador puede todo lo del moderador |
| `Conflict` | `LocalDate date`, `TimeSlot slot` | fecha y franja ocupadas que impiden la solicitud |

### Agregado `BookingRequest` (solicitud; serie si tiene repetición)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `BookingRequestId` | |
| `roomId` | `RoomId` | salón |
| `communityId` | `CommunityId` | propietaria |
| `requestedBy` | `UserId` | solicitante |
| `eventId` | `EventId?` | evento de Agenda, opcional |
| `reason` | `Reason` | |
| `firstDate` | `LocalDate` | fecha de la primera ocurrencia |
| `slot` | `TimeSlot` | misma franja en todas las ocurrencias |
| `recurrence` | `WeeklyRecurrence?` | nula en solicitudes simples |
| `status` | `BookingRequestStatus` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `rejectionReason` | `RejectionReason?` | solo si `REJECTED` |
| `bookings` | `List<Booking>` | 1..53 ocurrencias |
| `history` | `List<StatusChange>` | historial (FR-010) |

Fábrica `BookingRequest.request(actor, roomId, communityId, date, slot, reason, recurrence?, eventId?, clock)`:

- `date >= hoy` en `Europe/Madrid` (FR-002).
- El salón existe y está activo (FR-019); el servicio lo bloquea con `FOR SHARE` durante el alta (R10).
- Moderador: `communityId` igual al suyo (o se toma el suyo). Administrador: `communityId` obligatorio.
- Expande las ocurrencias (`PENDING`) y añade `StatusChange(null → PENDING)`.

Comportamiento:

| Método | Quién | Desde | Hacia | Efecto en ocurrencias |
|---|---|---|---|---|
| `approve(actor)` | administrador | `PENDING` | `APPROVED` | las `PENDING` pasan a `APPROVED` |
| `reject(actor, reason?)` | administrador | `PENDING` | `REJECTED` | las `PENDING` pasan a `REJECTED` (libera franjas) |
| `cancel(actor, today)` | moderador de la comunidad o administrador | `PENDING`, `APPROVED` | `CANCELLED` | las activas con fecha `>= today` pasan a `CANCELLED`; las pasadas no cambian |
| `cancelBooking(actor, bookingId, today)` | moderador de la comunidad o administrador | `PENDING`, `APPROVED` | igual; `CANCELLED` si ya no queda ninguna activa | solo esa ocurrencia, con fecha `>= today`, pasa a `CANCELLED` |

Transición no permitida → `BookingRequestNotPending` / `BookingRequestNotActive`. Sin permiso → `BookingForbidden`.
Cada cambio añade un `StatusChange`.

### Entidad `Booking` (ocurrencia)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `BookingId` | |
| `date` | `LocalDate` | |
| `slot` | `TimeSlot` | copia de la del agregado |
| `status` | `BookingStatus` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`; activa = `PENDING` o `APPROVED` |

### Entidad `StatusChange` (historial)

| Campo | Tipo | Notas |
|---|---|---|
| `bookingId` | `BookingId?` | nulo si el cambio es de toda la solicitud |
| `from` | `BookingRequestStatus?` / `BookingStatus?` | nulo en el alta |
| `to` | estado | |
| `changedBy` | `UserId` | |
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
| `rename(actor, name)` | administrador | solo activo |
| `deactivate(actor, activeFutureBookings)` | administrador | solo activo; `activeFutureBookings == 0`, si no `RoomHasActiveBookings(count)`; definitiva |

Hoteles y restaurantes quedan fuera del modelo ([research.md](./research.md) R10).

### Puertos (`spaces.application.port`)

- `in`: `RequestBooking`, `ApproveBookingRequest`, `RejectBookingRequest`, `CancelBookingRequest`, `CancelBooking`,
  `ListBookingRequests`, `GetBookingRequest`, `GetRoomOccupancy`, `ListRooms`, `CreateRoom`, `RenameRoom`,
  `DeactivateRoom`.
- `out`:
  - `BookingRequestRepository`: `save`, `findById`, `findConflicts(roomId, dates, slot)`, `search(criteria, page)`.
  - `RoomRepository`: `findById`, `findByIdForUpdate`, `findByIdForShare`, `findAllActive`, `existsActiveByName`,
    `save`.
  - `BookingCounter`: `activeFutureBookings(roomId, today)`.
  - `RoomOccupancyQuery`: `occupiedSlots(roomId, date)`.
  - `SpacesEventPublisher`: publica eventos de dominio.
- `save` traduce `23P01` a `RoomOccupied(conflicts)`; el servicio no ve SQL.

### API pública del módulo (`spaces`, raíz)

- `SpacesApi.requestForEvent(EventBookingCommand) → EventBookingResult` (`Booked(bookingRequestId)` o
  `Occupied(List<Conflict>)`).
- `SpacesApi.bookingRequestStatus(UUID bookingRequestId) → Optional<BookingRequestState>`; `BookingRequestState` es un
  enum exportado en la raíz de `spaces`, para no exponer tipos de `domain` a otros módulos.
- `EventBookingCommand(eventId, roomId, communityId, actor, date, startTime, endTime, reason)`; `actor` es un
  `ActorRef(userId, role, communityId)` exportado en la raíz de `spaces`.
- Eventos: `BookingRequestApproved`, `BookingRequestRejected`, `BookingRequestCancelled` (`bookingRequestId`,
  `eventId?`, `roomId`). Sin datos personales.

## Persistencia (PostgreSQL, Flyway `V2__spaces.sql`)

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
  requested_by     uuid         not null
  event_id         uuid         null, unique
  reason           varchar(200) not null
  first_date       date         not null
  start_time       time         not null
  end_time         time         not null, check (end_time > start_time)
  recurrence_until date         null
  status           varchar(16)  not null check in (PENDING, APPROVED, REJECTED, CANCELLED)
  rejection_reason varchar(500) null
  version          bigint       not null
  created_at       timestamptz  not null
  índices: (community_id, status), (status, first_date)

booking
  id                 uuid        PK
  booking_request_id uuid        not null FK booking_request(id)
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
  booking_request_id uuid        not null FK booking_request(id)
  booking_id         uuid        null FK booking(id)
  from_status        varchar(16) null
  to_status          varchar(16) not null
  changed_by         uuid        not null
  changed_at         timestamptz not null
```

- `CREATE EXTENSION IF NOT EXISTS btree_gist` al inicio de la migración.
- `booking.room_id` duplica el de la solicitud para que la restricción sea de una sola tabla; lo fija el adaptador.
- `period` no se mapea en JPA (`ddl-auto: validate` no la exige).
- Ninguna tabla se comparte con otros módulos; `community_id`, `requested_by` y `event_id` no llevan FK a otros contextos.
- Sin datos personales: `requested_by` y `changed_by` son ids opacos.

## Módulo `agenda` (mínimo)

### Agregado `Event` (evento)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `EventId` | generado en `agenda` antes de pedir la reserva |
| `communityId` | `CommunityId` | moderador: la suya; administrador: la elegida |
| `type` | `EventType` | `WORD` (Palabra), `CONVIVENCE` (Convivencia de un día) |
| `title` | `EventTitle` | 1..120 |
| `date` | `LocalDate` | `>= hoy` |
| `slot` | `TimeSlot` | propio de `agenda` (no se comparte el de `spaces`) |
| `roomId` | `RoomId` | referencia opaca a `spaces` |
| `bookingRequestId` | `BookingRequestId` | devuelto por `SpacesApi.requestForEvent` (UUID) |
| `createdBy` | `UserId` | |

Casos de uso: `CreateEvent` (transaccional, llama a `SpacesApi.requestForEvent`; `Occupied` → excepción y rollback) y
`GetEvent` (añade el estado de la solicitud con `SpacesApi.bookingRequestStatus`). Puerto `out`: `EventRepository`.

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
  created_by         uuid         not null
  created_at         timestamptz  not null
```

Migración propia de `agenda` (`V3__agenda.sql`); sin FK hacia tablas de `spaces` (contextos separados, solo ids).

## Transiciones

```text
BookingRequest:  (alta) → PENDING ─approve→ APPROVED
                          PENDING ─reject──→ REJECTED
                 PENDING|APPROVED ─cancel──→ CANCELLED
                 PENDING|APPROVED ─cancelBooking (última activa)→ CANCELLED

Booking:         PENDING → APPROVED | REJECTED | CANCELLED
                 APPROVED → CANCELLED

Room:            (alta) → activo ─rename→ activo
                 activo ─deactivate (sin reservas activas futuras)→ dado de baja (definitivo)
```
