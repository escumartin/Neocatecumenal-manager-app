# Research: Reservas de salones

**Feature**: `001-reservas-salones` | **Fecha**: 2026-11-23 | **Plan**: [plan.md](./plan.md)

Fase 0 del plan. Cada apartado resuelve una incógnita del contexto técnico con decisión, motivo y alternativas.

## R1. Bounded context y módulo Modulith

- **Decisión**: un único módulo `spaces` (paquete `io.github.escumartin.neocatecumenal.spaces`) para el bounded context
  «Espacios y reservas» de `dominio.md`. Contiene los salones (`Room`, con su gestión por el administrador) y las solicitudes de
  reserva. API pública en la raíz del módulo: `SpacesApi` y los eventos publicados.
- **Motivo**: `dominio.md` agrupa lugares y reservas en el mismo contexto. La regla de solape depende del salón, y la
  futura baja de un salón con reservas futuras («debe resolver antes esas reservas») se resuelve dentro del mismo módulo
  sin llamadas cruzadas.
- **Alternativas descartadas**:
  - Módulo `bookings` separado de `rooms`: exige una API entre ambos para algo que es una sola consistencia (salón y
    ocupación) y duplica el concepto de lugar.
  - Reservas dentro de Agenda de eventos: hay reservas sin evento (US1) y el dominio las separa.

## R2. Relación con Agenda de eventos (FR-014)

- **Decisión**: dependencia en un solo sentido, `agenda → spaces`.
  - Agenda llama de forma síncrona a `SpacesApi.requestForEvent(...)` dentro de su transacción de alta de evento. Si el
    salón está ocupado, `SpacesApi` devuelve un resultado de conflicto (fechas en conflicto) y Agenda no guarda el evento:
    misma transacción, todo o nada.
  - `spaces` guarda `eventId` como identificador opaco (`EventId`, UUID) y no conoce el modelo de Agenda.
  - `spaces` publica `BookingRequestApproved`, `BookingRequestRejected` y `BookingRequestCancelled` (con `eventId` si lo
    hay). La Agenda completa los consumirá con `@ApplicationModuleListener`; la mínima no los escucha (R12).
- **Motivo**: «si el salón está ocupado, el evento no se guarda» exige respuesta síncrona; un evento de dominio
  asíncrono no puede impedir el alta. El sentido único evita ciclos entre módulos (`ApplicationModules.verify()`).
- **Alcance**: esta feature crea un módulo `agenda` mínimo (ver R12) con el alta y la consulta de un evento con salón.
  El escenario US5-5 se valida de punta a punta. Editar o borrar eventos queda fuera, así que no hace falta
  `cancelForEvent`; se añadirá con la feature completa de Agenda.
- **Alternativas descartadas**:
  - Agenda publica `EventCreated` y `spaces` reacciona: asíncrono, no puede impedir el alta y crea la dependencia
    `spaces → agenda`.
  - Reserva creada por el cliente antes del evento (dos llamadas HTTP): rompe la atomicidad y deja reservas huérfanas.

## R3. Modelo de solicitud y serie

