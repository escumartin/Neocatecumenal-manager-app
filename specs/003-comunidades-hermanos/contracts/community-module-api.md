# Contrato: APIs de módulo entre `community` e `identity`

**Feature**: `003-comunidades-hermanos` | **Decisión**: [research.md](../research.md) R1, R4, R5

Dependencias en un solo sentido: `community → identity`, y `community → spaces`/`agenda` solo para consultar
historial (R16). `identity` no importa ningún tipo de `community`; lo que
necesita de él lo pide con interfaces de extensión que define en su raíz y que `community` implementa.
`ApplicationModules.verify()` lo comprueba.

## Cambios en la raíz de `identity`

### Tipos nuevos

| Tipo | Forma | Uso |
|---|---|---|
| `UserRef` | `record(UUID id, String name, Role role, UUID communityId)` | usuario para validar vínculos, avisos y desactivación |
| `IdentityRuleViolation` | `RuntimeException` con `String code()` | `FORBIDDEN`, `SELF_ACTION_FORBIDDEN`, `LAST_MODERATOR`, `LAST_ADMINISTRATOR`, `COMMUNITY_DEACTIVATED`, `COMMUNITY_HAS_USERS` |
| `StaleLockOrderException` | `RuntimeException` | la relectura bajo bloqueo no coincide con lo bloqueado (R15). La lanzan `UserLinkGuard` (implementación de `community`) y `DeleteUser`/`UpdateUser` de `identity`; la capturan y reintentan los adaptadores REST de `identity` y `community` con `TransactionRetry` (máximo 3 llamadas, después `409 CONCURRENT_MODIFICATION`) |
| `CommunityCatalog` | interfaz, implementada por `identity` | único camino para escribir la proyección `identity_community` |
| `UserLinkGuard` | interfaz de extensión, implementada por `community` | FR-030 y desvínculo antes del borrado (R15) |
| `CommunityProvisioner` | interfaz de extensión, implementada por `community` | carga inicial (R5) |

`CommunityRef` pasa a `record(UUID id, int number, boolean active)`.

### `IdentityApi` (métodos nuevos)

| Método | Semántica |
|---|---|
| `Optional<UserRef> user(UUID id)` | lectura sin control de actor; vacío si no existe |
| `Optional<UserRef> lockUser(UUID id)` | como `user`, con `SELECT ... FOR SHARE` de `identity_user` en la transacción en curso; para vincular (R15) |
| `List<UserRef> usersOf(UUID communityId)` | todos los usuarios de la comunidad, ordenados por nombre |
| `void moveUser(UUID userId, UUID communityId)` | ejecuta `UpdateUser` con el actor de la petición y solo cambia la comunidad: aplica `UserManagementPolicy` (solo administrador, sin acción propia, último moderador), comunidad activa y el guardián. Lanza `IdentityRuleViolation` |
| `void deleteUser(UUID userId)` | ejecuta `DeleteUser` con el actor de la petición (002 FR-010, FR-018), cierra sesiones y publica `UserDeleted`. Lanza `IdentityRuleViolation` |

Ambas órdenes se unen a la transacción en curso: si fallan, `community` no confirma nada.

### `CommunityCatalog`

| Método | Semántica |
|---|---|
| `void register(UUID id, int number)` | inserta la proyección activa |
| `void renumber(UUID id, int number)` | — |
| `void setActive(UUID id, boolean active)` | — |
| `void remove(UUID id)` | `IdentityRuleViolation("COMMUNITY_HAS_USERS")` si la FK de usuarios lo impide |

### `UserLinkGuard`

| Método | Semántica |
|---|---|
| `boolean allowsCommunityChange(UUID userId, UUID currentCommunityId, UUID targetCommunityId)` | bloquea las filas `community` de origen y destino por id; `true` si el usuario no está vinculado o su hermano ya está en `targetCommunityId` |
| `void releaseBeforeDeletion(UUID userId, UUID currentCommunityId)` | lee sin bloqueo el hermano vinculado; bloquea por id la fila `community` del usuario y la de ese hermano; si la lectura previa no encontró hermano, con la comunidad del usuario ya bloqueada vuelve a buscarlo por `user_id` (un vínculo nuevo exige ese mismo bloqueo, así que no puede aparecer otro después) y, si ahora existe en otra comunidad, `StaleLockOrderException`; bloquea la fila del hermano (`FOR UPDATE`) y relee su comunidad; si no es una de las bloqueadas, lanza `StaleLockOrderException` (reintento de toda la transacción, máximo 3, después `409 CONCURRENT_MODIFICATION`); si coincide, lo desvincula |

`UpdateUser` (si cambia la comunidad) y `DeleteUser` lo llaman como primer paso, antes de `LockingMembershipCounts` y de
cualquier otro bloqueo de `identity`. Al volver, con la comunidad bloqueada, releen el usuario con `FOR UPDATE`; si su
comunidad ya no es la que se pasó al guardián, `StaleLockOrderException` y reintento; si coincide, políticas y
recuentos usan la comunidad releída (regla general de R15); `false` → `409 USER_LINKED_TO_MEMBER`. Sin implementación registrada (tests de
`identity` aislado) se usa una que permite y no hace nada.

### `CommunityProvisioner`

| Método | Semántica |
|---|---|
| `UUID ensure(int number)` | devuelve el id de la comunidad con ese número, creándola (y su proyección) si no existe |

## Raíz de `community`

Paquete `io.github.escumartin.neocatecumenal.community`. Hoy no exporta tipos: ningún otro módulo lo consume. Solo
contiene `package-info.java` con `allowedDependencies = {"identity", "spaces", "agenda", "generated"}`. Sin eventos publicados mientras no
haya consumidor.

## Cambios en `spaces` y `agenda`

| Tipo | Método | Uso |
|---|---|---|
| `spaces.SpacesApi` | `boolean hasBookingRequestsFor(UUID communityId)` | impedir borrar una comunidad con historial (R16) |
| `agenda.AgendaApi` (nuevo en la raíz de `agenda`) | `boolean hasEventsFor(UUID communityId)` | ídem |

## Eventos consumidos

Ninguno. `community` no escucha `UserDeleted`: el desvínculo ocurre antes del borrado, dentro de
`UserLinkGuard.releaseBeforeDeletion`, para respetar el orden de bloqueos (R15). El registro persistente de
publicaciones que 002 R9 dejó pendiente no hace falta.

## Orden de bloqueos

Global, para todos los caminos (tabla completa en [research.md](../research.md) R15):

1. filas `community`, en orden ascendente de id;
2. filas `community_member`;
3. filas de `identity` (`identity_user`, administradores, `identity_community`).

`identity` llama a `UserLinkGuard` antes de cualquier bloqueo propio en `UpdateUser` y `DeleteUser`; las órdenes
`moveUser` y `deleteUser` que lanza `community` llegan con los niveles 1 y 2 ya tomados, y el guardián los reutiliza en
la misma transacción.
