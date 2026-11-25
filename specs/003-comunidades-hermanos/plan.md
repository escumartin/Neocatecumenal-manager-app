# Implementation Plan: Comunidades y hermanos

**Branch**: `feat/003-comunidades-hermanos` | **Date**: 2026-11-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-comunidades-hermanos/spec.md`

## Summary

El administrador gestiona las comunidades (alta, número, nombre, desactivar, reactivar y borrar si están vacías) y
designa su responsable, que una vez designado nunca falta. Cada comunidad tiene sus hermanos con nombre, apellidos
opcionales y móvil, fijo y correo solo con consentimiento por campo. El registrado consulta el directorio de su
comunidad y edita su propio contacto; el moderador gestiona los hermanos de su comunidad, registra el consentimiento en
nombre de quien no tiene usuario y los vincula con usuarios registrados. Supresión, rectificación y copia de datos.

Enfoque técnico:

- Módulo Spring Modulith nuevo `community`, dueño de comunidades, hermanos (`Member`), contacto y registro de
  consentimiento; depende de `identity` (API e interfaces de extensión) y, solo para consultar historial, de `spaces` y
  `agenda` (R16). `identity` conserva `identity_community` como
  proyección que solo escribe `community` por `CommunityCatalog`, en la misma transacción (R1).
- `identity` define dos interfaces de extensión que implementa `community`: `UserLinkGuard` (FR-030 y desvínculo antes
  de borrar un usuario) y `CommunityProvisioner` (carga inicial), y gana `IdentityApi.user`, `lockUser`, `usersOf`,
  `moveUser` y `deleteUser`, que
  reutilizan su `UserManagementPolicy` (último moderador, último administrador, sin acción propia) (R4, R5).
- Responsable reforzado en dominio, con FK compuesta y con bloqueo de la fila de la comunidad (R3).
- Consentimiento como registro de solo inserción por campo dentro del agregado `Member`; retirar borra el valor;
  borrado en cascada con el hermano (R7).
- Orden global de bloqueos `community` (por id) → `community_member` → `identity` en todos los caminos; lo leído antes
  de bloquear se relee con bloqueo y, si cambió, se reintenta la transacción (máx. 3); `identity` consulta el guardián
  antes de bloquear nada; sin listener de `UserDeleted` en `community` (R4, R15).
- Una comunidad con reservas o eventos no se borra (`409 COMMUNITY_HAS_HISTORY`), se desactiva (R16).
- Contrato primero: borrador en `contracts/community.openapi.yaml`, validado con Redocly.
- Cliente: pestaña nueva «Comunidad» (directorio), Gestión → Comunidades y Hermanos, Cuenta → Mis datos de contacto
  (R14).

## Technical Context

**Language/Version**: Java 25 (backend); TypeScript strict (cliente)

**Primary Dependencies**:

- Backend: Spring Boot 4.2, Spring Modulith 2.2, Spring Data JPA, Flyway, Bean Validation, Spring Security (de 002).
  Sin dependencias nuevas: sin `libphonenumber` (R6) ni `spring-modulith-starter-jpa` (R4).
- Generación: `openapi-generator-maven-plugin` (generador `spring`, `interfaceOnly`, `useTags`,
  `openApiNullable=false`); interfaces nuevas `CommunitiesApi` (ampliada), `MembersApi` y `MyMemberApi`.
- Cliente: Expo 58, Expo Router con `NativeTabs`, NativeWind, React Native Reusables, `openapi-fetch`,
  `openapi-typescript`; `Linking` y `Share` de React Native. Sin dependencias nuevas.

**Storage**: PostgreSQL 18; migraciones nuevas `V5__identity_community_active.sql` y `V6__community.sql` (R13). Claves
UUID.

**Testing**:

- JUnit 5 y AssertJ sin Spring: `Community`, `Member`, `PersonName`, `PhoneNumber`, `EmailAddress`,
  `MemberAccessPolicy`, `DirectoryOrder` y servicios con puertos falsos y reloj fijo. Test de propiedad: ninguna
  secuencia de cambios de contacto deja un valor sin último `GRANTED` (SC-007).
- Testcontainers PostgreSQL: FK compuesta del responsable, cascada del consentimiento, índice único del vínculo,
  número duplicado en paralelo (SC-006, edge cases), traslado y borrado con `identity` real, desvínculo al borrar un
  usuario y, con dos hilos, cada carrera de R15 (borrar usuario frente a trasladar su hermano, vincular frente a borrar
  usuario, vincular frente a cambiar de comunidad, trasladar frente a desactivar el destino, traslados cruzados), sin
  `40P01`.
- MockMvc con la cadena de seguridad real: matriz de permisos por rol y comunidad (SC-005), `404` para hermanos de otra
  comunidad, errores sin datos personales.
- `@ApplicationModuleTest` de `community` con `identity` y de `identity` con guardián y aprovisionador falsos.
- Log capturado sin nombres, teléfonos ni correos (SC-009).
- `ModularityTest` y `ArchitectureTest` existentes; `community` entra en ambos sin cambios en los tests.
- Cliente: `jest-expo` y Testing Library para hooks, `lib/members` y pantallas (roles, etiquetas, estados).

**Target Platform**: servidor Linux (JVM) y cliente Expo web, iOS y Android

**Project Type**: web-service + cliente móvil/web

**Performance Goals**: unas 25 comunidades y 1200 hermanos; directorio de ≤ 80 hermanos < 200 ms p95; SC-003 (teléfono
en < 30 s) con búsqueda local.

**Constraints**:

- Ningún dato de contacto sin consentimiento vigente del campo (FR-011, SC-007).
- Comunidad con responsable nunca sin él (FR-006, FR-007, SC-006).
- Sin ciclos entre módulos; orden global de bloqueos `community` (por id) → `community_member` → `identity` (R15).
- Nada personal en logs ni en `Problem.detail` (FR-021, SC-009).

**Scale/Scope**:

- Backend: 1 módulo nuevo (`community`), 19 operaciones HTTP nuevas y 1 ampliada; cambios acotados en `identity`
  (`IdentityApi`, `UpdateUser`, `CreateUser`, arranque, proyección).
- Cliente: 1 pestaña nueva y unas 11 pantallas: directorio, ficha, comunidades (lista, formulario, ficha y responsable),
  hermanos (lista, formulario, contacto, vínculo, traslado, copia) y mis datos de contacto.

## Dependencies

- **`002-identity-access`** (implementada): usuarios, roles, sesión, `IdentityApi`, `UserDeleted`, carga inicial. Esta
  feature amplía su API y le añade FR-030 (R4).
- **`001-reservas-salones`** (implementada): cambios acotados. Backend: `SpacesApi.hasBookingRequestsFor` y
  `AgendaApi.hasEventsFor` nuevos (R16); `IdentityApi.community(id)` incluye `active`. Cliente:
  `app/src/screens/bookings/pending-requests.tsx` resuelve números con `useCommunities` y pasa a pedir
  `includeDeactivated` para no perder los de comunidades desactivadas (R12, R14).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple el plan | Pre | Post |
|---|---|---|---|
| I. Dominio puro | `community.domain` sin Spring, JPA ni Jackson; reloj como argumento; `Collator` y expresiones regulares de Java estándar; validación en value objects | PASS | PASS |
| II. Puertos y adaptadores | puertos en `community.application.port.{in,out}`; `UserDirectory`, `IdentityCommunityCatalog` y `CommunityHistory` son puertos de salida implementados en `infrastructure` sobre `IdentityApi` y `CommunityCatalog`; el guardián y el aprovisionador son adaptadores de entrada en `infrastructure.in.identity`; `Actor` resuelto en el adaptador REST | PASS | PASS |
| III. Un módulo por contexto | `community` nuevo según `dominio.md`; `community → identity` solo por la raíz (`IdentityApi`, `UserRef`, `CommunityCatalog`, interfaces de extensión) y `community → spaces`/`agenda` solo por `SpacesApi`/`AgendaApi`; ni `identity`, ni `spaces`, ni `agenda` importan nada de `community` (sin ciclo); sin FK entre módulos (R15); cada tabla con un único dueño y escritor; la copia de `identity_community` en `V6` es una migración de un solo uso (R13) | PASS | PASS |
| IV. Tests primero | cada regla (responsable, consentimiento, vínculo, permisos, validación) nace de un test de dominio sin Spring; FK, cascada y concurrencia con Testcontainers; [quickstart.md](./quickstart.md) § 2 mapea cada escenario | PASS | PASS |
| V. Contrato primero | borrador `contracts/community.openapi.yaml` validado con Redocly; primera tarea: fusión según la regla de abajo y generación; nada generado se edita a mano; solo aditivo | PASS | PASS |
| VI. Separación en cliente y tokens | acceso a datos en `lib/api/{communities,members}-api.ts`, lógica en `lib/members/` y hooks; pantallas con componentes `ui/` y tokens de `design-system` | PASS | PASS |
| VII. Accesible para todas las edades | FR-024: texto ≥ 18 px, objetivos ≥ 48 px, AA en temas claro y oscuro, etiquetas en cada control y en las acciones de llamar o escribir, escalado de fuente al 200 %, movimiento reducido respetado (sin animaciones propias; transiciones nativas), estados con `StateView`; confirmaciones accesibles de borrado y desactivación; mensajes de validación con formato y ejemplo (SC-004) | PASS | PASS |
| VIII. Privacidad por diseño | solo nombre, apellidos opcionales y tres campos de contacto, cada uno justificado ([data-model.md](./data-model.md)); consentimiento por campo con registro sin valores; retirada borra el valor; acceso (`/api/me/member`, copia), rectificación y supresión con cascada al usuario registrado; directorio limitado a la comunidad; logs y errores sin datos personales con test; el registro de consentimiento muestra quién lo anotó (nombre o «usuario eliminado», R17) | PASS | PASS |
| IX. Higiene de código | sin comentarios; identificadores en inglés (`Member` por «hermano», R2); textos en `app/src/i18n/es`; errores como `code` estables | PASS | PASS |

Restricciones adicionales: stack conforme, sin dependencias nuevas; sin secretos; datos de prueba sintéticos.

Coherencia con `dominio.md`: cada comunidad con responsable obligatorio (con el estado explícito «sin responsable» hasta
designarlo, decidido en la spec); el moderador solo opera sobre su comunidad; el administrador elige comunidad; borrar
es definitivo y pide confirmación; la comunidad no se queda sin moderador (regla de `identity`, reutilizada). Se apartan
del glosario, por decisión registrada en la spec, la dirección y el sexo del hermano.

Desviaciones registradas (no son violaciones):

- `community` no escucha `UserDeleted`: el desvínculo se hace antes del borrado por `UserLinkGuard`, en la misma
  transacción, para respetar el orden de bloqueos (R4, R15). Cierra el pendiente de 002 R9 sin registro persistente.
- Hermano de otra comunidad → `404`, frente al `403` de 002 R13 para usuarios: lo pide US4-2 (R8).

**Resultado**: sin violaciones. No hace falta *Complexity Tracking*.

## Project Structure

### Documentation (this feature)

```text
specs/003-comunidades-hermanos/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── community.openapi.yaml
│   └── community-module-api.md
├── checklists/
│   └── requirements.md
└── tasks.md                # lo genera /speckit.tasks
```

### Source Code (repository root)

```text
api/
└── openapi.yaml                                  # se le fusiona contracts/community.openapi.yaml (0.3.0)

