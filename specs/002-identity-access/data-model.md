# Data model: Identidad y acceso

**Feature**: `002-identity-access` | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

Claves internas UUID. El identificador de acceso y el número de comunidad son atributos de negocio con unicidad. Todo
vive en el módulo `identity`; ninguna tabla se comparte con otros módulos.

## Dominio (`identity.domain`, Java puro)

### Value objects

| Tipo | Contenido | Invariantes |
|---|---|---|
| `UserId` | `UUID value` | no nulo |
| `CommunityId` | `UUID value` | no nulo |
| `AccessId` | `int value` | 100000 ≤ value ≤ 999999 |
| `CommunityNumber` | `int value` | > 0 |
| `DisplayName` | `String value` | NFC, recortado, 1-100 caracteres |
| `UserRole` | `REGISTERED`, `MODERATOR`, `ADMINISTRATOR` | `includes(other)` según herencia (FR-005) |
| `PlainPassword` | `String value` | NFC; nunca se persiste ni se imprime (`toString` enmascarado) |
| `PasswordHash` | `String value` | opaco, con prefijo del algoritmo (`{argon2}`) |
| `PrivacyConsent` | `String noticeVersion`, `Instant acceptedAt` | ambos presentes o el consentimiento no existe |
| `Actor` | `UserId id`, `UserRole role`, `CommunityId communityId` | quien ejecuta un caso de uso |

### Agregado `User`

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `UserId` | inmutable |
| `accessId` | `AccessId` | generado en el alta, inmutable (FR-012) |
| `name` | `DisplayName` | editable por moderador (registrados de su comunidad) o administrador |
| `role` | `UserRole` | solo el administrador lo cambia (FR-010) |
| `communityId` | `CommunityId` | solo el administrador lo cambia |
| `passwordHash` | `PasswordHash` | FR-015 |
| `passwordChangeRequired` | `boolean` | `true` tras alta o reasignación (FR-014, FR-022) |
| `privacyConsent` | `PrivacyConsent?` | FR-020 |
| `version` | `long` | bloqueo optimista |

Comportamiento:

- `User.register(id, accessId, name, role, communityId, initialHash)`: `passwordChangeRequired = true`, sin
  consentimiento.
- `rename(name)`, `changeRole(role)`, `moveTo(communityId)`.
- `resetPassword(initialHash)`: `passwordChangeRequired = true`.
- `changeOwnPassword(newHash)`: `passwordChangeRequired = false`.
- `acceptPrivacyNotice(version, now)`.
- `pendingSteps(currentNoticeVersion)`: `PRIVACY_NOTICE` si no hay consentimiento o su versión no es la vigente;
  `PASSWORD_CHANGE` si `passwordChangeRequired`. Orden fijo: primero el aviso.

### Políticas de dominio

`PasswordPolicy.check(PlainPassword, AccessId, CommonPasswords)` devuelve los motivos de rechazo:

| Código | Regla |
|---|---|
| `PASSWORD_TOO_SHORT` | < 8 *code points* |
| `PASSWORD_TOO_LONG` | > 64 *code points* |
| `PASSWORD_TOO_COMMON` | en la lista, sin distinguir mayúsculas |
| `PASSWORD_CONTAINS_ACCESS_ID` | contiene los 6 dígitos del identificador |

`CommonPasswords` es una interfaz de dominio; su implementación carga el fichero de recursos.

`UserManagementPolicy` (actor, objetivo, cambio):

| Acción | Administrador | Moderador | Registrado |
|---|---|---|---|
| Listar usuarios | cualquier comunidad (la elegida) | su comunidad | no |
| Crear | cualquier rol y comunidad | solo `REGISTERED` en su comunidad | no |
| Editar nombre | cualquiera | `REGISTERED` de su comunidad | no |
| Cambiar rol o comunidad | cualquiera salvo a sí mismo | no | no |
| Reasignar contraseña inicial | cualquiera salvo a sí mismo | `REGISTERED` de su comunidad | no |
| Borrar | cualquiera salvo a sí mismo | `REGISTERED` de su comunidad | no |

