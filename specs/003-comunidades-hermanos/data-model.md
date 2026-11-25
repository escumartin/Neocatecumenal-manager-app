# Data model: Comunidades y hermanos

**Feature**: `003-comunidades-hermanos` | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

Claves internas UUID. El número de comunidad es un atributo de negocio con unicidad. Todo vive en el módulo
`community`; `identity` conserva su proyección `identity_community`, que solo escribe `community` a través de
`CommunityCatalog` (R1). Ninguna tabla se comparte entre módulos.

## Dominio (`community.domain`, Java puro)

### Value objects

| Tipo | Contenido | Invariantes |
|---|---|---|
| `CommunityId` | `UUID value` | no nulo |
| `MemberId` | `UUID value` | no nulo |
| `UserId` | `UUID value` | no nulo; id de un usuario de `identity`, sin entidad |
| `CommunityNumber` | `int value` | > 0 (FR-002) |
| `CommunityName` | `String value` | NFC, recortado, 1-100; vacío → ausente (FR-003) |
| `PersonName` | `String givenName`, `String surnames?` | NFC, recortados; nombre 1-100 obligatorio, apellidos 1-100 o ausentes (FR-009, US3-2) |
| `PhoneNumber` | `String value` | normalizado sin espacios, puntos, guiones ni paréntesis; `^[6-9][0-9]{8}$` o `^\+[1-9][0-9]{1,14}$` (FR-010) |
| `EmailAddress` | `String value` | recortado, ≤ 254, sintaxis WHATWG con punto en el dominio (FR-010) |
| `ContactField` | `MOBILE`, `LANDLINE`, `EMAIL` | únicos campos de contacto (FR-010) |
| `ConsentDecision` | `GRANTED`, `WITHDRAWN` | — |
| `ConsentChannel` | `APP`, `VERBAL`, `WRITTEN` | `APP` solo si decide el propio hermano (FR-026) |
| `RecorderRole` | `MEMBER`, `MODERATOR`, `ADMINISTRATOR` | `MEMBER` ⇔ canal `APP` |
| `CommunityStatus` | `ACTIVE`, `DEACTIVATED` | — |
| `ActorRole` | `REGISTERED`, `MODERATOR`, `ADMINISTRATOR` | copia local de `identity.Role` |
| `Actor` | `UserId id`, `ActorRole role`, `CommunityId communityId` | quien ejecuta el caso de uso |
| `LinkedUser` | `UserId id`, `ActorRole role`, `CommunityId communityId` | vista local del usuario para validar el vínculo |

### Agregado `Community`

| Campo | Tipo | Restricciones |
|---|---|---|
| `Community.id` | `CommunityId` | inmutable |
| `Community.number` | `CommunityNumber` | único (FR-002); solo el administrador lo cambia (FR-001) |
| `Community.name` | `CommunityName?` | opcional, no sustituye al número (FR-003) |
| `Community.status` | `CommunityStatus` | `ACTIVE` al crearse (FR-006) |
| `Community.responsibleMemberId` | `MemberId?` | vacío = «sin responsable»; una vez con valor, nunca vuelve a vacío (FR-006, FR-007) |
| `Community.version` | `long` | bloqueo optimista |

Comportamiento:

- `Community.create(id, number, name)`: `ACTIVE`, sin responsable.
- `renumber(number)`, `rename(name)`.
- `designateResponsible(Member member)`: exige `member.communityId == id` (`MEMBER_NOT_IN_COMMUNITY`); sustituye en una
  sola escritura. No existe operación para quitarlo.
- `hasResponsible()`, `isResponsible(MemberId)`.
- `deactivate(boolean privilegedUsersPresent, boolean confirmed)`: `COMMUNITY_HAS_PRIVILEGED_USERS` si hay usuarios con
  rol distinto de `REGISTERED` y no se confirmó (FR-005). `reactivate()`.
- `checkAcceptsNewMembers()`: `COMMUNITY_DEACTIVATED` si está desactivada (FR-005, FR-016).
- `checkDeletable(long members, long users, boolean hasHistory)`: `COMMUNITY_HAS_RESPONSIBLE` si tiene responsable;
  `COMMUNITY_NOT_EMPTY` si tiene hermanos o usuarios; `COMMUNITY_HAS_HISTORY` si hay reservas o eventos que la
  referencian (FR-004, R16).

