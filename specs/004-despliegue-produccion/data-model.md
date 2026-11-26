# Data Model: Despliegue a producción

**Feature**: `004-despliegue-produccion` | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

La feature casi no toca el dominio. Cambia el modelo en tres sitios: el registro de supresiones por módulo (R10), el
comando de la carga inicial (R7) y el esquema público `Compatibility` (R11). El resto son artefactos de operación
(copias, versiones desplegadas), descritos en [contracts/operations.md](./contracts/operations.md).

## 1. Registro de supresiones

Finalidad única: volver a aplicar, al restaurar una copia, las supresiones pedidas después de su fecha (FR-009). Solo
identificadores y fechas; ni nombres ni valores. Retención: 35 días, atada a la instantánea más antigua: `neocat-backup` usa `forget --keep-within-daily 30d` y avisa
si queda una instantánea de 31 días o más, así ninguna copia es más antigua que el registro conservado; cada
módulo purga lo suyo con un trabajo programado como `AccessDataPurgeJob`.

### `identity_erasure` (módulo `identity`, `V7__identity_erasure.sql`)

| Columna | Tipo | Regla | Justificación |
|---|---|---|---|
| `id` | `uuid` PK | generado | identidad de la fila |
| `user_id` | `uuid` not null | id del usuario borrado; sin FK (el usuario ya no existe) | qué borrar en la copia restaurada |
| `erased_at` | `timestamptz` not null | instante de la transacción de borrado | comparar con la fecha de la copia y purgar |

Índice por `erased_at`. Escribe solo `identity` (`DeleteUserService` y `IdentityApi.deleteUser`), en la misma
transacción que el borrado.

### `community_erasure` (módulo `community`, `V8__community_erasure.sql`)

| Columna | Tipo | Regla | Justificación |
|---|---|---|---|
| `id` | `uuid` PK | generado | identidad de la fila |
| `member_id` | `uuid` not null | id del hermano; sin FK | sujeto de la supresión |
| `field` | `varchar(16)` null | `MOBILE`, `LANDLINE`, `EMAIL` o `null` = hermano entero | retirar consentimiento borra solo un campo (003 R7) |
| `erased_at` | `timestamptz` not null | instante de la transacción | comparar y purgar |

Índice por `erased_at`. Escribe solo `community`: borrar hermano (`field = null`) y cada cambio de contacto que deja
un campo sin valor por retirada del consentimiento (`field` = ese campo), en la misma transacción.

### Puertos

- `identity.application.port.out.ErasureRecords`: `recordUser(UserId, Instant)`, `purgeBefore(Instant)`.
- `community.application.port.out.ErasureRecords`: `recordMember(MemberId, Instant)`,
  `recordContactField(MemberId, ContactField, Instant)`, `purgeBefore(Instant)`.
- Casos de uso de purga: `PurgeExpiredErasures` en cada módulo, disparados desde su adaptador programado.

### Validación

- Un borrado sin su fila en el registro es un fallo de test (aplicación, sin Spring; integración, Testcontainers).
- La purga elimina solo filas con `erased_at < now - 35 días`.
- Ningún log contiene `user_id` ni `member_id` del registro (test de logs existente ampliado).

## 2. Carga inicial (R7)

| Elemento | Antes | Después |
|---|---|---|
| `BootstrapCommand` | `communityNumbers`, `administratorName`, `administratorCommunity` | `administratorName`, `administratorCommunity` |
| `BootstrapOutcome` | `createdCommunities`, `Optional<CreatedAdministrator>` | sustituido por `CreatedAdministrator` |
| `IdentityProperties.Bootstrap` | `enabled`, `communities`, `administratorName`, `administratorCommunity` | `enabled`, `administratorName`, `administratorCommunity` |
| Ya hay administrador | asegura comunidades y no crea usuario | `AdministratorAlreadyExistsException` antes de cualquier escritura |
| Concurrencia | sin bloqueo | `UserRepository.lockForBootstrap()` (bloqueo consultivo de transacción) antes de comprobar |

Transiciones: `sin administrador` → (carga inicial) → `con administrador`; desde `con administrador`, la carga inicial
aborta sin cambios. Comunidades creadas en producción por la carga inicial: exactamente una.

## 3. Restauración: aplicación de supresiones

Entrada: base restaurada `R` con fecha de copia `T` (cabecera del volcado, `pg_restore -l`) y fuente `S` (producción
si existe; si no, la copia más reciente). Se aplican, en orden de `erased_at`, las filas de `S` con `erased_at > T`:

| Registro | Acción en `R` | Si choca con una FK |
|---|---|---|
| `identity_erasure(user_id)` | desvincula el hermano, pone a `null` `event.created_by`, `booking_request.requested_by` y `booking_status_change.changed_by` (lo mismo que hacen en producción los listeners `UserDeleted` de `agenda` y `spaces`), borra el usuario y sus sesiones | desvincula el hermano y reintenta |
| `community_erasure(member_id, null)` | borra el hermano (cascada de consentimientos) | anonimiza: nombre fijo «Suprimido», apellidos y contacto a `null` |
| `community_erasure(member_id, field)` | pone a `null` ese campo | — |

Las filas aplicadas se copian a `R` para que una restauración posterior desde `R` las respete. El informe lista ids y
acciones, nunca nombres.

## 4. Esquema público `Compatibility` (R11)

| Campo | Tipo | Regla | Origen |
|---|---|---|---|
| `minimumAppVersion` | `AppVersion` (`major.minor.patch`) | obligatoria | `service.minimum-app-version` |
| `serviceRevision` | `string` `^[0-9a-f]{7,40}$` | obligatoria | `SERVICE_REVISION` de la imagen (obligatorio en el build); fuera de imagen, `0000000` |

Cliente: `appVersion < minimumAppVersion` (comparación numérica por componentes) → pantalla de actualización
obligatoria. Sin datos personales.

## 5. Artefactos de operación (sin tabla)

| Entidad de la spec | Representación | Identidad | Caducidad |
|---|---|---|---|
| Versión desplegada | `BACKEND_IMAGE` y `WEB_IMAGE` en `state/current.env`; `serviceRevision` en `/api/compatibility` | commit de `master` | hasta el siguiente despliegue; la anterior, etiquetada `:previous`, en `previous.env` |
| Copia de seguridad | instantánea `restic` (`neocatecumenal.dump`) en `$NEOCAT_BACKUP_DIR/restic`, partición aparte | fecha | 30 días por calendario |
| Build móvil | build de EAS con `version` y `buildNumber`/`versionCode` | `version` + número | la gestionan las tiendas |
| Actualización OTA | EAS Update en el canal `production` | id de EAS | hasta la siguiente |

## Migraciones

| Fichero | Módulo | Contenido | Compatible con la versión anterior |
|---|---|---|---|
| `V7__identity_erasure.sql` | `identity` | tabla e índice | sí, solo añade |
| `V8__community_erasure.sql` | `community` | tabla e índice | sí, solo añade |
