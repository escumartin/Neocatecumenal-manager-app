# Tasks: Comunidades y hermanos

**Input**: Design documents from `/specs/003-comunidades-hermanos/`

**Prerequisites**: plan.md, spec.md, research.md (R1-R17), data-model.md, contracts/community.openapi.yaml, contracts/community-module-api.md, quickstart.md, `.specify/memory/constitution.md`

**Tests**: obligatorios (constitución IV, regla 3 del proyecto): cada regla de negocio nace de un test que falla. Dentro de cada historia el orden es capa a capa: dominio, aplicación, adaptadores (Testcontainers y MockMvc), cliente; en cada capa el test va antes que su implementación y debe verse fallar. Los escenarios y las carreras de dos hilos siguen quickstart.md § 2; cada FR-001 a FR-030 tiene al menos una tarea de test.

**Organization**: tareas agrupadas por historia de usuario (US1-US7). Cero comentarios en código y configuración. `community` no publica ni escucha eventos de dominio (R4). Orden global de bloqueos `community` (por id) → `community_member` → `identity` en todos los caminos (R15).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede ejecutar en paralelo (ficheros distintos, sin dependencias)
- **[Story]**: US1 a US7
- Las citas `data-model.md Entidad.campo` indican el campo restringido que la tarea implementa o valida

## Path Conventions