backend/src/main/java/io/github/escumartin/neocatecumenal/
├── identity/
│   ├── IdentityApi.java                          # + user, lockUser, usersOf, moveUser, deleteUser
│   ├── UserRef.java, IdentityRuleViolation.java, StaleLockOrderException.java  # nuevos
│   ├── CommunityRef.java                         # + active
│   ├── CommunityCatalog.java                     # nuevo, lo implementa identity
│   ├── UserLinkGuard.java                        # nuevo, lo implementa community
│   ├── CommunityProvisioner.java                 # nuevo, lo implementa community
│   ├── domain/Community.java                     # + active
│   ├── application/service/                      # Update/DeleteUser consultan el guardián antes de bloquear;
│   │                                             # Create/UpdateUser rechazan comunidad desactivada;
│   │                                             # IdentityApiService y CommunityCatalogService;
│   │                                             # BootstrapIdentity usa CommunityProvisioner
│   └── infrastructure/in/rest/CommunitiesController.java   # se borra: pasa a community
├── spaces/SpacesApi.java                         # + hasBookingRequestsFor
├── agenda/AgendaApi.java                         # nuevo: hasEventsFor
└── community/
    ├── package-info.java                         # allowedDependencies = {"identity", "spaces", "agenda", "generated"}
    ├── domain/                                   # Community, Member, ConsentRecord, CommunityId, MemberId, UserId,
    │                                             # CommunityNumber, CommunityName, PersonName, PhoneNumber,
    │                                             # EmailAddress, ContactField, ContactChange, ConsentContext,
    │                                             # ConsentDecision, ConsentChannel, RecorderRole, CommunityStatus,
    │                                             # Actor, ActorRole, LinkedUser, MemberAccessPolicy,
    │                                             # DirectoryOrder, CommunityDomainException
    ├── application/
    │   ├── port/in/                              # casos de uso de data-model.md § Aplicación
    │   ├── port/out/                             # CommunityRepository, MemberRepository, UserDirectory,
    │   │                                         # IdentityCommunityCatalog, CommunityHistory
    │   └── service/
    └── infrastructure/
        ├── in/rest/                              # CommunitiesController, MembersController, MyMemberController,
        │                                         # ActorResolver, ProblemDetails, mappers
        ├── in/identity/                          # UserLinkGuard y CommunityProvisioner adaptados
        ├── out/persistence/                      # entidades JPA, Spring Data, adaptadores, traducción de
        │                                         # 23505 y 23503 a códigos
        ├── out/identity/                         # UserDirectory e IdentityCommunityCatalog sobre la raíz de identity
        ├── out/history/                          # CommunityHistory sobre SpacesApi y AgendaApi
        └── config/

