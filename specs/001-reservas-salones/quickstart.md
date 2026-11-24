# Quickstart: validar Reservas de salones

**Feature**: `001-reservas-salones` | **Plan**: [plan.md](./plan.md) | **Contrato**: [contracts/room-bookings.openapi.yaml](./contracts/room-bookings.openapi.yaml) | **Modelo**: [data-model.md](./data-model.md)

Guía para comprobar de punta a punta que la feature funciona. No contiene implementación.

## Requisitos previos

- Java 25 en `JAVA_HOME`, Node LTS, Docker (Rancher Desktop vale).
- Contrato fusionado en `api/openapi.yaml` y código generado (`./mvnw -B generate-sources`).
- Con Rancher Desktop: `export TESTCONTAINERS_RYUK_DISABLED=true`.
- Feature «Identidad y acceso» implementada: un administrador y un moderador por comunidad (A y B) con datos
  sintéticos, sin datos personales reales. Los salones se crean con la API (paso 0).

## 1. Gates automáticos

```bash
cd backend && ./mvnw -B verify
cd app && npm run lint && npm run typecheck && npx expo export -p web
```

Esperado: exit 0. `verify` incluye tests de dominio y aplicación sin Spring, `ArchitectureTest`, `ModularityTest`
(`ApplicationModules.verify()`) y tests de adaptador con Testcontainers.

## 2. Escenarios que deben estar cubiertos por tests

| Escenario | Spec | Nivel de test | Resultado esperado |
|---|---|---|---|
| Solicitud simple en salón libre | US1-1 | aplicación + REST | `201`, estado `PENDING`, comunidad del moderador |
| Fin ≤ inicio, fecha pasada | US1-2, FR-002 | dominio + REST | `422` con `END_NOT_AFTER_START` / `DATE_IN_THE_PAST`; nada guardado |
| Moderador A no ve las de B | US1-3, FR-006 | aplicación + REST | lista solo con comunidad A |
| Solapes 19-21, 17-18:30, 17-21 contra 18-20 | US2-1, FR-003 | dominio + persistencia | `409 ROOM_OCCUPIED` con el conflicto |
| Contiguas 20-21 y 16-18; otro salón | US2-2, US2-3 | dominio + persistencia | `201` |
| Dos solicitudes simultáneas misma franja | US2-4, FR-013, SC-002 | persistencia (dos hilos, Testcontainers) | una `201`, otra `409`; una sola fila activa |
| Rechazar o cancelar libera la franja | US2-5, US3-2 | persistencia | nueva solicitud en la franja `201` |
| Aprobar / rechazar con motivo | US3-1, US3-2 | aplicación + REST | `APPROVED` / `REJECTED` y motivo visible |
| No administrador aprueba | US3-3, FR-009 | aplicación + REST | `403` |
| Dos administradores resuelven a la vez | Edge case, R5 | persistencia | uno `200`, otro `409 BOOKING_REQUEST_ALREADY_RESOLVED` |
| Moderador cancela otra comunidad | US4-2 | aplicación + REST | `403` |
| Ocupación sin motivos ni personas | US4-3, FR-008 | REST | solo franjas, estado y `ownCommunity` |
| Serie semanal 8 semanas con un choque | US5-2, FR-015 | aplicación + persistencia | `409` con la fecha en conflicto; ninguna ocurrencia guardada |
| Serie semanal sin choques | US5-1 | aplicación + persistencia | `201`, `occurrenceCount = 8` |
| Fecha fin > 1 año o ausente | Edge case | dominio + REST | `422 RECURRENCE_TOO_LONG` / `REQUIRED` |
| Cancelar una ocurrencia / la serie | US5-3, US5-4, FR-016 | aplicación + persistencia | solo esa franja libre / futuras canceladas, pasadas intactas |
| Alta de evento con salón libre | US5-5, FR-014, FR-020 | aplicación + persistencia + REST | `201`, evento y solicitud `PENDING` con su `eventId` |
| Alta de evento con salón ocupado | US5-6, FR-014 | aplicación + persistencia | `409 ROOM_OCCUPIED`; ni evento ni solicitud en BD |
| Consulta de evento | US5-7, FR-021 | REST | evento con `bookingStatus` actual |
| Crear, renombrar salón | US6-1, US6-2, FR-017 | aplicación + REST | `201` / `200`; nombre visible en solicitudes |
| Nombre vacío o repetido entre activos | US6-6 | dominio + persistencia | `422 REQUIRED` / `409 ROOM_NAME_TAKEN` |
| Baja sin reservas futuras activas | US6-3, FR-018, FR-019 | aplicación + REST | `204`; no sale en `GET /api/rooms`; historial intacto |
| Baja con reservas futuras activas | US6-4, FR-018 | aplicación + persistencia | `409 ROOM_HAS_ACTIVE_BOOKINGS` con el número |
| Baja concurrente con una solicitud nueva | R10 | persistencia (dos hilos) | o la baja falla con `409`, o la solicitud falla con `ROOM_INACTIVE`; nunca solicitud activa en salón dado de baja |
| No administrador gestiona salones | US6-5, FR-017 | REST | `403` |
| `agenda` no depende de internos de `spaces` | Principio III | `ModularityTest` | `verify()` en verde |
| Historial de estados | FR-010 | aplicación + REST | `history` con alta, resolución y cancelación |
| Logs sin datos personales | FR-011 | revisión + test de log | sin motivos ni nombres en la salida |