Transiciones:

```text
            create
              │
              ▼
  ACTIVE, sin responsable ──designateResponsible──▶ ACTIVE, con responsable
      │      ▲                                          │      ▲
 deactivate reactivate                            deactivate reactivate
      ▼      │                                          ▼      │
 DEACTIVATED, sin responsable                  DEACTIVATED, con responsable
      │
   delete: sin responsable, sin hermanos, sin usuarios y sin reservas ni eventos (también desde DEACTIVATED, sin responsable)
```

### Agregado `Member`

| Campo | Tipo | Restricciones |
|---|---|---|
| `Member.id` | `MemberId` | inmutable |
| `Member.communityId` | `CommunityId` | una sola comunidad (FR-009); solo el administrador la cambia (FR-016) |
| `Member.name` | `PersonName` | independiente del nombre del usuario (FR-029) |
| `Member.contact` | `Map<ContactField, String>` | un valor solo si la última decisión de ese campo es `GRANTED` (FR-011) |
| `Member.consents` | `List<ConsentRecord>` | solo inserción; sin valores (FR-027) |
| `Member.linkedUserId` | `UserId?` | uno a uno (FR-028) |
| `Member.version` | `long` | bloqueo optimista |

`ConsentRecord`:

| Campo | Tipo | Restricciones |
|---|---|---|
| `ConsentRecord.id` | `UUID` | — |
| `ConsentRecord.field` | `ContactField` | uno por fila |
| `ConsentRecord.decision` | `ConsentDecision` | — |
| `ConsentRecord.channel` | `ConsentChannel` | `APP` si lo decide el hermano vinculado; `VERBAL`/`WRITTEN` en su nombre (FR-026) |
| `ConsentRecord.recordedBy` | `UserId` | quien lo registró; se expone como `{role, name?, deleted}` resolviendo el nombre con `IdentityApi.user` al leer; usuario borrado → `deleted = true`, sin nombre (R17) |
| `ConsentRecord.recorderRole` | `RecorderRole` | `MEMBER` ⇔ `APP` |
| `ConsentRecord.noticeGiven` | `boolean` | `true` obligatorio con `VERBAL`/`WRITTEN` (FR-022, US3-6) |
| `ConsentRecord.recordedAt` | `Instant` | reloj inyectado |

Comportamiento:

- `Member.register(id, community, name, contactChange?, consentContext?, now)`: `community.checkAcceptsNewMembers()`;
  si trae contacto, exige contexto en su nombre (`CONSENT_REQUIRED` / `NOTICE_NOT_GIVEN`).
- `rename(PersonName)`: nunca toca `identity` (US3-10, FR-015).
- `changeContact(ContactChange change, ConsentContext ctx)` con `ContactChange` = por campo `{shared, value?}`:
  - `shared` pasa de no a sí: fila `GRANTED` y guarda el valor (`REQUIRED` si falta).
  - `shared` pasa de sí a no: fila `WITHDRAWN` y valor `null` (FR-011, FR-019).
  - `shared` sigue en sí con valor distinto: rectificación, sin fila (FR-020).
  - Hermano vinculado: si `ctx` no es el propio usuario y algún `shared` cambia → `CONSENT_MANAGED_BY_MEMBER` (FR-015,
    FR-026). Hermano sin usuario: `ctx` debe ser `VERBAL`/`WRITTEN` con `noticeGiven = true`.
- `link(LinkedUser user)`: `USER_NOT_IN_COMMUNITY` si `user.communityId != communityId`; `MEMBER_ALREADY_LINKED` si ya
  tiene. `unlink()`.
- `transferTo(Community target)`: `target.checkAcceptsNewMembers()`; el servicio bloquea antes origen y destino en
  orden de id, relee ambas con el bloqueo tomado (destino activo), bloquea y relee el hermano y comprueba que sigue en el
  origen bloqueado (si no, reintento); el responsable se comprueba con la comunidad origen bloqueada
  (`RESPONSIBLE_REQUIRED`) (R15).

### Políticas de dominio

`MemberAccessPolicy` (FR-010, FR-012..FR-016, FR-018, FR-028):