Invariantes de conjunto (las comprueba el servicio con las filas bloqueadas, ver R10):

- `LAST_ADMINISTRATOR`: un cambio de rol o un borrado no puede dejar 0 usuarios `ADMINISTRATOR`.
- `LAST_MODERATOR`: un borrado, un cambio de rol o un cambio de comunidad no puede dejar 0 usuarios `MODERATOR` en una
  comunidad que tenía al menos uno. Cuentan solo los de rol `MODERATOR`.
- `SELF_ACTION_FORBIDDEN` (409 en las tres acciones): nadie se borra, cambia su rol o comunidad, ni reasigna su contraseña por la gestión de
  usuarios.

### `Community` (referencia de solo lectura)

| Campo | Tipo |
|---|---|
| `id` | `CommunityId` |
| `number` | `CommunityNumber`, único |

Sin responsable ni otros datos (pertenecen a «Comunidades y hermanos»).

### `Session`

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `UUID` | |
| `userId` | `UserId` | |
| `tokenHash` | `byte[32]` | SHA-256 del token; el token en claro solo existe en la respuesta del login |
| `createdAt` | `Instant` | |
| `lastUsedAt` | `Instant` | |

- `isValid(now)`: `now < lastUsedAt + 30 d` y `now < createdAt + 90 d` (FR-026).
- `touch(now)`: renueva si `now - lastUsedAt ≥ 5 min`.

Termina por: cierre de sesión, caducidad, borrado del usuario (cascada), rechazo del aviso, reasignación de contraseña
(todas) o cambio de la propia (todas salvo la actual).

### `LoginThrottle` (reglas de FR-003)

Reglas puras con el reloj como argumento. Constantes: `PAIR_MAX_CONSECUTIVE = 5`, `IP_MAX_IN_WINDOW = 20`,
`WINDOW = 15 min`, `BLOCK = 15 min`.

El intento se cuenta como fallo **antes** de verificar la contraseña, en la misma transacción y con las filas de la IP
y del par bloqueadas (R7); si la contraseña es correcta, la reserva se deshace.

| Operación | Transición |
|---|---|
| `reserve(ipState, pairState, now)` | si `blockedUntil > now` en alguna, `Rejected(retryAfter = max(blockedUntil) - now)` sin contar. Si no, `Reserved`: par `consecutive + 1` (vuelve a 1 si el último fallo tiene ≥ 15 min) y `lastFailureAt = now`; IP añade `now` a `recentFailures` y descarta los instantes fuera de `(now - 15 min, now]`. Al llegar a 5 (par) o 20 (IP), `blockedUntil = now + 15 min` |
| Verificación fallida | se queda el estado reservado |
| Verificación correcta | el par se borra; la IP vuelve al estado leído bajo bloqueo (sin el instante ni el bloqueo de este intento) |

Retención: un registro se puede purgar cuando `now ≥ max(lastFailureAt + 15 min, blockedUntil)`.

## Aplicación (`identity.application`)

- `port/in`: `LogIn`, `LogOut`, `AuthenticateSession`, `GetMyProfile`, `AcceptPrivacyNotice`, `RejectPrivacyNotice`,
  `ChangeOwnPassword`, `ListUsers`, `GetUser`, `CreateUser`, `UpdateUser`, `ResetUserPassword`, `DeleteUser`,
  `ListCommunities`, `BootstrapIdentity`.