- `B/` = `backend/src/main/java/io/github/escumartin/neocatecumenal/` (módulos `B/community/` e `B/identity/`; raíces `B/spaces/` y `B/agenda/`)
- `BT/` = `backend/src/test/java/io/github/escumartin/neocatecumenal/` (`BT/community/`, `BT/identity/`)
- `BR/` = `backend/src/main/resources/`
- `A/` = `app/src/`
- Contrato: `api/openapi.yaml` (fuente de verdad; borrador en `specs/003-comunidades-hermanos/contracts/community.openapi.yaml`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: contrato primero y esqueleto del módulo; sin dependencias nuevas ni cambios en `backend/pom.xml`

- [x] T001 Fusionar `specs/003-comunidades-hermanos/contracts/community.openapi.yaml` en `api/openapi.yaml` según la regla de fusión de plan.md (aditiva: `servers`, `security` y `securitySchemes` de `api/openapi.yaml`; referencias externas a locales; `GET /api/communities` y el esquema `Community` sustituyen a los existentes; unión de `x-merge.problemCodes` y `x-merge.validationCodes` en los enums de `code`; descripciones de `updateUser` y `createUser` ampliadas; respuestas `CommunityOrMemberNotFound` y `CommunityStateConflict`; etiquetas `members` y `my-member`; eliminar `x-merge`; `info.version` 0.3.0) y validar con Redocly (`api/redocly.yaml`)
- [x] T002 [P] Generar las interfaces del backend (`CommunitiesApi` ampliada, `MembersApi`, `MyMemberApi`) con `cd backend && ./mvnw -B generate-sources` y comprobar que no se edita nada generado; se ejecuta en la misma unidad de trabajo que T004: entre ambas el build está roto (`skipDefaultInterface=true` hace que `CommunitiesApi` exija métodos que el controlador de `identity` no implementa), y la feature se mergea en una sola PR, nunca antes de T043
- [x] T003 [P] Regenerar los tipos del cliente en `A/api/schema.d.ts` (`cd app && npm run api:generate` y `npm run api:check`)
- [x] T004 Borrar de `identity` lo que pasa a `community` (R1), en la misma unidad de trabajo que T002 (el build solo compila al cerrar ambas): `B/identity/infrastructure/in/rest/CommunitiesController.java`, `B/identity/application/port/in/ListCommunities.java`, `B/identity/application/service/ListCommunitiesService.java`, su alta en `B/identity/infrastructure/config/IdentityServicesConfig.java` y `BT/identity/infrastructure/CommunitiesControllerTest.java`; reconectar `B/identity/application/service/IdentityApiService.java` (líneas 7 y 25-48) para que `communities()` lea del puerto de salida `CommunityRepository` de `identity` en lugar del puerto `ListCommunities` borrado, y mover a un fake de `CommunityRepository` el test de orden de `BT/identity/application/IdentityApiServiceTest.java` (líneas 16, 44 y 162); el test del `500` de `BT/identity/infrastructure/ErrorResponsesIT.java` (líneas 12, 78 y 172) pasa a apoyarse en otro endpoint de `identity` (p. ej. `GET /api/me`); `GET /api/communities` no responde hasta T043 y el checkpoint de la fase 2 debe compilar
- [x] T005 [P] Crear el esqueleto del módulo `community` en `B/community/` (`package-info.java` con `@ApplicationModule(allowedDependencies = {"identity", "spaces", "agenda", "generated"})`; paquetes `domain`, `application/port/in`, `application/port/out`, `application/service`, `infrastructure/{in/rest,in/identity,out/persistence,out/identity,out/history,config}`)
- [x] T006 Comprobar, sin cambiar `BT/ArchitectureTest.java` ni `BT/ModularityTest.java`, que cubren `community.domain` (sin Spring, JPA ni Jackson) y `community.application` sin dependencia de `infrastructure`, y que `ApplicationModules.verify()` queda verde con el esqueleto: `community` solo depende de `identity`, `spaces`, `agenda` y `generated`, y ninguno de ellos depende de `community` (principios I a III)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: migraciones, API de `identity` ampliada, interfaces de extensión, reintento de bloqueos, tipos base y manejadores de errores de `community`. Bloquea todas las historias.

**CRITICAL**: ninguna historia empieza hasta cerrar esta fase.

### Tests (escribir primero, ver fallar)

- [x] T007 [P] Test de Flyway de `V5__identity_community_active.sql` con Testcontainers en `BT/identity/infrastructure/CommunityActiveMigrationIT.java`: `active boolean not null default true`, las filas existentes de `identity_community` quedan activas (FR-005; data-model.md `IdentityCommunity.active`)
- [x] T008 [P] Test de Flyway y restricciones SQL de `V6__community.sql` con Testcontainers en `BT/community/infrastructure/CommunitySchemaMigrationIT.java`: `uk_community_number` y `check (number > 0)`, nombre en blanco rechazado, `status in (...)`, FK compuesta del responsable (uno de otra comunidad se rechaza y actualizar la fila a mano viola la FK), `community_member.community_id` sin `on delete` (no se borra una comunidad con hermanos), checks de `given_name`, `surnames`, `mobile` y `landline` (`600000001`, `+351912345678` válidos; `512345678`, `60000000`, `+1234567890123456` inválidos), `uk_community_member_user`, `uk_community_member_id_community`, consentimiento borrado en cascada con el hermano, `check ((channel = 'APP') = (recorder_role = 'MEMBER'))` y `check (channel = 'APP' or notice_given)`, y copia de `identity_community` a `community` activas y sin responsable al migrar de V5 a V6 con filas previas (R13; FR-002, FR-003, FR-005, FR-006, FR-007, FR-009, FR-010, FR-022, FR-026, FR-027, FR-028; data-model.md `Community.number`, `Community.name`, `Community.status`, `Community.responsibleMemberId`, `Member.communityId`, `Member.givenName`, `Member.surnames`, `Member.mobile`, `Member.landline`, `Member.linkedUserId`, `ConsentRecord.channel`, `ConsentRecord.recorderRole`, `ConsentRecord.noticeGiven`)
- [x] T009 [P] Tests de la API nueva de `identity` en `BT/identity/application/IdentityApiServiceTest.java`: `user` y `lockUser` (vacío si no existe), `usersOf` ordenado por nombre, `moveUser` con la política de gestión (solo administrador, sin acción propia, último moderador, comunidad destino activa, guardián) y `deleteUser` (cierra sesiones y publica `UserDeleted`) lanzando `IdentityRuleViolation` con `FORBIDDEN`, `SELF_ACTION_FORBIDDEN`, `LAST_MODERATOR`, `LAST_ADMINISTRATOR` y `COMMUNITY_DEACTIVATED`; `CommunityRef.active` (FR-005, FR-016, FR-018, FR-028)
- [x] T010 [P] Tests de `CommunityCatalogService` en `BT/identity/application/CommunityCatalogServiceTest.java`: `register` inserta la proyección activa, `renumber`, `setActive`, `remove` con `IdentityRuleViolation("COMMUNITY_HAS_USERS")` si hay usuarios; el catálogo es el único escritor de `identity_community` (FR-025, FR-005; data-model.md `IdentityCommunity.active`)
- [x] T011 [P] Tests de `UpdateUserService` y `DeleteUserService` con fakes en `BT/identity/application/UserLinkGuardIntegrationTest.java`: el guardián se consulta como primer paso y antes de cualquier bloqueo de `identity` (solo si cambia la comunidad en `UpdateUser`); `allowsCommunityChange` falso da `409 USER_LINKED_TO_MEMBER`; al volver, el usuario se relee con `findByIdForUpdate` y, si su comunidad no es la pasada al guardián, `StaleLockOrderException` sin más efectos; si coincide, `checkDeletion` y `LockingMembershipCounts` usan la comunidad releída (`LAST_MODERATOR` contra la nueva, nunca contra la antigua); sin implementación registrada se usa el guardián permisivo (FR-018, FR-028, FR-030, R15)
- [x] T012 [P] Tests de `CreateUserService` y `UpdateUserService` en `BT/identity/application/UserCommunityActiveTest.java`: alta o traslado a una comunidad desactivada da `COMMUNITY_DEACTIVATED`, leyendo `active` bajo `lockCommunity` (FR-005; data-model.md `IdentityCommunity.active`)
- [x] T013 [P] Tests de `BootstrapIdentityService` en `BT/identity/application/BootstrapIdentityServiceTest.java`: la carga inicial pide la comunidad a `CommunityProvisioner.ensure(number)` y usa el id devuelto; con el aprovisionador falso de la `@TestConfiguration` registra solo la proyección y sin bean el arranque falla (R5)
- [x] T014 [P] Tests Testcontainers de los repositorios de `identity` en `BT/identity/infrastructure/out/CommunityUserLockingIT.java`: `UserRepository.findByIdForUpdate` (`FOR UPDATE`), `lockForShare` (`FOR SHARE`), `CommunityRepository.setActive`, `renumber` y `delete`, este último con `23503` traducido a `COMMUNITY_HAS_USERS` (R15; data-model.md `IdentityCommunity.active`)
- [x] T015 [P] Tests de `TransactionRetry` de `identity` en `BT/identity/infrastructure/TransactionRetryTest.java` y de `community` en `BT/community/infrastructure/TransactionRetryTest.java` con MockMvc y un caso de uso falso que siempre lanza `StaleLockOrderException`: se llama 3 veces y la respuesta es `409 CONCURRENT_MODIFICATION`; con una sola excepción, segunda llamada y `2xx`; cada intento abre su propia transacción (R15)
- [x] T016 [P] Tests del manejador de errores y del actor de `community` en `BT/community/infrastructure/CommunityProblemHandlerTest.java` y `BT/community/infrastructure/ActorResolverTest.java`: cada excepción de dominio a su `status` y `code` estable (`403 FORBIDDEN`, `404 NOT_FOUND`, `409` con `COMMUNITY_HAS_RESPONSIBLE`, `COMMUNITY_NOT_EMPTY`, `COMMUNITY_HAS_HISTORY`, `COMMUNITY_HAS_PRIVILEGED_USERS`, `COMMUNITY_DEACTIVATED`, `RESPONSIBLE_REQUIRED`, `MEMBER_ALREADY_LINKED`, `USER_ALREADY_LINKED`, `USER_LINKED_TO_MEMBER`, `CONSENT_MANAGED_BY_MEMBER`, `MEMBER_NOT_LINKED`, `CONCURRENT_MODIFICATION`; `422 VALIDATION_FAILED` con `errors`), `OptimisticLockingFailureException` a `409`, sin manejadores genéricos, sin nombres, teléfonos ni correos en `Problem.detail` ni en los logs (solo `code` y `status`); el actor copia solo `userId`, rol y `communityId`, sin `CurrentUser` responde `401` (FR-021, SC-009)

### Implementation

- [x] T017 Migración `BR/db/migration/V5__identity_community_active.sql` con `identity_community.active boolean not null default true` (data-model.md `IdentityCommunity.active`); `V1` a `V4` no se tocan
- [x] T018 Migración `BR/db/migration/V6__community.sql` con `community`, `community_member`, la FK compuesta `fk_community_responsible`, `community_contact_consent`, sus checks e índices y el `insert ... select` desde `identity_community`, según data-model.md Persistencia (data-model.md `Community.number`, `Community.name`, `Community.status`, `Community.responsibleMemberId`, `Member.communityId`, `Member.givenName`, `Member.surnames`, `Member.mobile`, `Member.landline`, `Member.email`, `Member.linkedUserId`, `ConsentRecord.field`, `ConsentRecord.decision`, `ConsentRecord.channel`, `ConsentRecord.recordedBy`, `ConsentRecord.recorderRole`, `ConsentRecord.noticeGiven`, `ConsentRecord.recordedAt`)
- [x] T019 Tipos nuevos en la raíz de `identity` (`B/identity/`): `UserRef`, `IdentityRuleViolation`, `StaleLockOrderException`, `CommunityCatalog`, `UserLinkGuard`, `CommunityProvisioner`, `CommunityRef.active` y los métodos `user`, `lockUser`, `usersOf`, `moveUser` y `deleteUser` de `IdentityApi`, según contracts/community-module-api.md (data-model.md `IdentityCommunity.active`)
- [x] T020 Proyección en `identity`: `active` en `B/identity/domain/Community.java`, `CommunityJpaEntity`, `CommunityPersistenceAdapter`, `CommunitySpringDataRepository`; `UserRepository.findByIdForUpdate` y `lockForShare`, y `CommunityRepository.setActive`, `renumber` y `delete` con traducción de `23503` en `B/identity/application/port/out/` e `B/identity/infrastructure/out/persistence/` (data-model.md `IdentityCommunity.active`)
- [x] T021 `CommunityCatalogService` y `IdentityApiService` ampliado en `B/identity/application/service/`; `moveUser` y `deleteUser` reutilizan `UpdateUser`, `DeleteUser` y `UserManagementPolicy`, nunca duplican sus reglas (R4)
- [x] T022 `UpdateUserService`, `DeleteUserService`, `CreateUserService` y `BootstrapIdentityService` en `B/identity/application/service/`: guardián primero y relectura con `findByIdForUpdate` y `StaleLockOrderException` (`DeleteUserService` sobre el bloque de las líneas 45-46), `COMMUNITY_DEACTIVATED` bajo `lockCommunity`, y carga inicial por `CommunityProvisioner`; en `IdentityServicesConfig` se declara un bean explícito de guardián permisivo (comportamiento de producción previsto en contracts/community-module-api.md) que el adaptador de `community` sustituye por ser `@Primary` (sin `@ConditionalOnMissingBean`); `CommunityProvisioner` no tiene reserva en producción: si falta el bean, el arranque falla; los tests de `identity` y los de contexto completo de la fase 2 registran un `CommunityProvisioner` en una `@TestConfiguration` que el adaptador de US1 deja sin efecto (FR-005, FR-030, R5, R15)
- [x] T023 `TransactionRetry` en `B/identity/infrastructure/in/rest/` y en `B/community/infrastructure/in/rest/`: componente pequeño de cada capa de adaptadores; ante `StaleLockOrderException` reintenta la transacción, como mucho 3 llamadas, después `409 CONCURRENT_MODIFICATION`; `UsersController` envuelve cada caso de uso de escritura con el de `identity` (R15)
- [x] T024 [P] Tipos base de dominio de `community` en `B/community/domain/`: `CommunityId`, `MemberId`, `UserId`, `ActorRole`, `Actor`, `LinkedUser`, `CommunityStatus`, `CommunityDomainException` y las excepciones de dominio que usan las políticas (`CommunityForbidden`, `CommunityFieldInvalid`, `MemberNotFound`, `CommunityNotFound`) (data-model.md `Community.status`)
- [x] T025 [P] `ActorResolver`, `CommunityProblemHandler` (`@RestControllerAdvice` con `@Order(Ordered.HIGHEST_PRECEDENCE)` limitado a los controladores de `community`, sin datos personales) y cableado en `B/community/infrastructure/in/rest/` y `B/community/infrastructure/config/`, con el `Clock` de `ClockConfig` y sin bean `Clock` nuevo, apoyados en `IdentityApi.currentUser()`

**Checkpoint**: `cd backend && ./mvnw -B verify` en verde con la base (`GET /api/communities` aún sin controlador); las historias pueden empezar.

---

## Phase 3: User Story 1 - Gestionar comunidades (Priority: P1) 🎯 MVP

**Goal**: el administrador da de alta, renumera, renombra, desactiva, reactiva y borra comunidades; ningún otro rol puede; `identity` sigue viendo la lista de solo lectura.

**Independent Test**: el administrador crea la comunidad 12, la renombra, la desactiva con confirmación y la reactiva; con otra vacía y sin historial la borra; un moderador recibe `403` en todo ello.

**Cubre**: FR-001, FR-002, FR-003, FR-004, FR-005 (desactivar y reactivar), FR-025, SC-001 (primera parte), SC-006 (número).

### Tests de dominio

- [x] T026 [P] [US1] Tests de `CommunityNumber` y `CommunityName` en `BT/community/domain/CommunityNumberTest.java` y `CommunityNameTest.java`: número > 0; nombre NFC y recortado, 1 a 100 caracteres, vacío o en blanco pasa a ausente, 101 es `TOO_LONG` (FR-002, FR-003; data-model.md `Community.number`, `Community.name`)
- [x] T027 [P] [US1] Tests de `Community` en `BT/community/domain/CommunityTest.java`: `create` activa y sin responsable, `renumber`, `rename`, `deactivate(privilegedUsersPresent, confirmed)` con `COMMUNITY_HAS_PRIVILEGED_USERS` sin confirmar, `reactivate`, `checkAcceptsNewMembers` con `COMMUNITY_DEACTIVATED`, `checkDeletable(members, users, hasHistory)` con `COMMUNITY_HAS_RESPONSIBLE`, `COMMUNITY_NOT_EMPTY` y `COMMUNITY_HAS_HISTORY`, y borrado permitido también desde `DEACTIVATED` sin responsable (FR-004, FR-005, FR-006; data-model.md `Community.status`, `Community.responsibleMemberId`)
- [x] T028 [P] [US1] Tests de `MemberAccessPolicy` para la gestión de comunidades en `BT/community/domain/MemberAccessPolicyCommunityTest.java`: gestionar comunidades y responsable solo el administrador; registrado y moderador `CommunityForbidden` (FR-001)

### Implementación de dominio

- [x] T029 [US1] `CommunityNumber`, `CommunityName`, `Community` y `MemberAccessPolicy` (acciones de comunidad) en `B/community/domain/` (data-model.md `Community.number`, `Community.name`, `Community.status`, `Community.responsibleMemberId`)

### Cruce con `spaces` y `agenda`

- [x] T030 [P] [US1] Tests de `SpacesApi.hasBookingRequestsFor` y `AgendaApi.hasEventsFor` en `BT/spaces/application/SpacesApiHistoryTest.java` y `BT/agenda/application/AgendaApiHistoryTest.java` (más Testcontainers en `BT/spaces/infrastructure/BookingRequestHistoryIT.java` y `BT/agenda/infrastructure/EventHistoryIT.java`): verdadero si hay al menos una solicitud o evento con ese `community_id`, sin FK (R16)
- [x] T031 [US1] `hasBookingRequestsFor(UUID communityId)` en `B/spaces/SpacesApi.java` y su servicio, y `AgendaApi.hasEventsFor(UUID communityId)` nuevo en `B/agenda/AgendaApi.java` con su servicio y consulta en la persistencia de cada módulo (R16)

### Tests de aplicación

- [x] T032 [P] [US1] Tests de `CreateCommunityService`, `UpdateCommunityService`, `DeactivateCommunityService` y `ReactivateCommunityService` con fakes en `BT/community/application/CommunityManagementServicesTest.java`: alta o cambio escribe en `community` y en `IdentityCommunityCatalog` en la misma transacción y nada si falla una; `COMMUNITY_NUMBER_TAKEN` en el campo `number`; la desactivación pide `usersOf` y `confirmed`; el moderador y el registrado `CommunityForbidden` (FR-001, FR-002, FR-005; data-model.md `Community.number`, `Community.status`)
- [x] T033 [P] [US1] Tests de `DeleteCommunityService`, `ListCommunitiesService` y `GetCommunityService` con fakes en `BT/community/application/CommunityQueriesAndDeleteTest.java`: el borrado bloquea la comunidad con `lockInIdOrder` y evalúa `checkDeletable` con `MemberRepository.countIn`, `UserDirectory.usersOf` y `CommunityHistory`; el listado oculta las desactivadas salvo `includeDeactivated` para el administrador (`CommunityForbidden` para otros); `GetCommunity` expone `hasResponsible` (FR-004; data-model.md `Community.responsibleMemberId`)

### Implementación de aplicación

- [x] T034 [US1] Puertos de entrada `CreateCommunity`, `UpdateCommunity`, `DeactivateCommunity`, `ReactivateCommunity`, `DeleteCommunity`, `ListCommunities`, `GetCommunity` y `ProvisionCommunity`, puertos de salida `CommunityRepository` (con `lockInIdOrder(CommunityId...)`), `MemberRepository` (`countIn`), `UserDirectory`, `IdentityCommunityCatalog` y `CommunityHistory` en `B/community/application/port/`, y sus servicios transaccionales en `B/community/application/service/`

### Tests de adaptadores

- [x] T035 [P] [US1] Tests Testcontainers de `CommunityRepository` en `BT/community/infrastructure/CommunityPersistenceIT.java`: ida y vuelta, `@Version`, `lockInIdOrder` toma los bloqueos en orden ascendente de id, `23505` de `uk_community_number` traducido a `COMMUNITY_NUMBER_TAKEN` (FR-002; data-model.md `Community.number`, `Community.name`, `Community.status`)
- [x] T036 [P] [US1] Test de concurrencia de alta de número en `BT/community/infrastructure/CommunityNumberConcurrencyIT.java`: dos hilos con barrera crean el mismo número, uno `201` y otro `422 COMMUNITY_NUMBER_TAKEN`, una sola fila en `community` y una en `identity_community` (FR-002, SC-006; data-model.md `Community.number`)
- [x] T037 [P] [US1] Tests Testcontainers con `identity` real en `BT/community/infrastructure/CommunityIdentityProjectionIT.java`: `IdentityCommunityCatalog` y `UserDirectory` mantienen `identity_community` en la misma transacción (alta, renumerar, `active`, borrar); borrar con usuarios y sin hermanos da `409 COMMUNITY_NOT_EMPTY` por la FK de `identity_user`; borrar con reservas o eventos da `409 COMMUNITY_HAS_HISTORY` y la comunidad sigue y se puede desactivar (R1, R16; FR-004, FR-025)
- [x] T038 [P] [US1] Test `@ApplicationModuleTest` de `community` con `identity` en `BT/community/CommunityModuleTest.java`: `IdentityApi.communities()` incluye la comunidad nueva con id, número y `active`, y la carga inicial por `CommunityProvisioner` crea la comunidad en `community` y en `identity_community` con el mismo id (R5; FR-025)
- [x] T039 [P] [US1] Tests MockMvc de `CommunitiesController` con la cadena de seguridad real en `BT/community/infrastructure/CommunitiesControllerTest.java`: `GET` oculta desactivadas e `includeDeactivated=true` como no administrador `403`; `POST` `201` con `Location`, `hasResponsible=false`; el hermano y el responsable que necesitan los casos de borrado se siembran por SQL (la designación llega en US2); nombre en blanco guardado como ausente y de 101 caracteres `422 TOO_LONG`; `PUT` renumera y renombra; `DELETE` con responsable `409 COMMUNITY_HAS_RESPONSIBLE`, vacía `204`; `POST .../deactivation` sin `confirmed` `409 COMMUNITY_HAS_PRIVILEGED_USERS` y con `confirmed=true` `204`; `POST .../reactivation`; moderador y registrado `403` en cada operación de gestión (FR-001, FR-002, FR-003, FR-004, FR-005; data-model.md `Community.number`, `Community.name`)

### Implementación de adaptadores

- [x] T040 [US1] Entidad JPA `CommunityJpaEntity` con `@Version`, repositorio Spring Data con `FOR UPDATE` ordenado por id, adaptador `CommunityRepository`, traducción de `23505` y `23503` a códigos y mappers en `B/community/infrastructure/out/persistence/` (data-model.md `Community.number`, `Community.name`, `Community.status`, `Community.responsibleMemberId`)
- [x] T041 [P] [US1] Adaptadores `UserDirectory` e `IdentityCommunityCatalog` sobre `IdentityApi` y `CommunityCatalog` en `B/community/infrastructure/out/identity/`, y `CommunityHistory` sobre `SpacesApi.hasBookingRequestsFor` y `AgendaApi.hasEventsFor` en `B/community/infrastructure/out/history/`
- [x] T042 [P] [US1] Adaptador de entrada `CommunityProvisioner` (`ensure(number)` crea la comunidad y su proyección si no existe) en `B/community/infrastructure/in/identity/`, que sustituye al aprovisionador de reserva de `identity` (R5)
- [x] T043 [US1] `CommunitiesController` implementando `listCommunities`, `createCommunity`, `getCommunity`, `updateCommunity`, `deleteCommunity`, `deactivateCommunity` y `reactivateCommunity` de `CommunitiesApi` con mappers en `B/community/infrastructure/in/rest/`, con `TransactionRetry` y `ActorResolver` (data-model.md `Community.number`, `Community.name`)

### Cliente

- [x] T044 [P] [US1] Tests de componente y de hooks en `A/screens/communities/__tests__/CommunitiesScreens.test.tsx`, `A/hooks/communities/__tests__/use-communities.test.tsx` y `A/lib/api/__tests__/communities-api.test.ts`: lista con número, nombre opcional, estado y «Sin responsable» con `StateView`; formulario de alta y edición con `FormField` (número obligatorio, nombre opcional con límite de 100 y error de número repetido en su campo); ficha con acciones de desactivar (confirmación accesible que avisa de los moderadores), reactivar y borrar (confirmación de borrado definitivo con el motivo de rechazo en `COMMUNITY_HAS_RESPONSIBLE`, `COMMUNITY_NOT_EMPTY` y `COMMUNITY_HAS_HISTORY`); cada control con `accessibilityRole` y `accessibilityLabel`, objetivos ≥ 48 px y textos ≥ 18 px; la opción `includeDeactivated` de `useCommunities` solo para el administrador (FR-001 a FR-005, FR-024)
- [x] T045 [P] [US1] Test de la pantalla de pendientes de 001 en `A/screens/bookings/__tests__/PendingRequestsScreen.test.tsx`: la búsqueda de números pide `includeDeactivated`, de modo que el administrador ve el número de una solicitud de una comunidad desactivada, y un no administrador no lo pide (R12, R14)
- [x] T046 [US1] `A/lib/api/communities-api.ts` (alta, edición, estado, borrado, `includeDeactivated`), `A/hooks/communities/use-communities.tsx` con la opción nueva y `A/hooks/communities/use-community*.tsx` (ficha y acciones), pantallas de lista, formulario y ficha en `A/screens/communities/` con `ListSection`, `ListRow`, `StateView` y `FormField`, rutas en `A/app/(app)/(tabs)/gestion/comunidades/{index,nueva,[communityId]}.tsx`, fila Comunidades en `A/screens/management/index.tsx` con `canManageCommunities` en `A/lib/auth/navigation-permissions.ts`, y `A/i18n/es/communities.ts` con los mensajes de `COMMUNITY_NUMBER_TAKEN`, `COMMUNITY_HAS_RESPONSIBLE`, `COMMUNITY_NOT_EMPTY`, `COMMUNITY_HAS_HISTORY`, `COMMUNITY_HAS_PRIVILEGED_USERS` y `COMMUNITY_DEACTIVATED`, solo con tokens de `design-system`
- [x] T047 [US1] `A/screens/bookings/pending-requests.tsx`: la búsqueda de números resuelve con `useCommunities` y pasa a pedir `includeDeactivated` para no perder los de comunidades desactivadas (cambio de 001; R12, R14)

**Checkpoint**: US1 funcional de punta a punta; el administrador gestiona comunidades y `GET /api/communities` vuelve a responder.

---

## Phase 4: User Story 3 - Hermanos de una comunidad y sus datos de contacto (Priority: P1)

**Goal**: el moderador de una comunidad (o el administrador) da de alta hermanos con nombre, apellidos opcionales y móvil, fijo y correo solo con consentimiento por campo, también para quien no usa la app; edita nombre y contacto, y ve la lista de gestión. Va antes que US2 porque designar responsable exige hermanos.

**Independent Test**: un moderador da de alta un hermano sin usuario con móvil y consentimiento verbal con aviso; lo ve en la lista con su móvil; sin consentimiento o con un teléfono inválido, nada se guarda.

**Cubre**: FR-009, FR-010, FR-011, FR-015 (alta, edición y consentimiento en nombre), FR-019 (en nombre), FR-020 (rectificación), FR-021, FR-022, FR-026, FR-027, FR-029, SC-002, SC-007, SC-009.

### Tests de dominio

- [x] T048 [P] [US3] Tests de `PersonName` en `BT/community/domain/PersonNameTest.java`: NFC y recortado, nombre 1 a 100 obligatorio (`REQUIRED`), apellidos 1 a 100 o ausentes (FR-009; data-model.md `Member.givenName`, `Member.surnames`)
- [x] T049 [P] [US3] Tests de `PhoneNumber` y `EmailAddress` en `BT/community/domain/PhoneNumberTest.java` y `EmailAddressTest.java`, parametrizados: válidos `600 000 001`, `912-345-678`, `+351912345678` (normalizados sin espacios, puntos, guiones ni paréntesis) e inválidos `512345678`, `60000000`, `+1234567890123456` (`PHONE_INVALID`); correo recortado ≤ 254, sintaxis WHATWG con punto en el dominio, `a@b` inválido (`EMAIL_INVALID`) (FR-010; data-model.md `Member.mobile`, `Member.landline`, `Member.email`)
- [x] T050 [P] [US3] Tests de `Member.register`, `rename` y `changeContact` en `BT/community/domain/MemberTest.java`: alta en comunidad desactivada `COMMUNITY_DEACTIVATED`; contacto sin contexto `CONSENT_REQUIRED`; `VERBAL` o `WRITTEN` con `noticeGiven = false` `NOTICE_NOT_GIVEN`; `shared` de no a sí guarda el valor y añade fila `GRANTED` (`REQUIRED` si falta el valor); de sí a no, valor `null` y fila `WITHDRAWN`; sigue en sí con valor distinto es rectificación sin fila; hermano vinculado cuyo `shared` cambia por quien no es él mismo `CONSENT_MANAGED_BY_MEMBER`; `rename` no toca nada de `identity` (FR-010, FR-011, FR-015, FR-019, FR-020, FR-022, FR-026, FR-029; data-model.md `Member.contact`, `ConsentRecord.decision`, `ConsentRecord.channel`, `ConsentRecord.noticeGiven`)
- [x] T051 [P] [US3] Test de propiedad en `BT/community/domain/MemberContactPropertyTest.java`: ninguna secuencia generada de cambios de contacto deja un valor sin último `GRANTED` de su campo, y ninguna fila de consentimiento contiene valores (SC-007, FR-027; data-model.md `Member.contact`, `ConsentRecord.field`)
- [x] T052 [P] [US3] Tests de `MemberAccessPolicy` para alta, nombre, contacto en nombre y rectificación en `BT/community/domain/MemberAccessPolicyMembersTest.java` y de `DirectoryOrder` en `BT/community/domain/DirectoryOrderTest.java`: moderador solo su comunidad, administrador cualquiera, registrado nada; orden por `Collator` español de fuerza primaria con clave `surnames ?? givenName`, después `givenName`, después `id`, sin efecto de tildes ni mayúsculas (FR-009, FR-015, FR-016)

### Implementación de dominio

- [x] T053 [US3] `PersonName`, `PhoneNumber`, `EmailAddress`, `ContactField`, `ConsentDecision`, `ConsentChannel`, `RecorderRole`, `ConsentContext`, `ContactChange`, `ConsentRecord`, `Member` (`register`, `rename`, `changeContact`), `DirectoryOrder` y las reglas de miembro de `MemberAccessPolicy` en `B/community/domain/` (data-model.md `Member.communityId`, `Member.givenName`, `Member.surnames`, `Member.mobile`, `Member.landline`, `Member.email`, `Member.contact`, `ConsentRecord.field`, `ConsentRecord.decision`, `ConsentRecord.channel`, `ConsentRecord.recordedBy`, `ConsentRecord.recorderRole`, `ConsentRecord.noticeGiven`, `ConsentRecord.recordedAt`)

### Tests de aplicación

- [x] T054 [P] [US3] Tests de `CreateMemberService`, `RenameMemberService`, `ChangeMemberContactService`, `GetMemberService` y `ListMembersService` con fakes y reloj fijo en `BT/community/application/MemberServicesTest.java`: moderador de A crea en A y no en B (`CommunityForbidden`), administrador elige comunidad, comunidad desactivada `COMMUNITY_DEACTIVATED`, `GET` de un hermano de otra comunidad `MemberNotFound` idéntico a uno inexistente, `ListMembers` ordenado con `DirectoryOrder`, `RenameMember` no llama a `UserDirectory`, contacto con consentimiento anotado con autor y rol del actor (FR-009, FR-010, FR-011, FR-015, FR-016, FR-029; data-model.md `Member.communityId`, `ConsentRecord.recordedBy`)

### Implementación de aplicación

- [x] T055 [US3] Puertos de entrada `CreateMember`, `RenameMember`, `ChangeMemberContact`, `GetMember` y `ListMembers`, puerto de salida `MemberRepository` (`save`, `findById`, `lock`, `findByCommunity`, `countIn`, `findByLinkedUser`) y servicios transaccionales en `B/community/application/`

### Tests de adaptadores

- [x] T056 [P] [US3] Tests Testcontainers de `MemberRepository` en `BT/community/infrastructure/MemberPersistenceIT.java`: ida y vuelta con consentimientos de solo inserción, `@Version`, retirada deja el valor a `NULL` y la fila `WITHDRAWN` sin valores, rectificación no deja el valor anterior en ninguna tabla, borrado del hermano en cascada con su consentimiento, `uk_community_member_user` (FR-011, FR-019, FR-020, FR-027; data-model.md `Member.contact`, `Member.mobile`, `Member.landline`, `Member.email`, `ConsentRecord.field`, `ConsentRecord.decision`)
- [x] T057 [P] [US3] Tests MockMvc de `MembersController` en `BT/community/infrastructure/MembersControllerTest.java`: `POST /api/members` `201` con `Location`, sin nombre `422 REQUIRED`, sin apellidos `201`; teléfonos y correo inválidos `422 PHONE_INVALID` o `EMAIL_INVALID` y nada guardado; contacto sin `onBehalfConsent` `422 CONSENT_REQUIRED`; `noticeGiven=false` `422 NOTICE_NOT_GIVEN`; `PUT .../contact` y `PUT /api/members/{id}` `200`; moderador con otra comunidad `403` en el alta y `404` en edición; `GET /api/members` ordenado y `GET /api/members/{id}` con solo nombre, apellidos, comunidad y contactos consentidos; renombrar no cambia `identity_user.name` (FR-009, FR-010, FR-011, FR-022, FR-026, FR-029; data-model.md `Member.givenName`, `Member.surnames`, `Member.mobile`, `Member.email`)
- [x] T058 [P] [US3] Test de logs en `BT/community/infrastructure/MemberLogsTest.java` y de errores en `BT/community/infrastructure/MemberProblemDetailsTest.java`: con el logger capturado, alta, cambio de contacto y errores con nombres, apellidos, teléfonos y correos sintéticos no emiten ninguno de ellos ni en `Problem.detail`; solo ids y códigos (FR-021, SC-009)

### Implementación de adaptadores

- [x] T059 [US3] Entidades JPA `MemberJpaEntity` y `ConsentRecordJpaEntity` con `@Version` y cascada del consentimiento, repositorio Spring Data con bloqueo `FOR UPDATE`, adaptador `MemberRepository`, traducción de `23505` y `23503` y mappers en `B/community/infrastructure/out/persistence/` (data-model.md `Member.givenName`, `Member.surnames`, `Member.mobile`, `Member.landline`, `Member.email`, `Member.linkedUserId`, `ConsentRecord.field`, `ConsentRecord.decision`)
- [x] T060 [US3] `MembersController` implementando `listMembers`, `createMember`, `getMember`, `renameMember` y `changeMemberContact` de `MembersApi` con mappers en `B/community/infrastructure/in/rest/`, con `TransactionRetry`, `ActorResolver` y errores sin datos personales (data-model.md `Member.givenName`, `Member.surnames`, `Member.mobile`, `Member.landline`, `Member.email`)

### Cliente

- [x] T061 [P] [US3] Tests de componente y de validación en `A/screens/members/__tests__/MembersScreens.test.tsx` y `A/lib/members/__tests__/contact-validation.test.ts`: lista de hermanos de gestión con `ListSection` y `ListRow`, formulario con `FormField` (nombre obligatorio, apellidos opcionales, móvil, fijo y correo con interruptor «Compartir» por campo), validación con las dos expresiones fijas de FR-010 iguales a las del servidor, mensajes con formato previo y ejemplo sin borrar lo escrito, selector de comunidad para el administrador con `RadioSection`, aviso de datos y consentimiento verbal o escrito con casilla «He informado al hermano» obligatoria; controles con rol y etiqueta, objetivos ≥ 48 px y textos ≥ 18 px (FR-010, FR-022, FR-024, FR-026)
- [x] T062 [US3] `A/lib/api/members-api.ts`, `A/lib/members/contact-validation.ts`, hooks en `A/hooks/members/`, pantallas de lista, formulario y contacto en `A/screens/members/`, rutas `A/app/(app)/(tabs)/gestion/hermanos/{index,nuevo,[memberId],contacto}.tsx`, fila Hermanos en `A/screens/management/index.tsx` con `canManageMembers` en `A/lib/auth/navigation-permissions.ts` y `A/i18n/es/members.ts` con los mensajes de `PHONE_INVALID`, `EMAIL_INVALID`, `CONSENT_REQUIRED` y `NOTICE_NOT_GIVEN`, solo con tokens de `design-system`

**Checkpoint**: US3 funcional; alta de un hermano con móvil en menos de 2 minutos (SC-002) comprobable en la prueba manual.

---

## Phase 5: User Story 2 - Responsable obligatorio de cada comunidad (Priority: P1)

**Goal**: toda comunidad nace «sin responsable», admite hermanos en ese estado y el administrador designa o cambia su responsable entre sus hermanos; una vez designado nunca falta.

**Independent Test**: con la comunidad 12 y un hermano de la 12, el administrador lo designa; designar a un hermano de otra comunidad da `422 MEMBER_NOT_IN_COMMUNITY`.

**Cubre**: FR-006, FR-007, FR-008, SC-001 (segunda parte), SC-006.

### Tests de dominio

- [x] T063 [P] [US2] Tests de `Community.designateResponsible`, `isResponsible` y `hasResponsible` en `BT/community/domain/CommunityResponsibleTest.java`: designar a un hermano propio, sustituir en una sola escritura, `MEMBER_NOT_IN_COMMUNITY` con uno de otra comunidad, sin operación para quitarlo; y `MemberAccessPolicy` (solo administrador, moderador `CommunityForbidden`) (FR-006, FR-007; data-model.md `Community.responsibleMemberId`)

### Implementación de dominio

- [x] T064 [US2] `Community.designateResponsible`, `hasResponsible` e `isResponsible` y la regla de designar en `MemberAccessPolicy`, en `B/community/domain/` (data-model.md `Community.responsibleMemberId`)

### Tests de aplicación

- [x] T065 [P] [US2] Tests de `DesignateResponsibleService` en `BT/community/application/ResponsibleServicesTest.java`: bloquea la comunidad antes que el hermano, designa a un hermano propio, `MEMBER_NOT_IN_COMMUNITY`, moderador `CommunityForbidden`; `GetCommunity` calcula `responsibleIsModerator` con `UserDirectory.user` del usuario vinculado y no bloquea ninguna operación si es falso (FR-006, FR-007, FR-008; data-model.md `Community.responsibleMemberId`)

### Implementación de aplicación

- [x] T066 [US2] Puerto de entrada `DesignateResponsible`, `DesignateResponsibleService`, y `CommunityRepository`/`MemberRepository` con la comprobación de responsable bajo el bloqueo de la fila de la comunidad en `B/community/application/` (R3, R15)

### Tests de adaptadores

- [x] T067 [P] [US2] Tests Testcontainers en `BT/community/infrastructure/ResponsiblePersistenceIT.java`: la FK compuesta rechaza al de otra comunidad, actualizar a mano `responsible_member_id` a uno ajeno viola la FK, borrar al hermano responsable viola la FK (el `409 RESPONSIBLE_REQUIRED` de borrar y trasladar se prueba en T089 y T100) (FR-006, FR-007; data-model.md `Community.responsibleMemberId`)
- [x] T068 [P] [US2] Test de concurrencia del cambio de responsable en `BT/community/infrastructure/ResponsibleChangeConcurrencyIT.java`: secuencias de designar y sustituir con dos hilos y lectores concurrentes; en ninguna lectura aparece la comunidad sin responsable una vez designado (SC-006, FR-007; data-model.md `Community.responsibleMemberId`)
- [x] T069 [P] [US2] Tests MockMvc en `BT/community/infrastructure/ResponsibleControllerTest.java`: `PUT /api/communities/{id}/responsible` `200` con uno propio, `422 MEMBER_NOT_IN_COMMUNITY` con uno ajeno, moderador y registrado `403`; «sin responsable» visible en `GET` y alta de hermano `201` en ese estado (FR-006, FR-007; data-model.md `Community.responsibleMemberId`)

### Implementación de adaptadores

- [x] T070 [US2] `designateResponsible` en `CommunitiesController` y persistencia de `responsible_member_id` en `B/community/infrastructure/in/rest/` y `B/community/infrastructure/out/persistence/` (data-model.md `Community.responsibleMemberId`)

### Cliente

- [x] T071 [P] [US2] Tests de componente en `A/screens/communities/__tests__/ResponsibleScreen.test.tsx`: la ficha muestra «Sin responsable» de forma explícita, la pantalla de responsable lista solo hermanos de esa comunidad con `RadioSection`, aviso (no bloqueo) si el responsable no tiene rol de moderador, error de `MEMBER_NOT_IN_COMMUNITY` y `RESPONSIBLE_REQUIRED` localizados, controles con rol y etiqueta, objetivos ≥ 48 px (FR-006, FR-007, FR-008, FR-024)
- [x] T072 [US2] Hook `use-community-responsible`, pantalla `A/screens/communities/responsible.tsx`, ruta `A/app/(app)/(tabs)/gestion/comunidades/responsable.tsx`, estado «Sin responsable» en lista y ficha y mensajes en `A/i18n/es/communities.ts`, solo con tokens de `design-system`

**Checkpoint**: US2 funcional; SC-001 (comunidad con responsable en menos de 3 minutos) comprobable en la prueba manual.

---

## Phase 6: User Story 4 - Directorio de la propia comunidad (Priority: P2)

**Goal**: cualquier registrado, moderador o administrador consulta el directorio de su comunidad (o de la que elige el administrador) en la pestaña nueva «Comunidad», ordenado y con búsqueda, y no ve nada de otra comunidad.

**Independent Test**: un registrado de la 12 abre la pestaña Comunidad, ve a sus hermanos con los contactos consentidos, busca por nombre y llama con un toque; pedir el directorio o una ficha de otra comunidad da `403` y `404`.

**Cubre**: FR-012, FR-013, FR-021 (errores), FR-024, SC-003, SC-005.

### Tests de dominio y aplicación

- [x] T073 [P] [US4] Tests de `MemberAccessPolicy` para ver directorio y ficha en `BT/community/domain/MemberAccessPolicyDirectoryTest.java` y de `ListMembersService`/`GetMemberService` en `BT/community/application/DirectoryServicesTest.java`: registrado y moderador solo su comunidad activa, administrador cualquiera incluso desactivada, comunidad ajena `CommunityForbidden`, comunidad desactivada `COMMUNITY_DEACTIVATED` para registrado y moderador, ficha ajena `MemberNotFound`, moderador y administrador ven los mismos campos de contacto además de `linkedUserId` y el registrado no lo ve (FR-012, FR-013; data-model.md `Member.linkedUserId`)

### Implementación de dominio y aplicación

- [x] T074 [US4] Reglas de ver directorio y ficha en `MemberAccessPolicy` (`B/community/domain/`) y su uso en `ListMembersService` y `GetMemberService` (`B/community/application/service/`) (FR-012, FR-013)

### Tests de adaptadores

- [x] T075 [P] [US4] Tests MockMvc de aislamiento con la cadena de seguridad real en `BT/community/infrastructure/DirectoryAccessIT.java`: matriz de permisos por rol y comunidad; directorio de B como registrado de A `403`, ficha de un hermano de B `404` idéntico a uno inexistente, visitante `401`, desactivada `409 COMMUNITY_DEACTIVATED`, administrador sin `communityId` `422`, el `Problem` no contiene nombre, teléfono ni correo, y el JSON del registrado omite `linkedUserId` (FR-012, FR-013, FR-021, SC-005, SC-009)
- [x] T076 [P] [US4] Test de rendimiento en `BT/community/infrastructure/DirectoryPerformanceIT.java`: directorio de 80 hermanos con contactos en menos de 200 ms p95 (rendimiento del plan; SC-003)

### Cliente

- [x] T077 [P] [US4] Tests de componente y de búsqueda en `A/screens/directory/__tests__/DirectoryScreen.test.tsx`, `A/screens/directory/__tests__/MemberCardScreen.test.tsx` y `A/lib/members/__tests__/directory-search.test.ts`: lista ordenada con `ListSection` y `ListRow`, búsqueda local sin tildes ni mayúsculas, `StateView` en carga, vacío, error y comunidad desactivada sin datos, ficha con acciones de llamar y escribir con etiquetas accesibles solo para los contactos presentes, el visitante va al login, el administrador elige comunidad con `RadioSection`; textos ≥ 18 px, objetivos ≥ 48 px y escalado de fuente al 200 % sin cortes (FR-012, FR-013, FR-024, SC-003)
- [x] T078 [US4] Pestaña «Comunidad» para todos los roles: `A/app/(app)/(tabs)/comunidad/{_layout,index,[memberId]}.tsx`, pestaña en `A/app/(app)/(tabs)/_layout.tsx` y `canSeeCommunity` en `A/lib/auth/navigation-permissions.ts`, `A/lib/members/directory-search.ts`, hooks del directorio en `A/hooks/members/`, pantallas en `A/screens/directory/` con `ListSection`, `ListRow`, `StateView`, `Linking` para llamar y escribir, y textos en `A/i18n/es/members.ts` y `A/i18n/es/navigation.ts`

**Checkpoint**: US4 funcional; teléfono de un hermano en menos de 30 segundos (SC-003) comprobable en la prueba manual.

---

## Phase 7: User Story 5 - Editar mis propios datos de contacto (Priority: P2)

**Goal**: el registrado con hermano vinculado edita en Cuenta → «Mis datos de contacto» solo su móvil, fijo y correo y decide cuáles comparte; ocultar un dato lo borra; ve quién anotó cada consentimiento.

**Independent Test**: un registrado vinculado añade correo y móvil compartidos y guarda; apaga «Compartir» del correo y el valor desaparece del directorio; sin hermano vinculado ve cómo pedir el vínculo.

**Cubre**: FR-011 (retirada), FR-014, FR-019 (propia), FR-020 (acceso), FR-027 (visible), SC-004.

### Tests de dominio y aplicación

- [x] T079 [P] [US5] Tests de `ChangeOwnContactService` y `GetOwnMemberService` con fakes en `BT/community/application/OwnMemberServicesTest.java`: busca el hermano con `MemberRepository.findByLinkedUser`, sin vínculo `MEMBER_NOT_LINKED`, el contexto es el del propio usuario (canal `APP`, rol `MEMBER`, `noticeGiven` no exigido), cambiar `shared` crea fila `APP` que prevalece sobre una anotada `VERBAL` o `WRITTEN`, `shared=false` borra el valor, inválido deja los valores anteriores, `MemberContactInput` no admite nombre ni comunidad, `recordedBy` con `{role, name?, deleted}` resuelto con `UserDirectory.user` (usuario borrado `deleted = true` sin nombre, `MEMBER` sin nombre) (FR-014, FR-019, FR-020, FR-027, R17; data-model.md `ConsentRecord.recordedBy`, `ConsentRecord.channel`)

### Implementación de aplicación

- [x] T080 [US5] Puertos de entrada `GetOwnMember` y `ChangeOwnContact` y sus servicios, con la resolución del autor del consentimiento, en `B/community/application/` (data-model.md `ConsentRecord.recordedBy`, `ConsentRecord.recorderRole`)

### Tests de adaptadores

- [x] T081 [P] [US5] Tests MockMvc de `MyMemberController` en `BT/community/infrastructure/MyMemberControllerTest.java`: `GET /api/me/member` `200` con `currentConsents` y su registro, `409 MEMBER_NOT_LINKED` sin vínculo; `PUT /api/me/member/contact` `200` con valores válidos, inválido `422` con valores anteriores intactos, `shared=false` borra; `PUT /api/members/{otro}/contact` como registrado `403`; el cuerpo no acepta nombre ni comunidad; tras anotar el moderador un consentimiento `VERBAL`, `currentConsents` muestra el canal y la decisión propia crea fila `APP` (FR-014, FR-019, FR-020, FR-027; data-model.md `Member.contact`, `ConsentRecord.channel`)

### Implementación de adaptadores

- [x] T082 [US5] `MyMemberController` implementando `getMyMember` y `changeMyContact` de `MyMemberApi` con mappers en `B/community/infrastructure/in/rest/`, con `TransactionRetry` y `ActorResolver` (data-model.md `Member.contact`, `ConsentRecord.recordedBy`)

### Cliente

- [x] T083 [P] [US5] Tests de componente en `A/screens/account/__tests__/ContactScreen.test.tsx` y `A/screens/account/__tests__/AccountScreen.test.tsx`: fila «Mis datos de contacto» en Cuenta; formulario con `FormField` y un interruptor «Compartir» por campo, validación con formato previo, ejemplo y sin borrar lo escrito, nombre y comunidad no editables, origen del consentimiento anotado por el moderador visible con «usuario eliminado» cuando proceda, `StateView` con explicación de cómo pedir el vínculo si no hay hermano; el aviso de datos usa el texto de recursos de T111; textos ≥ 18 px y objetivos ≥ 48 px (FR-014, FR-022, FR-024, SC-004)
- [x] T084 [US5] Hook `use-own-member`, `A/screens/account/contact.tsx`, ruta `A/app/(app)/(tabs)/cuenta/contacto.tsx`, fila Mis datos de contacto en `A/screens/account/index.tsx` y textos en `A/i18n/es/members.ts`, reutilizando `lib/members/contact-validation.ts`, solo con tokens de `design-system`

**Checkpoint**: US5 funcional; SC-004 se mide en la prueba manual (quickstart § 3, paso 6).

---

## Phase 8: User Story 6 - El moderador gestiona los hermanos de su comunidad (Priority: P2)

**Goal**: el moderador vincula hermanos con usuarios registrados de su comunidad y los desvincula; el administrador además traslada hermanos entre comunidades (con su usuario) en una sola transacción; el cambio de comunidad desde Usuarios de un usuario vinculado queda bloqueado (FR-030); todo sin interbloqueos.

**Independent Test**: el moderador de la 12 vincula un hermano con un registrado de la 12; el administrador intenta cambiarle la comunidad desde Usuarios y se bloquea; lo traslada desde Hermanos y hermano y usuario cambian juntos; con dos hilos ninguna carrera termina en `40P01`.

**Cubre**: FR-005 (traslado), FR-007 (traslado), FR-016, FR-023, FR-028, FR-030, SC-005, SC-006.

### Tests de dominio

- [x] T085 [P] [US6] Tests de `Member.link`, `unlink` y `transferTo` en `BT/community/domain/MemberLinkTransferTest.java`: `USER_NOT_IN_COMMUNITY` si `user.communityId != communityId`, `MEMBER_ALREADY_LINKED`, desvincular, `transferTo` con `checkAcceptsNewMembers` del destino, `RESPONSIBLE_REQUIRED` con la comunidad origen bloqueada, y `MemberAccessPolicy` para vincular, desvincular y trasladar (moderador solo `REGISTERED` de su comunidad, no desvincula al propio vinculado, trasladar solo administrador) (FR-007, FR-016, FR-023, FR-028; data-model.md `Member.communityId`, `Member.linkedUserId`)

### Implementación de dominio

- [x] T086 [US6] `Member.link`, `unlink`, `transferTo` y las reglas de vincular, desvincular y trasladar en `MemberAccessPolicy`, en `B/community/domain/` (data-model.md `Member.communityId`, `Member.linkedUserId`)

### Tests de aplicación

- [x] T087 [P] [US6] Tests de `LinkMemberService`, `UnlinkMemberService`, `TransferMemberService` y de la implementación de `UserLinkGuard` (`GuardUserLinkService`) con fakes que cambian la fila entre la lectura previa y el bloqueo, en `BT/community/application/LinkTransferServicesTest.java`: vincular bloquea comunidad, hermano y usuario (`lockUser`) en ese orden, `USER_NOT_FOUND` si el usuario no existe, moderador con moderador, administrador o usuario de otra comunidad `CommunityForbidden`, administrador con usuario de otra comunidad `USER_NOT_IN_COMMUNITY`; traslado bloquea origen y destino por id, relee el destino activo y el hermano y lanza `StaleLockOrderException` si ya no está en el origen; `moveUser` falla con `IdentityRuleViolation` (`LAST_MODERATOR`) y no cambia nada; `allowsCommunityChange` y la conversión de `IdentityRuleViolation` a errores de `community` (FR-005, FR-016, FR-028, FR-030, R15; data-model.md `Member.communityId`, `Member.linkedUserId`)

### Implementación de aplicación

- [x] T088 [US6] Puertos de entrada `LinkMember`, `UnlinkMember`, `TransferMember` y `GuardUserLink`, ampliación de `UserDirectory` (`lockUser`, `moveUser`) y de `MemberRepository` (`lock`, `findByLinkedUser`) y servicios transaccionales con el orden de bloqueos `community` → `community_member` → `identity` en `B/community/application/` (R4, R15)

### Tests de adaptadores

- [x] T089 [P] [US6] Tests MockMvc y Testcontainers con `identity` real en `BT/community/infrastructure/LinkTransferControllerIT.java`: `PUT /api/members/{id}/link` registrado de A por moderador de A `200`, moderador con moderador, administrador o usuario de B `403`, administrador con usuario de B `422 USER_NOT_IN_COMMUNITY`; `DELETE .../link` administrador `204`, moderador con registrado de su comunidad `204`, el propio vinculado `403`; `POST .../transfer` solo administrador (moderador `403`), hermano y usuario pasan a B juntos, destino desactivado `422 COMMUNITY_DEACTIVATED`, responsable `409 RESPONSIBLE_REQUIRED`, último moderador `409 LAST_MODERATOR` y nada cambia; renombrar o vincular no copia nombres entre hermano y usuario (FR-005, FR-007, FR-016, FR-028, FR-029; data-model.md `Member.communityId`, `Member.linkedUserId`)
- [x] T090 [P] [US6] Tests Testcontainers de `UserLinkGuard` en `BT/community/infrastructure/UserLinkGuardIT.java` con `identity` real: `PUT /api/users/{id}` con otra comunidad estando vinculado `409 USER_LINKED_TO_MEMBER`; con el hermano ya trasladado a la comunidad destino se permite; sin vínculo se permite (FR-030, FR-028)
- [x] T091 [P] [US6] Test de concurrencia uno a uno en `BT/community/infrastructure/LinkUniquenessConcurrencyIT.java`: dos hilos vinculan el mismo hermano o el mismo usuario, una gana y la otra recibe `409 MEMBER_ALREADY_LINKED` o `409 USER_ALREADY_LINKED`, nunca dos vínculos (FR-028; data-model.md `Member.linkedUserId`)
- [x] T092 [P] [US6] Tests de concurrencia con dos hilos y barrera en `BT/community/infrastructure/LinkCommunityChangeRaceIT.java` y `LinkVsUserDeletionRaceIT.java`: vincular frente a cambio de comunidad desde `identity` nunca deja un vínculo entre comunidades distintas ni `40P01`; vincular frente a borrar el usuario nunca deja `community_member.user_id` apuntando a un usuario inexistente y el vínculo tardío recibe `422 USER_NOT_FOUND`; con `lockUser`, vincular frente a cambio de rol a moderador nunca deja al moderador vinculado por otro moderador (FR-028, FR-030, R15; data-model.md `Member.linkedUserId`)
- [x] T093 [P] [US6] Tests de concurrencia con dos hilos y barrera en `BT/community/infrastructure/TransferRaceIT.java`: trasladar frente a desactivar el destino nunca mete un hermano en una comunidad desactivada y el traslado tardío recibe `422 COMMUNITY_DEACTIVATED`; traslados cruzados A a B y B a A simultáneos terminan sin `40P01` (bloqueo por id) (FR-005, FR-016, R15)
- [x] T094 [P] [US6] Tests de concurrencia con dos hilos y barrera en `BT/community/infrastructure/UserDeletionVsTransferRaceIT.java`: borrar el usuario (lectura previa en A, invocado a través de `TransactionRetry` de `identity`) frente a trasladar su hermano; una espera a la otra, sin `40P01` y resultado coherente (usuario borrado y hermano desvinculado en el origen, o ambos trasladados y después usuario borrado); con el traslado A a B confirmado mientras el borrado espera, este relee el usuario en B, reintenta, bloquea B y evalúa `LAST_MODERATOR` contra B, nunca contra A (FR-016, FR-018, FR-028, R15)

### Implementación de adaptadores

- [x] T095 [US6] `linkMember`, `unlinkMember` y `transferMember` en `MembersController`, y el adaptador de entrada `UserLinkGuard` (`allowsCommunityChange`) en `B/community/infrastructure/in/identity/` (con `releaseBeforeDeletion` en US7), más la consulta de `lockUser` y `moveUser` en `B/community/infrastructure/out/identity/` y los mappers, con `TransactionRetry` (data-model.md `Member.communityId`, `Member.linkedUserId`)

### Cliente

- [x] T096 [P] [US6] Tests de componente en `A/screens/members/__tests__/LinkTransferScreens.test.tsx` y `A/screens/users/__tests__/UserEditScreen.test.tsx`: pantallas de vincular (solo usuarios registrados de la comunidad) y de trasladar (solo administrador, el moderador no ve la acción) con `RadioSection` y confirmación accesible, mensajes de `USER_LINKED_TO_MEMBER`, `COMMUNITY_DEACTIVATED`, `RESPONSIBLE_REQUIRED`, `LAST_MODERATOR`, `MEMBER_ALREADY_LINKED`, `USER_ALREADY_LINKED` y `USER_NOT_IN_COMMUNITY`; la edición de usuario indica trasladar al hermano en lugar de cambiar la comunidad; controles con rol y etiqueta, objetivos ≥ 48 px (FR-016, FR-028, FR-030, FR-024)
- [x] T097 [US6] Mutaciones y hooks en `A/hooks/members/`, pantallas de vincular y trasladar en `A/screens/members/`, rutas `A/app/(app)/(tabs)/gestion/hermanos/{vincular,trasladar}.tsx`, traducción de `USER_LINKED_TO_MEMBER` y `COMMUNITY_DEACTIVATED` en `A/screens/users/edit.tsx` y textos en `A/i18n/es/members.ts`, solo con tokens de `design-system`

**Checkpoint**: US6 funcional; ninguna carrera de R15 termina en `40P01` ni deja vínculos incoherentes.

---

## Phase 9: User Story 7 - Supresión, rectificación y copia de datos (Priority: P2)

**Goal**: borrar un hermano es definitivo y atómico con su usuario vinculado; la copia de datos incluye el registro de consentimiento con su autor; borrar un usuario en `identity` desvincula antes a su hermano.

**Independent Test**: el moderador borra con confirmación un hermano vinculado a un registrado: desaparecen hermano, consentimientos y usuario (sesiones `401`); borrar un usuario vinculado en Usuarios deja al hermano sin vínculo y con su nombre intacto; la copia muestra el autor de cada consentimiento.

**Cubre**: FR-017, FR-018, FR-019 (registro), FR-020, FR-027, FR-028 (desvínculo al borrar), SC-008.

### Tests de dominio y aplicación

- [x] T098 [P] [US7] Tests de `DeleteMemberService`, `ExportMemberDataService` y del desvínculo previo al borrado (`GuardUserLink.releaseBeforeDeletion`) con fakes en `BT/community/application/DeleteExportServicesTest.java`: moderador borra los de su comunidad sin usuario o vinculados a `REGISTERED`, no el suyo ni uno vinculado a moderador o administrador (`CommunityForbidden`), administrador con las reglas de `identity` (`SELF_ACTION_FORBIDDEN`, `LAST_MODERATOR`, `LAST_ADMINISTRATOR`), `RESPONSIBLE_REQUIRED` si es el responsable, bloqueo `community` → `community_member` → `identity`, el borrado llama a `deleteUser` en la misma transacción y nada cambia si `identity` rechaza; `releaseBeforeDeletion` desvincula, relee la comunidad del hermano y lanza `StaleLockOrderException` si cambió; la copia incluye todo lo guardado y el registro con `recordedBy` (`deleted` y nombre) y nada más; copia solo moderador de su comunidad o administrador (FR-017, FR-018, FR-020, FR-027, R15, R17; data-model.md `ConsentRecord.recordedBy`, `Member.linkedUserId`)

### Implementación de aplicación

- [x] T099 [US7] Puertos de entrada `DeleteMember` y `ExportMemberData`, y servicios transaccionales con el orden de bloqueos y `releaseBeforeDeletion` en `GuardUserLinkService`, en `B/community/application/` (R4, R15)

### Tests de adaptadores

- [x] T100 [P] [US7] Tests MockMvc y Testcontainers con `identity` real en `BT/community/infrastructure/MemberDeletionIT.java`: `DELETE /api/members/{id}` `204` sin fila de hermano ni de consentimiento (SC-008); vinculado a un registrado, el usuario se borra y sus sesiones dan `401`; moderador con el suyo o con uno de moderador `403`; borrar al responsable `409 RESPONSIBLE_REQUIRED`; administrador `409 SELF_ACTION_FORBIDDEN`, `LAST_MODERATOR` o `LAST_ADMINISTRATOR` según 002; el historial de reservas y eventos del usuario borrado queda anonimizado por `UserDeleted` (FR-017, FR-018, SC-008; data-model.md `Member.linkedUserId`, `ConsentRecord.recordedBy`)
- [x] T101 [P] [US7] Tests Testcontainers del desvínculo al borrar un usuario en `BT/community/infrastructure/UserDeletionUnlinkIT.java`: borrar un usuario vinculado desde `identity` desvincula antes al hermano (sigue, `linkedUserId` vacío, nombre intacto) y, si `identity` rechaza el borrado (`LAST_MODERATOR`), el vínculo sigue; no hay listener de `UserDeleted` en `community` (FR-028, R4)
- [x] T102 [P] [US7] Test de concurrencia en `BT/community/infrastructure/UserDeletionVsMemberDeletionRaceIT.java`: borrar usuario (002) frente a borrar su hermano con dos hilos, sin `40P01`; una termina y la otra recibe `404` o `409` sin efectos parciales (FR-018, R15)
- [x] T103 [P] [US7] Tests MockMvc de la copia en `BT/community/infrastructure/MemberDataExportControllerTest.java`: `GET /api/members/{id}/data-export` con todo lo guardado y el registro de consentimiento (campo, decisión, canal, aviso, fecha y `recordedBy` con rol y nombre, `deleted = true` sin nombre tras borrar al moderador, `MEMBER` sin nombre) y sin valores en el registro; moderador de otra comunidad `404`, registrado `403`; rectificar un valor no deja el anterior en ninguna tabla (FR-020, FR-027, SC-008, R17; data-model.md `ConsentRecord.recordedBy`)

### Implementación de adaptadores

- [x] T104 [US7] `deleteMember` y `exportMemberData` en `MembersController`, `releaseBeforeDeletion` en el adaptador `UserLinkGuard` de `B/community/infrastructure/in/identity/`, `deleteUser` en `B/community/infrastructure/out/identity/` y los mappers de la copia, con `TransactionRetry` (data-model.md `Member.linkedUserId`, `ConsentRecord.recordedBy`)

### Cliente

- [x] T105 [P] [US7] Tests de componente y de texto en `A/screens/members/__tests__/MemberDeleteExportScreen.test.tsx` y `A/lib/members/__tests__/data-export-text.test.ts`: borrado con confirmación accesible y definitiva (rol `alertdialog`, foco y etiquetas) que avisa de que el usuario vinculado también se borra, copia de datos legible con el autor de cada consentimiento («usuario eliminado» cuando proceda) y compartible con `Share`, errores de `SELF_ACTION_FORBIDDEN`, `LAST_MODERATOR` y `LAST_ADMINISTRATOR` localizados; objetivos ≥ 48 px y textos ≥ 18 px (FR-017, FR-020, FR-024)
- [x] T106 [US7] `A/lib/members/data-export-text.ts`, mutaciones en `A/hooks/members/`, pantalla de copia y confirmación de borrado en `A/screens/members/`, ruta `A/app/(app)/(tabs)/gestion/hermanos/copia.tsx` y textos en `A/i18n/es/members.ts`, solo con tokens de `design-system`

**Checkpoint**: US7 funcional; tras borrar un hermano no queda dato personal suyo recuperable (SC-008).

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: documentación, aviso legal, privacidad en logs, accesibilidad manual, prueba manual y gates.

### Tests (escribir primero, ver fallar)

- [x] T107 [P] Test de logs transversal en `BT/community/infrastructure/NoPersonalDataInLogsIT.java`: con el logger capturado, recorrer alta, edición, retirada, vínculo, traslado, borrado y copia con nombres, apellidos, teléfonos y correos sintéticos y comprobar que ninguno aparece en la salida (FR-021, SC-009)
- [x] T108 [P] Test de aislamiento transversal en `BT/community/infrastructure/CommunityIsolationIT.java`: matriz de permisos de todas las operaciones de `CommunitiesApi`, `MembersApi` y `MyMemberApi` por rol y comunidad con la cadena de seguridad real; 0 casos en que un registrado o un moderador ve o modifica hermanos de otra comunidad (SC-005)

### Implementation

- [x] T109 Actualizar `.agents/context/dominio.md` con las decisiones de la spec: «Hermano → `Member`», el estado explícito «sin responsable», contacto con consentimiento por campo (móvil, fijo y correo) y quitar dirección y sexo del glosario del hermano (plan.md Pendientes)
- [ ] T110 **Para el usuario**: redactar el texto legal del aviso de datos de contacto (qué datos se guardan, para qué, quién los ve y base legal) y entregarlo para los recursos del cliente; la tarea la hace el usuario, no el asistente, y bloquea el cierre de T111
- [ ] T111 Incorporar el texto del usuario al aviso de datos en `A/i18n/es/members.ts` y mostrarlo en el alta con consentimiento verbal o escrito, en Mis datos de contacto y en la copia de datos (FR-022); hasta recibir el texto, las pantallas usan un marcador sin contenido legal inventado
- [ ] T112 Revisión manual de accesibilidad (FR-024, principio VII) según quickstart.md § 3, paso 10, en las pantallas de directorio, ficha, comunidades, hermanos y Mis datos de contacto: VoiceOver y TalkBack con etiquetas en cada acción (incluidas llamar y escribir), fuente al 200 % sin cortes, movimiento reducido, contraste AA en claro y oscuro, objetivos ≥ 48 px y texto ≥ 18 px
- [ ] T113 Ejecutar la prueba manual de quickstart.md § 3 con 80 hermanos sintéticos y cronometrar a una persona que no conoce las pantallas: SC-001 (paso 1, < 3 min), SC-002 (paso 3, < 2 min) y SC-003 (paso 5, < 30 s), anotando tiempo y si necesitó ayuda; y la comprobación de SC-004 (paso 6): al menos 9 de 10 personas distintas, incluidas mayores de 70 si es posible, guardan su contacto sin error ni ayuda
- [ ] T114 Completar la prueba manual de quickstart.md § 3, pasos 2, 4, 7, 8 y 9 (usuarios, rectificación y retirada, traslado con su usuario, desactivar y reactivar con `Reservas → Pendientes` mostrando el número, copia y borrado con «usuario eliminado»), y repasar uno a uno FR-001 a FR-030 y SC-001 a SC-009, diciendo cuáles se cumplen y cuáles no
- [ ] T115 Gates finales: `npx --yes @redocly/cli@2.76.0 lint api/openapi.yaml --config api/redocly.yaml`, `cd backend && ./mvnw -B verify` (con `TESTCONTAINERS_RYUK_DISABLED=true` si hace falta; incluye `ArchitectureTest` y `ModularityTest`), `cd app && npm run lint && npm run typecheck && npm test && npx expo export -p web` y `npm run api:check`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias; la fusión del contrato va primero. Tras T004 `GET /api/communities` no responde hasta T043.
- **Foundational (Phase 2)**: depende de Setup y bloquea todas las historias. Dentro de la fase, `V5` y `V6` antes de los repositorios de `identity` y `community`, y los tipos de la raíz de `identity` antes de sus servicios.
- **User Stories (Phase 3+)**: dependen de Foundational. Orden de entrega: US1, US3, US2 (P1), US4, US5, US6, US7 (P2). US3 va antes que US2 porque designar responsable exige hermanos.
- **Polish (Phase 10)**: depende de las historias deseadas; el texto legal del usuario bloquea T111.

### User Story Dependencies

- **US1**: solo Foundational; incluye `SpacesApi.hasBookingRequestsFor`, `AgendaApi.hasEventsFor` y el cambio de `pending-requests` de 001.
- **US3**: US1 (comunidad activa donde dar de alta).
- **US2**: US1 y US3 (comunidad y hermanos).
- **US4**: US3 (lista y ficha de hermanos).
- **US5**: US3; su prueba completa con vínculo requiere US6 (aprovecha el vínculo ya sembrado en tests).
- **US6**: US1 y US3; la política de traslado y de responsable usa US2.
- **US7**: US3 y US6 (vínculo y guardián); borrar al responsable usa US2.

### Within Each User Story

- Capa a capa: dominio, aplicación, adaptadores (persistencia, REST), cliente.
- En cada capa el test va antes que la implementación y se ve fallar.
- Modelo antes que servicio, servicio antes que controlador; contrato fusionado y generado antes del backend y del cliente.

### Parallel Opportunities

- En Setup, las generaciones del backend y del cliente y el esqueleto del módulo.
- En Foundational, todos los tests (ficheros distintos) y los pares tipos base de dominio y `ActorResolver`/manejador.
- Dentro de cada historia, los tests de una misma capa.
- Backend y cliente de una historia, una vez fijado el contrato; US4 y US5 en paralelo tras US3; en US6 las carreras de dos hilos se escriben en paralelo.

---

## Parallel Example: User Story 6

```bash
Task: "Tests de Member.link, unlink y transferTo en BT/community/domain/MemberLinkTransferTest.java"

Task: "Test de concurrencia uno a uno en BT/community/infrastructure/LinkUniquenessConcurrencyIT.java"
Task: "Tests de concurrencia vincular frente a cambio de comunidad y a borrar el usuario"
Task: "Tests de concurrencia trasladar frente a desactivar el destino y traslados cruzados"
Task: "Tests de concurrencia borrar usuario frente a trasladar su hermano"

Task: "Tests de componente en A/screens/members/__tests__/LinkTransferScreens.test.tsx"
```

---

## Implementation Strategy

### MVP First (US1 + US3 + US2)

1. Phase 1 Setup y Phase 2 Foundational.
2. Phase 3 US1 (comunidades), Phase 4 US3 (hermanos y contacto) y Phase 5 US2 (responsable): las tres son P1 y juntas dan de alta comunidades con su responsable y hermanos con consentimiento.
3. **STOP y validar**: quickstart.md § 3 pasos 1 y 3 (SC-001 y SC-002), `./mvnw -B verify`.
4. Demo con un administrador y un moderador.

### Incremental Delivery

1. Setup + Foundational lista (`identity` ampliado, guardián y reintento).
2. US1, US3 y US2: MVP.
3. US4: directorio y pestaña «Comunidad» (SC-003).
4. US5: Mis datos de contacto (SC-004).
5. US6: vínculo, traslado y todas las carreras de R15.
6. US7: borrado, desvínculo y copia de datos.
7. Polish: docs, aviso legal, accesibilidad manual, prueba manual cronometrada y gates.

### Parallel Team Strategy

1. Un equipo cierra Setup + Foundational.
2. Con US1 cerrada: backend y cliente de US3 en paralelo; con US3, US4 y US5 en paralelo; US6 y US7 después, porque comparten `UserLinkGuard` y el orden de bloqueos.

---

## Notes

- [P] = ficheros distintos, sin dependencias.
- [Story] enlaza la tarea con su historia.
- Verificar que cada test falla antes de implementar.
- Cada historia es entregable por separado salvo las dependencias indicadas.
- Nada generado se edita a mano; `backend/pom.xml` no cambia.
- Sin comentarios en código ni configuración; textos de interfaz en `A/i18n/es`; ni datos reales ni secretos en el repo.
