# Contrato: API pública del módulo `identity`

**Feature**: `002-identity-access` | **Decisión**: [research.md](../research.md) R8, R9

Paquete raíz `io.github.escumartin.neocatecumenal.identity`. Es lo único que otros módulos pueden usar; `domain`,
`application` e `infrastructure` quedan internos y lo comprueba `ApplicationModules.verify()`.

## Tipos exportados

| Tipo | Forma | Uso |
|---|---|---|
| `IdentityApi` | interfaz | consultas de otros módulos |
| `CurrentUser` | `record(UUID userId, int accessId, String name, Role role, UUID communityId)` | usuario de la petición en curso (FR-004) |
| `Role` | `enum { REGISTERED, MODERATOR, ADMINISTRATOR }` con `boolean includes(Role required)` | herencia de FR-005 |
| `CommunityRef` | `record(UUID id, int number)` | referencia de comunidad (FR-025) |
| `UserDeleted` | `record(UUID userId)` | evento publicado tras el commit del borrado (FR-023) |

Las rutas sin sesión (`POST /api/sessions`, `GET /api/health`) son `permitAll` fijo en la configuración de seguridad de
`identity`; no hay extensión pública para que otros módulos declaren las suyas (R3).

## `IdentityApi`

| Método | Devuelve | Semántica |
|---|---|---|
| `Optional<CurrentUser> currentUser()` | usuario actual | vacío si no hay sesión, si la sesión ha caducado o si tiene pasos pendientes (aviso o contraseña). Rol y comunidad leídos de la base en esta petición (FR-008) |
| `List<CommunityRef> communities()` | todas, ordenadas por número | solo lectura (FR-025) |
| `Optional<CommunityRef> community(UUID id)` | una comunidad | para validar el `communityId` que elige un administrador (FR-007) |

Fuera de una petición HTTP autenticada, `currentUser()` devuelve vacío.

## Uso esperado por 001

- `spaces.infrastructure.in.rest.CurrentActorProvider` y el de `agenda` se implementan llamando a
  `IdentityApi.currentUser()` y mapeando a su `Actor` local; vacío → `401`.
- `Role.REGISTERED` no opera en Reservas: el adaptador de 001 lo traduce a `403`.
- El administrador elige comunidad con `GET /api/communities` en el cliente; el backend de 001 valida el UUID con
  `IdentityApi.community(id)`.

## Eventos

| Evento | Cuándo | Consumidores previstos |
|---|---|---|
| `UserDeleted` | tras el commit del borrado definitivo | «Comunidades y hermanos» (borrar contacto). `spaces` y `agenda` no lo necesitan: solo guardan el UUID, que deja de enlazar con datos personales |

Se publican con `ApplicationEventPublisher`, sin registro persistente de publicaciones, y se consumen con
`@ApplicationModuleListener`. Pendiente para cuando exista «Comunidades y hermanos»: añadir el registro persistente de
Spring Modulith (`spring-modulith-starter-jpa` y su migración) para que el evento no se pierda si el proceso cae tras el
commit (R9).