- `port/out`: `UserRepository` (incluye `lockAdministrators()`, `lockCommunity(id)`, `countModerators(communityId)`),
  `CommunityRepository`, `SessionRepository` (`deleteAllOf(userId)`, `deleteAllOfExcept(userId, sessionId)`,
  `purgeExpired(now)`), `LoginThrottleStore` (`lockIp(ip)` y `lockPair(accessIdentifier, ip)` aseguran la fila y la
  bloquean con `FOR UPDATE`, siempre IP antes que par; `save`, `deletePair`, `purgeExpired(now)`), `PasswordHasher`
  (`hash`, `matches`, `matchesDummy`),
  `CommonPasswordsProvider`, `AccessIdGenerator`, `InitialPasswordGenerator`, `SessionTokenIssuer` (genera token y
  hash), `CurrentSessionContext`, `IdentityEventPublisher`, `PrivacyNoticeSettings` (versión vigente).
- `service/`: implementaciones transaccionales e `IdentityApiService` (implementa `IdentityApi`).

## API pública del módulo (raíz `identity`)

Ver [contracts/identity-module-api.md](./contracts/identity-module-api.md).

## Persistencia (PostgreSQL, Flyway)

Migración nueva: `V2__identity.sql`. Como 002 se implementa antes que 001, las de 001 pasan a `V3__spaces.sql` y
`V4__agenda.sql`. Sin tabla `event_publication`: `UserDeleted` se publica sin registro persistente (R9).

```text
identity_community
  id          uuid      primary key
  number      integer   not null unique  check (number > 0)

identity_user
  id                           uuid         primary key
  access_id                    integer      not null  constraint uk_identity_user_access_id unique
                                            check (access_id between 100000 and 999999)
  name                         varchar(100) not null
  role                         varchar(16)  not null  check (role in ('REGISTERED','MODERATOR','ADMINISTRATOR'))
  community_id                 uuid         not null  references identity_community(id)
  password_hash                varchar(255) not null
  password_change_required     boolean      not null
  failed_password_changes      smallint     not null  default 0
  privacy_notice_version       varchar(20)
  privacy_notice_accepted_at   timestamptz
  version                      bigint       not null
  check ((privacy_notice_version is null) = (privacy_notice_accepted_at is null))
  índices: (community_id, role), (role)

identity_session
  id              uuid         primary key
  user_id         uuid         not null references identity_user(id) on delete cascade
  token_hash      bytea        not null unique
  created_at      timestamptz  not null
  last_used_at    timestamptz  not null
  índices: (user_id)

identity_login_pair_throttle
  access_id             integer      not null
  ip                    inet         not null
  consecutive_failures  smallint     not null
  last_failure_at       timestamptz  not null
  blocked_until         timestamptz
  primary key (access_id, ip)

identity_login_ip_throttle
  ip                inet           primary key
  recent_failures   timestamptz[]  not null  check (cardinality(recent_failures) <= 20)
  blocked_until     timestamptz
```

Notas:

- `identity_login_pair_throttle.access_id` no lleva FK: cuenta también identificadores inexistentes (FR-003).
- `identity_login_ip_throttle` es la fila contadora de la IP y, a la vez, el cerrojo que serializa sus intentos; los
  instantes (como mucho 20) mantienen exacta la ventana deslizante de 15 minutos.
- La FK `identity_user.community_id` es interna del módulo (la tabla de comunidades es de `identity` mientras
  «Comunidades y hermanos» no exista).
- No hay `created_at` ni `updated_at` en `identity_user`: ningún requisito los justifica (FR-024).

## Justificación de cada dato personal (FR-028)

| Dato | Para qué | Retención |
|---|---|---|
| `access_id`, `password_hash` | acceso | mientras exista el usuario |
| `name` | que el moderador reconozca al hermano | ídem |
| `role`, `community_id` | permisos | ídem |
| `privacy_notice_version`, `privacy_notice_accepted_at` | acreditar que se informó | ídem |
| `identity_session.*` | mantener la sesión | hasta cierre o caducidad (≤ 90 d) |
| `ip`, `access_id` de los fallos | frenar la fuerza bruta (art. 6.1.f) | hasta `max(último fallo + 15 min, fin del bloqueo)` |
