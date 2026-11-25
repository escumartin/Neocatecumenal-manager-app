# Quickstart: validar Reservas de salones

**Feature**: `001-reservas-salones` | **Plan**: [plan.md](./plan.md) | **Contrato**: [contracts/room-bookings.openapi.yaml](./contracts/room-bookings.openapi.yaml) | **Modelo**: [data-model.md](./data-model.md)

Guía para comprobar de punta a punta que la feature funciona. No contiene implementación.

## Requisitos previos

- Java 25 en `JAVA_HOME`, Node ≥ 24, Docker (Rancher Desktop vale).
- Contrato fusionado en `api/openapi.yaml` y código generado (`cd backend && ./mvnw -B generate-sources`;
  `cd app && npm run api:generate`).
- Con Rancher Desktop: `export TESTCONTAINERS_RYUK_DISABLED=true`.
- Identidad ya implementada: un administrador y un moderador por comunidad (A y B) con datos sintéticos. Los salones se
  crean con la API (paso 0).

## 1. Gates automáticos

```bash
cd backend && ./mvnw -B verify
cd app && npm run lint && npm run typecheck && npx expo export -p web
cd app && npm test
```

Esperado: exit 0. `verify` incluye tests de dominio y aplicación sin Spring, `ArchitectureTest`, `ModularityTest`
(`ApplicationModules.verify()`) y tests de adaptador con Testcontainers.

## 2. Requisitos y sus tests

Niveles: **D** dominio, **A** aplicación con fakes, **P** persistencia (Testcontainers), **R** REST (MockMvc con
`@MockitoBean IdentityApi`), **C** componente del cliente (Jest + Testing Library), **M** revisión manual.