| Acción | Registrado | Moderador | Administrador |
|---|---|---|---|
| Ver directorio / ficha | su comunidad, activa | su comunidad, activa | cualquiera, también desactivada |
| Alta, nombre, contacto en su nombre | no | su comunidad | cualquiera |
| Rectificar valor de un campo consentido de un vinculado | no | su comunidad | cualquiera |
| Cambiar qué comparte un vinculado | solo él mismo (`/api/me`) | no | no |
| Vincular | no | su comunidad, usuario `REGISTERED` | misma comunidad |
| Desvincular | no (ni el propio vinculado) | su comunidad, usuario `REGISTERED` | siempre |
| Trasladar | no | no | sí |
| Borrar | no | su comunidad; sin usuario o vinculado a `REGISTERED`; no el suyo | sí, con reglas de `identity` |
| Copia de datos | no | su comunidad | cualquiera |
| Gestionar comunidades y responsable | no | no | sí |

Denegaciones: comunidad ajena en el directorio → `FORBIDDEN`; hermano de otra comunidad → `NOT_FOUND` (US4-2).

`DirectoryOrder`: `Collator` español de fuerza primaria; clave `surnames ?? givenName`, después `givenName`, después
`id` (FR-009, US4-1).

## Aplicación (`community.application`)

- `port/in`: `CreateCommunity`, `UpdateCommunity`, `DeactivateCommunity`, `ReactivateCommunity`, `DeleteCommunity`,
  `DesignateResponsible`, `ListCommunities`, `GetCommunity`, `ListMembers`, `GetMember`, `CreateMember`, `RenameMember`,
  `ChangeMemberContact`, `ChangeOwnContact`, `GetOwnMember`, `TransferMember`, `LinkMember`, `UnlinkMember`,
  `DeleteMember`, `ExportMemberData`, `GuardUserLink` (cambio de comunidad y liberación antes del borrado),
  `ProvisionCommunity`.
- `port/out`: `CommunityRepository` (con `lockInIdOrder(CommunityId...)`), `MemberRepository` (con `lock`, `countIn`,
  `findByLinkedUser`), `UserDirectory` (sobre `IdentityApi`: `user`, `lockUser`, `usersOf`, `moveUser`, `deleteUser`),
  `IdentityCommunityCatalog` (sobre `CommunityCatalog`), `CommunityHistory` (sobre `SpacesApi` y `AgendaApi`),
  `ClockProvider`.
- Orden de bloqueos: `community` (por id) → `community_member` → `identity` en todos los casos de uso (R15).
- Servicios transaccionales; el `Actor` entra en el comando desde el adaptador REST.

## API pública de los módulos

Ver [contracts/community-module-api.md](./contracts/community-module-api.md).

## Persistencia (PostgreSQL, Flyway)

### `V5__identity_community_active.sql` (módulo `identity`)

```text
identity_community
  + active   boolean   not null  default true
```

`IdentityCommunity.active`: proyección de `Community.status`; `CreateUser` y `UpdateUser` la leen bajo el bloqueo de la
fila para rechazar altas y traslados a una comunidad desactivada (FR-005).

### `V6__community.sql` (módulo `community`)