- **Decisión**: agregado `BookingRequest` (la solicitud, simple o periódica) con 1..n entidades hijas `Booking` (las
  ocurrencias). Una solicitud simple es una serie de una ocurrencia.
  - El estado de la solicitud (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`) se aprueba o rechaza como un todo.
  - Cada `Booking` lleva su propio estado, que se usa para la restricción de solape y para cancelar ocurrencias sueltas.
  - Regla de repetición `WeeklyRecurrence(until)`: frecuencia semanal, fecha fin obligatoria, `until >= date`,
    `until <= date + 1 año`. Se expande en el dominio a fechas concretas (máximo 53 ocurrencias).
- **Motivo**: un solo modelo para simple y serie; el agregado es pequeño (≤ 53 hijos) y la consistencia de aprobar o
  rechazar la serie entera queda dentro de un agregado.
- **Alternativas descartadas**:
  - Guardar la regla y calcular ocurrencias al vuelo: impide la restricción de exclusión en base de datos y cancelar
    ocurrencias sueltas.
  - `Booking` como agregado y `Series` como agrupador: aprobar la serie tocaría varios agregados en una transacción.
  - RRULE (RFC 5545) completa: v1 solo necesita semanal con fecha fin; se puede añadir después sin romper el modelo.

## R4. Sin solapes con concurrencia en PostgreSQL (FR-003, FR-013, SC-002)

- **Decisión**: restricción de exclusión GiST en la tabla `booking`, como garantía final, más comprobación previa en la
  aplicación para informar de las fechas en conflicto.
  - Extensión `btree_gist` (es *trusted* desde PostgreSQL 13: la crea el propietario de la base sin superusuario).
  - Columna generada `period tsrange GENERATED ALWAYS AS (tsrange(booking_date + start_time, booking_date + end_time, '[)')) STORED`.
  - `EXCLUDE USING gist (room_id WITH =, period WITH &&) WHERE (status IN ('PENDING', 'APPROVED'))`.
  - Intervalo semiabierto `[)`: las franjas contiguas no se solapan; inicio dentro, fin dentro y envolvente sí.
  - Hora local sin zona (`timestamp` sin `tz`): zona única de la parroquia (`Europe/Madrid`); evita desfases con el
    cambio de hora en franjas de un mismo día.
  - Flujo de alta: (1) el dominio valida y expande; (2) consulta de ocupación de las fechas implicadas y, si hay choque,
    error `RoomOccupied` con las fechas; (3) inserción de todas las ocurrencias en la misma transacción; (4) si otra
    transacción ganó la carrera, PostgreSQL lanza `23P01 exclusion_violation`, la transacción se deshace entera y el
    adaptador lo traduce a `RoomOccupied` (con las fechas recalculadas en una consulta posterior).
  - Rechazar o cancelar cambia `status` y libera la franja al instante, porque la restricción es parcial.
- **Motivo**: la restricción es atómica y no depende del nivel de aislamiento; cubre «gana quien solicita antes» y la
  serie todo o nada sin bloqueos explícitos.
- **Alternativas descartadas**:
  - Solo comprobación en la aplicación: con `READ COMMITTED` dos transacciones concurrentes pasan ambas.
  - `SERIALIZABLE`: reintentos y falsos positivos; más complejo para el mismo resultado.
  - `SELECT ... FOR UPDATE` sobre el salón: serializa todas las reservas del salón y no protege si alguien se lo salta.
  - Tramos fijos con `UNIQUE`: la spec fija franjas libres.

## R5. Resolución concurrente (dos administradores)

- **Decisión**: bloqueo optimista con `version` en `booking_request` (`@Version` en la entidad JPA) y transición
  validada en el dominio (`approve`/`reject` solo desde `PENDING`). La segunda resolución recibe `409` con código
  `BOOKING_REQUEST_ALREADY_RESOLVED`.
- **Alternativas descartadas**: bloqueo pesimista (innecesario a esta escala); `If-Match`/ETag en la API (se puede añadir
  después sin cambiar el modelo).

## R6. Identidad y autorización

- **Decisión**: el actor (`Actor(userId, role, communityId)`) se resuelve en el adaptador REST y entra en cada comando;
  la autorización por rol y comunidad vive en el dominio/aplicación (`BookingPolicy`, `RoomPolicy`, `EventPolicy`). Los
  adaptadores REST de `spaces` y `agenda` obtienen el usuario autenticado a través del puerto `CurrentActorProvider`, que
  se implementa sobre la API pública del módulo de identidad.
- **Dependencia previa**: la feature «Identidad y acceso» se especifica e implementa antes que esta y aporta la
  autenticación, el rol y la comunidad del usuario. Este plan no incluye ningún adaptador provisional; los tests de
  adaptador sustituyen el puerto por un doble de test.
- **Motivo**: las reglas «moderador solo su comunidad» y «solo administrador resuelve o gestiona salones» son de
  negocio y se prueban sin Spring; el mecanismo de autenticación lo decide la feature de identidad.
- Rol evaluado en el momento de la acción: quien deja de ser moderador conserva su historial (`requested_by`) pero no
  puede crear ni cancelar.

## R7. Contrato OpenAPI y generación

- **Decisión**: borrador en `contracts/room-bookings.openapi.yaml` (OpenAPI 3.1), que se fusionará en
  `api/openapi.yaml` como primer paso de la implementación (principio V).
  - Backend: `openapi-generator-maven-plugin` con generador `spring`, `interfaceOnly=true`, `useSpringBoot3=true`,
    paquete `io.github.escumartin.neocatecumenal.api.generated`; los controladores de `spaces/infrastructure/in/rest`
    implementan las interfaces generadas.
  - Cliente: tipos generados con `openapi-typescript` y llamadas con `openapi-fetch`, en `app/src/lib/api/`.
  - Errores con Problem Details (RFC 9457, `application/problem+json`) y un `code` estable para que el cliente traduzca
    los mensajes.
  - Acciones de estado como subrecursos `POST .../approval`, `.../rejection`, `.../cancellation`: verbos de negocio
    explícitos y autorización distinta por acción.
- **Alternativas descartadas**: `PATCH` con `status` (mezcla permisos y transiciones en un endpoint); generación del
  contrato desde el código (contradice el principio V).

## R8. Privacidad (FR-008, FR-011, principio VIII)

- **Decisión**: `booking_request` guarda solo salón, comunidad, solicitante (`requested_by`, id opaco), evento opcional,
  motivo, estado y motivo de rechazo; nada de nombres, teléfonos ni correos. La consulta de ocupación devuelve solo
  franjas (inicio y fin) y un indicador de si es de la comunidad del actor. Los logs registran ids y códigos de error,
  nunca motivos ni datos de personas. Motivo máximo 200 caracteres; motivo de rechazo máximo 500.

## R9. Fechas y validaciones de tiempo

- **Decisión**: `java.time.Clock` inyectado con zona `Europe/Madrid`; la fecha no puede ser anterior a hoy; franja en
  minutos (`HH:mm`), `end > start`, mismo día (no hay `24:00`). Solo se cancelan ocurrencias con fecha de hoy en
  adelante; cancelar una serie cancela sus ocurrencias futuras y deja las pasadas como están.

## R10. Baja de salones

- **Decisión**: la baja es siempre lógica y definitiva (`active = false`, sin reactivación). Se bloquea con `409
  ROOM_HAS_ACTIVE_BOOKINGS` (con el número de reservas) mientras el salón tenga ocurrencias `PENDING` o `APPROVED` con
  fecha de hoy en adelante; el administrador las rechaza o cancela antes. El nombre es único entre salones activos
  (índice único parcial `WHERE active`).
- **Motivo**: es la regla más simple y segura. Las reservas pasadas y su historial conservan la FK al salón; no hay
  dos caminos (borrado físico o lógico), y la spec ya exige resolver antes las reservas futuras. Cumple «borrar es
  definitivo y pide confirmación» de `dominio.md`: para el usuario la baja no tiene vuelta atrás.
- **Carrera baja contra solicitud**: la baja toma `SELECT ... FOR UPDATE` sobre la fila del salón y el alta de
  solicitudes toma `FOR SHARE` sobre la misma fila antes de insertar; así no entra una solicitud en un salón que se está
  dando de baja.
- **Alternativas descartadas**:
  - Borrado físico si no hay reservas y lógico si las hay: dos comportamientos para la misma acción.
  - Cancelar en cascada las reservas futuras al dar de baja: deja a comunidades sin salón sin que nadie lo decida.
  - Reactivar salones: no lo pide la spec.

## R11. Identificadores

- **Decisión**: UUID como clave interna de todas las entidades (`room`, `booking_request`, `booking`, `event`). Los
  módulos `spaces` y `agenda` referencian usuarios y comunidades solo por su UUID. El número de comunidad y el
  identificador numérico de acceso son atributos de negocio con unicidad que pertenecen a la feature de identidad y
  comunidades; esta feature no los guarda ni los duplica.
- **Motivo**: las claves internas no dependen de reglas de negocio que pueden cambiar (renumerar una comunidad) y no
  revelan información en las URL.

## R12. Agenda mínima

- **Decisión**: módulo `agenda` (paquete `io.github.escumartin.neocatecumenal.agenda`) con el agregado `Event` y solo
  dos casos de uso: `CreateEvent` (alta con salón) y `GetEvent` (consulta).
  - Tipos admitidos: `WORD` (Palabra) y `CONVIVENCE` (Convivencia de un día). Campos: comunidad, tipo, título
    (1..120), fecha, franja, salón y `bookingRequestId`.
  - `CreateEventService` es transaccional: valida el evento, llama a `SpacesApi.requestForEvent` y guarda el evento
    con el `bookingRequestId` devuelto. Si la respuesta es `Occupied`, lanza `RoomOccupied` y la transacción entera se
    deshace: ni evento ni solicitud.
  - El `eventId` se genera en `agenda` antes de llamar a `spaces`, que lo guarda como referencia opaca con unicidad.
  - `GetEvent` devuelve el evento y el estado de su solicitud, consultado con `SpacesApi.bookingRequestStatus(id)`.
  - `agenda` no escucha aún los eventos de `spaces`: el estado se lee al consultar.
- **Fuera de alcance**: edición y borrado de eventos, listados, consulta por los registrados, Eucaristía, Paso,
  Convivencia de fin de semana, eventos periódicos, lecturas, cuerpo, precio y reglas de día de la semana por tipo.
- **Alternativas descartadas**: guardar el evento dentro de `spaces` (mezcla contextos que `dominio.md` separa);
  crear primero la solicitud desde el cliente (rompe la atomicidad).