| FR | Camino feliz | Caso de error | Niveles |
|---|---|---|---|
| FR-001 | moderador A solicita salón libre → `201 PENDING` con comunidad A (US1-1); administrador con `communityId=B` → `201` con comunidad B (US1-4) | administrador sin `communityId` → `422 COMMUNITY_REQUIRED`; con comunidad inexistente → `422 COMMUNITY_NOT_FOUND`; moderador A con `communityId=B` → `403`; `REGISTERED` → `403` | D, A, R |
| FR-002 | franja de hoy que empieza dentro de una hora → `201` | fin ≤ inicio → `422 END_NOT_AFTER_START`; inicio ya pasado hoy o fecha pasada → `422 START_NOT_IN_FUTURE`; nada guardado (US1-2) | D, R |
| FR-003 | contiguas 20-21 y 16-18 contra 18-20 y otro salón en la misma franja → `201` (US2-2, US2-3) | 19-21, 17-18:30 y 17-21 contra 18-20 → `409 ROOM_OCCUPIED` con el conflicto (US2-1) | D, P |
| FR-004 | `PENDING→APPROVED`, `PENDING→REJECTED`, `PENDING/APPROVED→CANCELLED` | aprobar o rechazar una `REJECTED`/`CANCELLED` → `409 BOOKING_REQUEST_ALREADY_RESOLVED`; aprobar una `PENDING` cuya primera ocurrencia ya empezó → `422 START_NOT_IN_FUTURE` (rechazarla sí vale); cancelar una `REJECTED` → `409 BOOKING_REQUEST_NOT_ACTIVE`; cancelar una franja ya empezada → `409 BOOKING_ALREADY_STARTED` | D, R |
| FR-005 | administrador lista `status=PENDING` sin `communityId` y ve las de A y B; aprueba una serie entera (todas sus ocurrencias `APPROVED`); rechaza con motivo | dos administradores resuelven a la vez → uno `200`, otro `409 BOOKING_REQUEST_ALREADY_RESOLVED` (incluido `OptimisticLockingFailureException`); invariante: no se pueden insertar ocurrencias activas solapadas con una serie pendiente, así que aprobarla nunca choca (US3-4) | A, P, R |
| FR-006 | moderador A lista y solo ve A (US1-3); administrador ve A y B, y con `communityId=B` solo B | moderador A pide `communityId=B` en el listado → `403`; moderador A pide `GET` de una solicitud de B → `404` (no revela que existe) | A, R |
| FR-007 | moderador A cancela una reserva aprobada de A → `CANCELLED`, franja libre (US4-1); administrador cancela una de B | moderador A cancela una de B → `403` (US4-2); franja empezada → `409 BOOKING_ALREADY_STARTED` (US4-3); confirmación previa en el cliente (C) | D, A, R, C |
| FR-008 | moderador B consulta ocupación → franjas de A con `ownCommunity=false` y sin motivo (US4-4); administrador con `communityId=B` | administrador sin `communityId` → `422 COMMUNITY_REQUIRED`; moderador A con `communityId=B` → `403`; la respuesta no contiene `reason` ni ids de personas (aserción sobre el JSON) | A, R |
| FR-009 | administrador aprueba y rechaza series enteras | moderador o `REGISTERED` aprueba o rechaza → `403` (US3-3) | D, R |
| FR-010 | `history` con alta, resolución y cancelación, con `changedBy` y `changedAt` | cancelar una ocurrencia añade un cambio con `bookingId`; una transición fallida no añade historial | D, A, R |
| FR-011 | `booking_request` solo con las columnas de [data-model.md](./data-model.md) | test de logs: tras crear, rechazar y cancelar con un motivo centinela, la salida no contiene el motivo, nombres ni `accessId` | P, R |
| FR-012 | test de componente de las pantallas de solicitar, cancelar y salones: cada control tiene `accessibilityRole` y `accessibilityLabel`, los botones usan la variante de tamaño con altura ≥ 48 px y los textos la de ≥ 18 px de `design-system` | test de componente: el diálogo de confirmación es accesible (rol `alertdialog`, foco y etiquetas); revisión manual de contraste AA claro/oscuro, escalado de fuente y movimiento reducido (§ 4) | C, M |
| FR-013 | rechazar o cancelar libera la franja: nueva solicitud en la misma franja → `201` (US2-5) | dos solicitudes simultáneas en dos hilos → una `201`, otra `409 ROOM_OCCUPIED` (posiblemente con `conflicts` vacío); una sola fila activa (US2-4, SC-002) | P |
| FR-014 | alta de evento con salón libre → `201`, evento y solicitud `PENDING` con su `eventId` (US5-7) | salón ocupado → `409 ROOM_OCCUPIED` y ni evento ni solicitud en BD (US5-8); salón dado de baja → `409 ROOM_INACTIVE` y rollback | A, P, R |
| FR-015 | serie semanal de 8 semanas sin choques → `201`, `occurrenceCount = 8` (US5-1); `until` justo a un año → `201` con ≤ 53 | una ocurrencia choca → `409` con esa fecha y ninguna guardada (US5-2); sin `until` → `422 REQUIRED`; `until` > 1 año → `422 RECURRENCE_TOO_LONG`; `until` < fecha → `422 RECURRENCE_UNTIL_BEFORE_DATE`; `frequency` distinta de `WEEKLY` → `422 INVALID_FORMAT` (US5-5) | D, A, P, R |
| FR-016 | cancelar una ocurrencia → solo esa `CANCELLED` (US5-3); cancelar la serie → futuras `CANCELLED`, pasadas y empezadas intactas (US5-4, US5-6) | cancelar una ocurrencia empezada → `409 BOOKING_ALREADY_STARTED`; cancelar una serie sin ninguna ocurrencia futura → `409 BOOKING_ALREADY_STARTED`; moderador de otra comunidad → `403` | D, A, P |
| FR-017 | administrador crea y renombra un salón → `201` / `200`, nombre nuevo visible en solicitudes (US6-1, US6-2) | nombre vacío → `422 REQUIRED`; repetido entre activos → `409 ROOM_NAME_TAKEN` (US6-6); moderador gestiona salones → `403` (US6-5) | D, A, P, R |
| FR-018 | baja sin reservas activas futuras → `204`, historial intacto (US6-3) | con reservas activas con franja futura (las pendientes pasadas no cuentan) → `409 ROOM_HAS_ACTIVE_BOOKINGS` con `activeBookings` (US6-4); baja concurrente con una solicitud (dos hilos) → nunca solicitud activa en salón dado de baja; renombrar o dar de baja un salón ya dado de baja → `409 ROOM_INACTIVE` | D, A, P, R |
| FR-019 | `GET /api/rooms` solo devuelve activos | solicitar un salón dado de baja → `409 ROOM_INACTIVE`; ocupación de un salón dado de baja → `404` y no aparece en la lista | A, R |
| FR-020 | moderador A da de alta una Palabra de A → `201`; administrador con `communityId=B` da de alta una Convivencia de B → `201` | administrador sin `communityId` → `422 COMMUNITY_REQUIRED`; moderador A con `communityId=B` → `403`; tipo no admitido → `422 INVALID_FORMAT`; título > 120 → `422 TOO_LONG` | D, A, R |
| FR-021 | moderador A consulta su evento con `bookingStatus` actual, también tras rechazar o cancelar su solicitud (US5-9) | moderador B consulta el evento de A → `404` (no revela que existe); evento inexistente → `404` | A, R |
| FR-022 | al publicar `UserDeleted` del moderador A en la transacción de borrado, sus `requested_by`, `changed_by` y `created_by` quedan a `NULL`, la reserva sigue y `changedBy` sale ausente | si la anonimización falla, el borrado del usuario se deshace (misma transacción); purga: solicitud con `last_date` hace 1 año + 1 día → borrada con ocurrencias e historial; con `last_date` hace 1 año justo → se conserva; evento con `event_date` hace más de un año → borrado | A, P |
| FR-023 | motivo de 500 caracteres → `201`; moderador A rectifica el motivo de su solicitud `PENDING` → `200` (US1-6); rechazo con motivo de 500 → `200` | motivo de 501 → `422 TOO_LONG` (US1-5); rectificar una `APPROVED` → `409 BOOKING_REQUEST_ALREADY_RESOLVED`; moderador B rectifica una de A → `403`; el formulario muestra el aviso de no incluir datos personales (C) | D, R, C |
| Principio III | `agenda` y `spaces` solo usan la raíz de `identity` y `agenda` solo la de `spaces` | `ModularityTest` falla si alguien importa internos | `ApplicationModules.verify()` |