## 3. Prueba manual contra la API

Arrancar PostgreSQL local y el backend con el perfil que carga `db/testdata`, y obtener un token de moderador y otro de
administrador con el mecanismo de la feature de identidad.

0. Administrador: `POST /api/rooms` con `Salón A` y con `Salón B` → `201`; repetir `Salón A` → `409 ROOM_NAME_TAKEN`.
1. `GET /api/rooms` → aparecen `Salón A` y `Salón B`.
2. Moderador A: `POST /api/booking-requests` con Salón A, mañana, `18:00`–`20:00`, motivo «Palabra» → `201 PENDING`.
3. Moderador B: misma sala y día, `19:00`–`21:00` → `409 ROOM_OCCUPIED` con el conflicto `18:00`–`20:00`.
4. Moderador B: `20:00`–`21:00` → `201`.
5. Moderador B: `GET /api/rooms/{salonA}/occupancy?date=<mañana>` → dos franjas; la de A con `ownCommunity=false` y sin
   motivo.
6. Administrador: `POST /api/booking-requests/{id}/rejection` con motivo sobre la del paso 2 → `REJECTED`; repetir →
   `409`.
7. Moderador B: `18:00`–`20:00` → `201` (franja liberada).
8. Moderador A: serie semanal desde la semana próxima hasta 8 semanas después que incluya el día del paso 7 → `409` con
   esa fecha; sin ese día → `201` con 8 ocurrencias.
9. Moderador A: cancelar una ocurrencia → solo esa queda `CANCELLED`; cancelar la serie → futuras `CANCELLED`.
10. Moderador A: `POST /api/events` tipo `WORD`, Salón B, en una franja ocupada → `409`; `GET /api/booking-requests`
    no muestra ninguna solicitud nueva. En una franja libre → `201` con `bookingStatus = PENDING`.
11. Administrador: `POST /api/rooms/{salonB}/deactivation` → `409 ROOM_HAS_ACTIVE_BOOKINGS`; tras cancelar sus
    reservas futuras → `204`, y Salón B ya no aparece en `GET /api/rooms`.

## 4. Prueba manual en el cliente

`cd app && npx expo start --web` y, con el moderador A:

- Solicitar un salón en menos de 2 minutos (SC-001) viendo antes las franjas ocupadas.
- Un choque muestra un mensaje claro con las fechas en conflicto.
- Cancelar pide confirmación.
- Con el administrador: crear, renombrar y dar de baja un salón; la baja pide confirmación.
- Dar de alta una Palabra con salón y ver el estado de su solicitud.
- Comprobar accesibilidad: texto ≥ 18 px, objetivos ≥ 48 px, etiquetas en controles, contraste AA en claro y oscuro,
  escalado de fuente del sistema y movimiento reducido.