backend/src/main/resources/db/migration/
├── V5__identity_community_active.sql
└── V6__community.sql

backend/src/test/java/io/github/escumartin/neocatecumenal/community/{domain,application,infrastructure}/

app/src/
├── app/(app)/(tabs)/comunidad/{_layout,index,[memberId]}.tsx
├── app/(app)/(tabs)/gestion/comunidades/{index,nueva,[communityId],responsable}.tsx
├── app/(app)/(tabs)/gestion/hermanos/{index,nuevo,[memberId],contacto,vincular,trasladar,copia}.tsx
├── app/(app)/(tabs)/cuenta/contacto.tsx
├── lib/api/communities-api.ts                    # MODIFY: alta, edición, estado, responsable, includeDeactivated
├── lib/api/members-api.ts                        # CREATE
├── lib/members/{contact-validation,directory-search,data-export-text}.ts
├── lib/auth/navigation-permissions.ts            # MODIFY: + comunidad, canManageMembers, canManageCommunities
├── hooks/communities/use-communities.tsx         # MODIFY: opción includeDeactivated
├── hooks/communities/use-community*.tsx          # CREATE: ficha y acciones de administrador
├── hooks/members/                                # CREATE
├── screens/bookings/pending-requests.tsx         # MODIFY: includeDeactivated en la búsqueda de números
├── screens/{directory,communities,members}/      # CREATE
├── screens/account/                              # MODIFY: fila Mis datos de contacto; CREATE: contact.tsx
├── i18n/es/{communities,navigation}.ts           # MODIFY
└── i18n/es/members.ts                            # CREATE
```

**Structure Decision**: web-service en `backend/` con un módulo Modulith nuevo (`community`) más cliente Expo en
`app/`. Se mantiene la convención de `hexagonal-ddd`; `infrastructure/in/identity` agrupa las implementaciones de las
interfaces de extensión de `identity` (son adaptadores de entrada: `identity` llama a `community`) y
`infrastructure/out/identity` los puertos de salida hacia `identity`.

## Tabla de cambios

| Ruta | Acción | Motivo |
|---|---|---|
| `api/openapi.yaml` | MODIFY | fusión aditiva del borrador; 0.2.0 → 0.3.0 |
| `identity/IdentityApi.java`, `CommunityRef.java` | MODIFY | R4; `active` para FR-005 |
| `identity/{UserRef,IdentityRuleViolation,StaleLockOrderException,CommunityCatalog,UserLinkGuard,CommunityProvisioner}.java` | CREATE | R4, R5, R15; `StaleLockOrderException` en la raíz de `identity` porque la lanzan el guardián de `community` y `DeleteUser`/`UpdateUser` de `identity`, y `identity` no puede importar `community` |
| `identity/application/service/DeleteUserService.java:45-46` | MODIFY | antes de `findById`: guardián con la comunidad de la lectura previa; después, relee el usuario con `UserRepository.findByIdForUpdate` (`FOR UPDATE`; `lockUser` es `FOR SHARE` y solo para vincular); si su comunidad no es la bloqueada, `StaleLockOrderException`; `checkDeletion` y `LockingMembershipCounts` con la comunidad releída (R15) |
| `identity/application/service/{UpdateUser,CreateUser,BootstrapIdentity,IdentityApi}Service.java` | MODIFY | FR-030, FR-005, R5; guardián primero y relectura con bloqueo (R15); `CreateUser`/`UpdateUser` leen `active` bajo `lockCommunity` |
| `identity/application/service/CommunityCatalogService.java` | CREATE | implementa `CommunityCatalog` sobre la proyección (R1) |
| `identity/application/port/out/{UserRepository,CommunityRepository}.java` | MODIFY | `findByIdForUpdate`, `lockForShare` (para `lockUser`); `setActive`, `renumber`, `delete` con traducción de `23503` |
| `identity/domain/Community.java`; `identity/infrastructure/out/persistence/{CommunityJpaEntity,CommunityPersistenceAdapter,CommunitySpringDataRepository,UserSpringDataRepository,UserPersistenceAdapter}.java` | MODIFY | columna `active`, consultas `FOR UPDATE`/`FOR SHARE`, escritura de la proyección |
| `identity/infrastructure/in/rest/TransactionRetry.java` y `community/infrastructure/in/rest/TransactionRetry.java` | CREATE | componente pequeño de cada capa de adaptadores: los controladores de `identity` y `community` envuelven cada caso de uso de escritura; ante `StaleLockOrderException` reintenta la transacción, como mucho 3 llamadas, después `409 CONCURRENT_MODIFICATION` |
| `spaces/SpacesApi.java` y su servicio; `agenda/AgendaApi.java` y su servicio | MODIFY / CREATE | R16 |
| `identity/infrastructure/in/rest/CommunitiesController.java` | DELETE | la etiqueta `communities` pasa a `community` (R1) |
| `community/**`, `V5__identity_community_active.sql`, `V6__community.sql` | CREATE | módulo y migraciones nuevos |
| `app/src/lib/auth/navigation-permissions.ts`, `app/src/app/(app)/(tabs)/_layout.tsx` | MODIFY | pestaña Comunidad |
| `app/src/screens/management/index.tsx` | MODIFY | filas Comunidades y Hermanos |
| `app/src/screens/account/index.tsx` | MODIFY | fila Mis datos de contacto |
| `app/src/screens/users/edit.tsx` | MODIFY | traduce `USER_LINKED_TO_MEMBER` y `COMMUNITY_DEACTIVATED` |
| `app/src/lib/api/communities-api.ts`, `app/src/hooks/communities/use-communities.tsx` | MODIFY | se reutilizan y amplían |
| `app/src/i18n/es/communities.ts`, `app/src/i18n/es/navigation.ts` | MODIFY | textos nuevos |
| `app/src/screens/bookings/pending-requests.tsx` | MODIFY | búsqueda de números con `includeDeactivated` (001) |
| resto de `app/src/**` de la estructura marcado CREATE | CREATE | pantallas, hooks y `lib` nuevos |

## Regla de fusión del contrato

El borrador `contracts/community.openapi.yaml` se fusiona en `api/openapi.yaml` como primera tarea:

1. `servers`, `security` y `components.securitySchemes` se toman de `api/openapi.yaml`; las referencias externas del
   borrador (`../../../api/openapi.yaml#/components/...`) pasan a locales.
2. `x-merge.replaces`: la operación `GET /api/communities` y el esquema `Community` del borrador sustituyen a los
   existentes. Ambos son superconjuntos compatibles: un parámetro opcional y propiedades opcionales nuevas; `id` y
   `number` no cambian.
3. Enums `Problem.code` y `ValidationProblem.errors[].code`: unión con `x-merge.problemCodes` y
   `x-merge.validationCodes`, sin quitar ni renombrar ninguno.
4. `x-merge.identityOperationChanges`: se amplían las descripciones de `updateUser` (409 `USER_LINKED_TO_MEMBER`, 422
   `COMMUNITY_DEACTIVATED`) y `createUser` (422 `COMMUNITY_DEACTIVATED`); sus esquemas no cambian.
5. Respuestas propias con nombre propio (`CommunityOrMemberNotFound`, `CommunityStateConflict`); `NotFound` y
   `StateConflict` de identidad no se redefinen.
6. Se añaden las etiquetas `members` y `my-member`, y los `paths`, `parameters` y `schemas` nuevos; ningún nombre choca
   con los existentes. La etiqueta `my-member` existe para que sus operaciones no caigan en `MeApi`, que implementa
   `identity`. `x-merge` se elimina en la fusión.
7. Resultado: cambio solo aditivo; `info.version` 0.2.0 → 0.3.0. Tras fusionar: Redocly con `api/redocly.yaml`,
   `./mvnw -B generate-sources` y `npm run api:generate`.

## Decisiones principales

Detalle, alternativas y consecuencias en [research.md](./research.md).

1. **Módulo `community`** dueño de comunidades, hermanos, contacto y consentimiento; `identity_community` queda como
   proyección con un único escritor (R1).
2. **`Member`** como nombre del hermano en el código (R2).
3. **Estado en dos ejes** (`ACTIVE`/`DEACTIVATED` y responsable opcional que no vuelve a vacío), con FK compuesta y
   bloqueo de fila; FR-018 de 002 reutilizado vía `IdentityApi`, nunca duplicado (R3).
4. **Vínculo uno a uno** con índice único; FR-030 y el desvínculo previo al borrado con la interfaz de extensión
   `UserLinkGuard`, cuya regla es el invariante «usuario y hermano vinculados en la misma comunidad»; traslado y borrado
   en cascada a través de `IdentityApi.moveUser`/`deleteUser` en una transacción (R4).
5. **Carga inicial** de comunidades por `CommunityProvisioner` (R5).
6. **Validación FR-010** con dos expresiones fijas, iguales en servidor y cliente (R6).
7. **Consentimiento** como registro de solo inserción por campo, con canal, autor, aviso y fecha, sin valores; entrada
   `{shared, value}` explícita (R7).
8. **Permisos** en `MemberAccessPolicy`; hermano ajeno → `404` (R8).
9. **Orden** con `Collator` español en aplicación; búsqueda en el cliente (R9).
10. **Supresión** atómica con el usuario vinculado; copia por `data-export` (R10).
11. **Cliente**: pestaña Comunidad para todos; gestión en Gestión; mis datos en Cuenta (R14).
12. **Orden global de bloqueos** `community` (por id) → `community_member` → `identity`, con la regla «lectura previa
    para ordenar, bloqueo, relectura con bloqueo y validación, reintento acotado si cambió»; vincular bloquea el usuario
    con `IdentityApi.lockUser`; traslado bloquea origen y destino por id y revalida el destino; sin FK entre módulos
    (R15).
13. **Comunidad con historial** en reservas o agenda: no se borra, se desactiva (R16).
14. **Autor del consentimiento** visible en la copia y en Mis datos: nombre o «usuario eliminado» (R17).

## Criterios de éxito

| Criterio | Cómo se mide | Dónde |
|---|---|---|
| SC-001 (comunidad y responsable < 3 min) | ejecución manual cronometrada por una persona que no conoce la pantalla: desde Gestión hasta ver la comunidad con responsable | [quickstart.md](./quickstart.md) § 3, paso 1 |
| SC-002 (hermano con contacto < 2 min) | ejecución manual cronometrada como moderador: desde Gestión → Hermanos hasta ver el hermano con su móvil | quickstart § 3, paso 3 |
| SC-003 (teléfono < 30 s) | ejecución manual cronometrada como registrado con 80 hermanos sintéticos | quickstart § 3, paso 5 |
| SC-004 (90 % actualiza su contacto a la primera) | en la prueba manual, al menos 9 de 10 intentos con personas distintas (incluidas mayores) guardan sin error ni ayuda; mensajes de validación con formato previo, ejemplo y sin borrar lo escrito (R14) | quickstart § 3, paso 6 |
| SC-005 a SC-009 | tests automáticos | quickstart § 2 |

## Pendientes fuera del ámbito de este plan

- Texto del aviso de datos de contacto (contenido legal) para los recursos del cliente: lo redacta el usuario.
- Reservas y agenda de comunidades desactivadas: 001 no lo contempla; se revisará si hace falta bloquearlas.

## Complexity Tracking

Sin violaciones de la constitución.