## 3. Prueba manual contra la API

Arrancar PostgreSQL local y el backend, y obtener una sesión de moderador A, moderador B y administrador con
`POST /api/sessions`.

0. Administrador: `POST /api/rooms` con `Salón A` y con `Salón B` → `201`; repetir `Salón A` → `409 ROOM_NAME_TAKEN`.
1. `GET /api/rooms` → aparecen `Salón A` y `Salón B`.
2. Moderador A: `POST /api/booking-requests` con Salón A, mañana, `18:00`–`20:00`, motivo «Palabra» → `201 PENDING`.
3. Moderador B: misma sala y día, `19:00`–`21:00` → `409 ROOM_OCCUPIED` con el conflicto `18:00`–`20:00`.
4. Moderador B: `20:00`–`21:00` → `201`.
5. Moderador B: `GET /api/rooms/{salonA}/occupancy?date=<mañana>` → dos franjas; la de A con `ownCommunity=false` y sin
   motivo. Administrador sin `communityId` → `422 COMMUNITY_REQUIRED`.
6. Moderador A: `PUT /api/booking-requests/{id}/reason` sobre la del paso 2 → `200` con el motivo nuevo.
7. Administrador: `GET /api/booking-requests?status=PENDING` → ve las de A y B. `POST .../rejection` con motivo sobre la
   del paso 2 → `REJECTED`; repetir → `409 BOOKING_REQUEST_ALREADY_RESOLVED`.
8. Moderador B: `18:00`–`20:00` → `201` (franja liberada).
9. Moderador A: serie semanal en Salón A, `18:00`–`20:00`, desde mañana y `until` 7 semanas después → `409` con la fecha
   de mañana (ocupada en el paso 8); la misma serie desde una semana después y `until` 7 semanas después de ese nuevo
   inicio → `201` con 8 ocurrencias. `until` a 13 meses → `422 VALIDATION_FAILED` con
   `errors[0] = {field: recurrence.until, code: RECURRENCE_TOO_LONG}`.
10. Moderador A: cancelar una ocurrencia → solo esa `CANCELLED`; cancelar la serie → futuras `CANCELLED`.
11. Moderador A: `POST /api/events` tipo `WORD` en Salón A, mañana `18:00`–`20:00` (ocupada en el paso 8) → `409`;
    `GET /api/booking-requests` no muestra ninguna solicitud nueva. En Salón B, en una franja libre → `201` con
    `bookingStatus = PENDING`. Moderador B:
    `GET /api/events/{id}` → `404`.
12. Administrador: `POST /api/rooms/{salonB}/deactivation` → `409 ROOM_HAS_ACTIVE_BOOKINGS`; tras cancelar sus
    reservas futuras → `204`; Salón B ya no aparece en `GET /api/rooms` y solicitarlo → `409 ROOM_INACTIVE`.
13. Administrador: crear un segundo moderador en la comunidad B y borrar al moderador B original
    (`DELETE /api/users/{id}`; borrar al único moderador daría `409 LAST_MODERATOR`) → sus solicitudes siguen y su
    `history` muestra `changedBy` ausente.

## 4. Prueba manual en el cliente

`cd app && npx expo start --web` y, con el moderador A:

- Solicitar un salón en menos de 2 minutos (SC-001) viendo antes las franjas ocupadas.
- El campo de motivo avisa de no incluir datos personales y corta en 500 caracteres.
- Un choque muestra un mensaje claro con las fechas en conflicto.
- Cancelar pide confirmación; una franja empezada no ofrece cancelar.
- Con el administrador: elegir comunidad antes de solicitar; crear, renombrar y dar de baja un salón; la baja pide
  confirmación.
- Dar de alta una Palabra con salón y ver el estado de su solicitud.
- Accesibilidad (FR-012): texto ≥ 18 px, objetivos ≥ 48 px, etiquetas en controles, contraste AA en claro y oscuro,
  escalado de fuente del sistema al 200 % sin cortes y movimiento reducido.