```text
community
  id                     uuid          primary key
  number                 integer       not null  constraint uk_community_number unique  check (number > 0)
  name                   varchar(100)  check (name is null or length(btrim(name)) > 0)
  status                 varchar(16)   not null  check (status in ('ACTIVE','DEACTIVATED'))
  responsible_member_id  uuid
  version                bigint        not null

community_member
  id            uuid          primary key
  community_id  uuid          not null  references community(id)
  given_name    varchar(100)  not null  check (length(btrim(given_name)) > 0)
  surnames      varchar(100)  check (surnames is null or length(btrim(surnames)) > 0)
  mobile        varchar(16)   check (mobile is null or mobile ~ '^([6-9][0-9]{8}|\+[1-9][0-9]{1,14})$')
  landline      varchar(16)   check (landline is null or landline ~ '^([6-9][0-9]{8}|\+[1-9][0-9]{1,14})$')
  email         varchar(254)
  user_id       uuid          constraint uk_community_member_user unique
  version       bigint        not null
  constraint uk_community_member_id_community unique (id, community_id)
  índices: (community_id)

alter table community add constraint fk_community_responsible
  foreign key (responsible_member_id, id) references community_member (id, community_id)

community_contact_consent
  id                   uuid         primary key
  member_id            uuid         not null  references community_member(id) on delete cascade
  field                varchar(16)  not null  check (field in ('MOBILE','LANDLINE','EMAIL'))
  decision             varchar(16)  not null  check (decision in ('GRANTED','WITHDRAWN'))
  channel              varchar(16)  not null  check (channel in ('APP','VERBAL','WRITTEN'))
  recorded_by_user_id  uuid         not null
  recorder_role        varchar(16)  not null  check (recorder_role in ('MEMBER','MODERATOR','ADMINISTRATOR'))
  notice_given         boolean      not null
  recorded_at          timestamptz  not null
  check ((channel = 'APP') = (recorder_role = 'MEMBER'))
  check (channel = 'APP' or notice_given)
  índices: (member_id, field, recorded_at)

insert into community (id, number, status, version)
  select id, number, 'ACTIVE', 0 from identity_community
```

Restricciones por campo:

| Entidad.campo | Restricción | Origen |
|---|---|---|
| `Community.number` | `unique`, `> 0`; colisión → `422 COMMUNITY_NUMBER_TAKEN` en el campo `number` | FR-002, edge case de altas simultáneas |
| `Community.name` | opcional, ≤ 100, no en blanco | FR-003 |
| `Community.status` | `ACTIVE`/`DEACTIVATED` | FR-005 |
| `Community.responsibleMemberId` | FK compuesta a `(community_member.id, community_id)`, sin `on delete`/`on update` | FR-006, FR-007 |
| `Member.communityId` | FK a `community`, sin `on delete`: una comunidad con hermanos no se borra | FR-004, FR-009 |
| `Member.givenName` | obligatorio, 1-100 | FR-009, US3-2 |
| `Member.surnames` | opcional, 1-100 | FR-009 |
| `Member.mobile`, `Member.landline` | formato FR-010 en dominio y `check` | FR-010 |
| `Member.email` | ≤ 254 y sintaxis en dominio | FR-010 |
| `Member.contact` (los tres) | valor solo con último `GRANTED` del campo (invariante del agregado) | FR-011, SC-007 |
| `Member.linkedUserId` | `unique`; sin FK (otro módulo); misma comunidad comprobada en dominio | FR-023, FR-028 |
| `ConsentRecord.*` | sin valores; borrado en cascada con el hermano | FR-027, SC-008 |
| `ConsentRecord.noticeGiven` | `true` con `VERBAL`/`WRITTEN` | FR-022, FR-026 |
| `IdentityCommunity.active` | proyección de `Community.status` | FR-005 |

Notas:

- La FK `identity_user.community_id → identity_community` sigue siendo interna de `identity` y es la red de seguridad del
  borrado de comunidades con usuarios (`23503` → `409 COMMUNITY_NOT_EMPTY`).
- `recorded_by_user_id` y `user_id` no llevan FK: son ids de `identity`. La FK `user_id → identity_user on delete set
  null` se descartó (R15); la integridad la garantizan el guardián y el orden de bloqueos.
- `booking_request.community_id` y `event.community_id` no llevan FK; una comunidad con historial no se borra (R16).
- Sin `created_at` ni `updated_at` en `community_member`: ningún requisito los justifica.

## Justificación de cada dato personal (FR-010, FR-022, principio VIII)

| Dato | Para qué | Base legal | Retención |
|---|---|---|---|
| `given_name`, `surnames` | reconocer al hermano en el directorio y en la gestión | art. 9.2.d y 6.1.f RGPD (FR-022) | mientras exista el hermano |
| `community_id` | pertenencia y permisos | ídem | ídem |
| `mobile`, `landline`, `email` | que los hermanos de su comunidad lo localicen | consentimiento por campo | hasta retirada o borrado |
| `user_id` | vínculo con su usuario | art. 6.1.f | hasta desvincular o borrar |
| `community_contact_consent.*` | acreditar el consentimiento y sus retiradas | art. 7.1 RGPD | mientras exista el hermano (FR-027) |
