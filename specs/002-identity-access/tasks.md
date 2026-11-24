# Tasks: Identidad y acceso

**Input**: Design documents from `/specs/002-identity-access/`

**Prerequisites**: plan.md, spec.md, research.md (R1-R15), data-model.md, contracts/identity.openapi.yaml, contracts/identity-module-api.md, quickstart.md

**Tests**: obligatorios (constitución IV, regla 3 del proyecto): cada regla de negocio nace de un test que falla. Dentro de cada historia el orden es capa a capa: dominio, aplicación, adaptadores, contrato/API, cliente; en cada capa el test va antes que su implementación y debe verse fallar.

**Organization**: tareas agrupadas por historia de usuario (US1-US4). Cero comentarios en código y configuración.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede ejecutar en paralelo (ficheros distintos, sin dependencias)
- **[Story]**: US1, US2, US3, US4
- Las citas `data-model.md Entidad.campo` indican el campo restringido que la tarea implementa o valida

## Path Conventions

- `B/` = `backend/src/main/java/io/github/escumartin/neocatecumenal/identity/`
- `BT/` = `backend/src/test/java/io/github/escumartin/neocatecumenal/identity/`
- `BR/` = `backend/src/main/resources/`
- `A/` = `app/src/`
- Contrato: `api/openapi.yaml` (fuente de verdad; borrador en `specs/002-identity-access/contracts/identity.openapi.yaml`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: contrato primero, dependencias y esqueleto de módulo

- [x] T001 Fusionar `specs/002-identity-access/contracts/identity.openapi.yaml` en `api/openapi.yaml` (13 operaciones, esquemas `Problem`/`ValidationProblem`, `bearerAuth` y `cookieAuth`) y validar con Redocly (`api/redocly.yaml`)
- [x] T002 Regenerar tipos del cliente en `A/api/schema.d.ts` (`npm run api:generate` y `npm run api:check`) y comprobar que el generador `spring` produce las interfaces en `backend/target/generated-sources` (`./mvnw -B generate-sources`)
- [x] T003 Añadir a `backend/pom.xml` `spring-boot-starter-security`, `org.bouncycastle:bcprov-jdk18on` y, en test, `spring-security-test`
- [x] T004 [P] Crear el esqueleto del módulo en `B/` (paquetes `domain`, `application/port/in`, `application/port/out`, `application/service`, `infrastructure/{in/rest,in/security,in/bootstrap,out/persistence,out/security,config}`) con `package-info.java` de Modulith
- [x] T005 [P] Añadir al cliente `openapi-fetch` y `expo-secure-store` en `app/package.json`
- [x] T006 [P] Añadir herramientas de test de cliente (`jest-expo`, `@testing-library/react-native`) y el script `npm test` en `app/package.json` (ver hallazgo F-02 del análisis)
- [x] T007 [P] Copiar a mano la lista de contraseñas comunes de SecLists revisada a `BR/identity/common-passwords.txt` y su licencia MIT a `BR/identity/THIRD-PARTY-NOTICES`
- [x] T008 Ampliar `backend/src/test/java/io/github/escumartin/neocatecumenal/ArchitectureTest.java` con la regla de Jackson fuera de `domain` y la de `application` sin dependencia de `infrastructure`; y comprobar que quedan verdes con el esqueleto

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: esquema, tipos de dominio compartidos, API pública, errores y seguridad base. Bloquea todas las historias.

**CRITICAL**: ninguna historia empieza hasta cerrar esta fase.

### Tests (escribir primero, ver fallar)

- [x] T009 [P] Tests de value objects en `BT/domain/ValueObjectsTest.java`: `AccessId.value` entre 100000 y 999999, `CommunityNumber.value` > 0, `DisplayName.value` NFC, recortado, 1-100 caracteres, `PlainPassword.toString` enmascarado, `PrivacyConsent` con versión y fecha ambas presentes (data-model.md `User.accessId`, `User.name`, `Community.number`, `PrivacyConsent`)
- [x] T010 [P] Tests de `UserRole.includes` (herencia FR-005) en `BT/domain/UserRoleTest.java` y de `Role.includes` exportado en `BT/RoleTest.java`
- [x] T011 [P] Tests de `User.register` (`passwordChangeRequired = true`, sin consentimiento) y de `User.pendingSteps(currentNoticeVersion)` con orden fijo aviso y luego contraseña, en `BT/domain/UserTest.java` (data-model.md `User.passwordChangeRequired`, `User.privacyConsent`)
- [x] T012 [P] Test de Flyway y restricciones SQL con Testcontainers en `BT/infrastructure/SchemaMigrationIT.java`: unicidad `uk_identity_user_access_id`, `check (access_id between 100000 and 999999)`, `check (role in ...)`, `check (privacy_notice_version is null) = (privacy_notice_accepted_at is null)`, `identity_community.number` único y > 0, cascada de `identity_session` (data-model.md `identity_user.access_id`, `identity_user.role`, `identity_community.number`)
- [x] T013 [P] Test de `Argon2PasswordHasher` en `BT/infrastructure/Argon2PasswordHasherTest.java`: prefijo `{argon2}`, NFC, `matches`, `matchesDummy` (FR-015, FR-002)
- [x] T014 [P] Test de generadores en `BT/infrastructure/GeneratorsTest.java`: `AccessIdGenerator` dentro de 100000-999999 con `SecureRandom`; `InitialPasswordGenerator` sin caracteres confusos (R6) y válida para la política; `Sha256SessionTokenIssuer` de 256 bits y hash SHA-256 (R2, R11)
- [x] T015 [P] Test MockMvc de `ProblemDetailsHandler` y de `IdentitySecurityConfig` en `BT/infrastructure/SecurityBaselineTest.java`: `401` y `403` como Problem Details con `code` estable, solo login y health como `permitAll`, resto exige autenticación (R3, R13)

### Implementation

- [x] T016 Migración `BR/db/migration/V2__identity.sql` con `identity_community`, `identity_user`, `identity_session`, `identity_login_pair_throttle`, `identity_login_ip_throttle` e índices, según data-model.md Persistencia
- [x] T017 [P] Value objects y `Actor` en `B/domain/` (`UserId`, `CommunityId`, `AccessId`, `CommunityNumber`, `DisplayName`, `UserRole`, `PlainPassword`, `PasswordHash`, `PrivacyConsent`, `Actor`) y excepciones de dominio con `code` estable
- [x] T018 Agregado `User` y `Community` en `B/domain/User.java` y `B/domain/Community.java` (`register`, `pendingSteps`; `Community.number` único, sin responsable)
- [x] T019 [P] API pública del módulo en `B/IdentityApi.java`, `B/CurrentUser.java`, `B/Role.java`, `B/CommunityRef.java`, `B/UserDeleted.java` según contracts/identity-module-api.md
- [x] T020 [P] Puertos de salida en `B/application/port/out/`: `UserRepository`, `CommunityRepository`, `SessionRepository`, `PasswordHasher`, `AccessIdGenerator`, `InitialPasswordGenerator`, `SessionTokenIssuer`, `CurrentSessionContext`, `IdentityEventPublisher`, `PrivacyNoticeSettings`, `CommonPasswordsProvider`
- [x] T021 [P] Entidades JPA y repositorios Spring Data de `identity_user`, `identity_community` e `identity_session` con bloqueo optimista por `User.version`, mappers y adaptadores `UserRepository`, `CommunityRepository`, `SessionRepository` en `B/infrastructure/out/persistence/` (data-model.md `User.version`, `Session.tokenHash`)
- [x] T022 [P] `Argon2PasswordHasher` (m = 19 MiB, t = 2, p = 1, NFC, `matchesDummy`), generadores aleatorios y `Sha256SessionTokenIssuer` en `B/infrastructure/out/security/`
- [x] T023 [P] Configuración en `B/infrastructure/config/`: `Clock`, propiedades `identity.*` (versión del aviso vigente, orígenes permitidos, bootstrap desactivado por defecto) y `PrivacyNoticeSettings`
- [x] T024 `ProblemDetailsHandler` en `B/infrastructure/in/rest/` y `SecurityFilterChain` base con `permitAll` fijo para `POST /api/sessions` y `GET /api/health`, `forward-headers-strategy=none` en `B/infrastructure/in/security/` y `B/infrastructure/config/`

**Checkpoint**: `./mvnw -B verify` en verde con la base; las historias pueden empezar.

---

## Phase 3: User Story 1 - Iniciar y cerrar sesión (Priority: P1) 🎯 MVP

**Goal**: entrar con identificador y contraseña, ser reconocido en cada petición, cerrar sesión, con bloqueo por fallos y caducidad de sesión.

**Independent Test**: con un usuario de prueba precargado, iniciar sesión, ver nombre y rol en `GET /api/me`, cerrar sesión y volver a ser visitante.

**Cubre**: FR-001, FR-002, FR-003, FR-004 (parcial, vía `GET /api/me`), FR-026, FR-028 (retención de IP), SC-001, SC-007 (login).

### Tests de dominio

- [x] T025 [P] [US1] Tests de `Session` en `BT/domain/SessionTest.java`: `isValid` a 30 días sin uso y a 90 días absolutos, `touch` solo si pasan ≥ 5 min, reloj fijo (FR-026; data-model.md `Session`)
- [x] T026 [P] [US1] Tests de `LoginThrottle` en `BT/domain/LoginThrottleTest.java`: `reserve` cuenta antes de verificar, 5 seguidos del par bloquean 15 min, 20 de la IP en 15 min bloquean la IP, fallos de otra IP no bloquean al titular, ventana deslizante, acierto deshace la reserva, `Rejected(retryAfter)`, identificador inexistente cuenta igual, retención `max(último fallo + 15 min, bloqueo)` (FR-003, FR-028; `LoginThrottle`)

### Implementación de dominio

- [x] T027 [P] [US1] Implementar `Session` en `B/domain/Session.java`
- [x] T028 [P] [US1] Implementar `LoginThrottle` y sus estados en `B/domain/LoginThrottle.java`

### Tests de aplicación

- [x] T029 [P] [US1] Tests de `LogInService`, `LogOutService`, `AuthenticateSessionService` y `GetMyProfileService` con fakes y reloj fijo en `BT/application/LogInServiceTest.java` y `BT/application/AuthenticateSessionServiceTest.java`: credenciales correctas crean sesión, mismo error para inexistente (con `matchesDummy`) y contraseña incorrecta, bloqueo con `retryAfter`, usuario borrado o sesión caducada no autentican, renovación deslizante (FR-001, FR-002, FR-003, FR-026)

### Implementación de aplicación

- [x] T030 [US1] Puertos de entrada `LogIn`, `LogOut`, `AuthenticateSession`, `GetMyProfile` y puerto de salida `LoginThrottleStore` (`lockIp`, `lockPair` con IP antes que par, `save`, `deletePair`, `purgeExpired`) en `B/application/port/`
- [x] T031 [US1] Servicios `LogInService`, `LogOutService`, `AuthenticateSessionService`, `GetMyProfileService` en `B/application/service/` (transaccionales, reserva del intento antes de verificar)

### Tests de adaptadores

- [x] T032 [P] [US1] Tests Testcontainers de `LoginThrottleStore` en `BT/infrastructure/LoginThrottlePersistenceIT.java`: bloqueo `FOR UPDATE`, 30 logins erróneos simultáneos del mismo par verifican ≤ 5 veces (`PasswordHasher` espía) y 40 desde la misma IP con identificadores distintos ≤ 20, acierto borra la fila del par y no suma a la IP, 25 logins correctos no bloquean (FR-003, R7)
- [x] T033 [P] [US1] Test de purga en `BT/infrastructure/ThrottlePurgeIT.java`: se eliminan filas al pasar `max(último fallo + 15 min, blockedUntil)` y sesiones caducadas (FR-028, FR-026)
- [x] T034 [P] [US1] Tests MockMvc con la cadena de seguridad real en `BT/infrastructure/SessionsControllerTest.java`: `POST /api/sessions` `201` con `tokenDelivery` BODY y COOKIE (cookie `HttpOnly`, `Secure`, `SameSite=Strict`, sin token en cuerpo), `401 INVALID_CREDENTIALS` idéntico para inexistente y errónea, `429` con `Retry-After`, `DELETE /api/sessions/current` `204` y el mismo token da `401`, visitante en ruta protegida `401`, comprobación de `Origin` con cookie, `GET /api/me` (US1-1..5)
- [x] T035 [P] [US1] Test de logs en `BT/infrastructure/LoginLogsTest.java`: con el logger capturado, login correcto, fallido y bloqueado no emiten nombre, identificador de acceso, contraseña, token, cookie ni IP (SC-007, R14)

### Implementación de adaptadores

- [x] T036 [US1] Adaptador `LoginThrottleStore` con entidades de `identity_login_pair_throttle` e `identity_login_ip_throttle` en `B/infrastructure/out/persistence/` (data-model.md `identity_login_pair_throttle.consecutive_failures`, `identity_login_ip_throttle.recent_failures` con tope de 20)
- [x] T037 [P] [US1] `ClientIpResolver`, `SessionCookies`, `OriginCheck` y `SecurityContextCurrentSession` en `B/infrastructure/in/security/`
- [x] T038 [US1] `SessionAuthenticationFilter` que resuelve token y usuario en una consulta, renueva `last_used_at` y alimenta `CurrentSessionContext`, y su integración en `SecurityFilterChain` en `B/infrastructure/in/security/`
- [x] T039 [US1] `SessionsController` y `MeController` (`GET /api/me`) implementando las interfaces generadas, con mappers, en `B/infrastructure/in/rest/`
- [x] T040 [P] [US1] Purgas programadas de sesiones y fallos en `B/infrastructure/config/`

### Carga inicial (permite probar US1 de punta a punta)

- [x] T041 [P] [US1] Tests en `BT/infrastructure/BootstrapRunnerIT.java`: sin consola aborta con código ≠ 0, sin filas en `identity_community` ni `identity_user` y sin credencial en salida estándar ni logs; con consola falsa la credencial solo sale por ella; repetir no crea otro administrador (R12, SC-007)
- [x] T042 [US1] Puerto `BootstrapIdentity`, servicio e `IdentityBootstrapRunner` con proveedor de consola inyectable en `B/application/` y `B/infrastructure/in/bootstrap/`, desactivado por defecto

### Cliente (contrato ya fusionado en T001-T002)

- [x] T043 [P] [US1] Tests de `A/lib/auth/session-store` y del interceptor de `A/lib/api/client` en `A/lib/auth/__tests__/` y `A/lib/api/__tests__/`: bearer añadido, `401` lleva al login, `403` con paso pendiente redirige
- [x] T044 [P] [US1] Tests de `useSession` y de la pantalla de login en `A/hooks/session/__tests__/` y `A/screens/auth/__tests__/login.test.tsx`: mensaje genérico, tiempo de espera en bloqueo, mostrar u ocultar contraseña, etiquetas (FR-021)
- [x] T045 [US1] Cliente `openapi-fetch` con interceptor en `A/lib/api/` y almacén de sesión (`expo-secure-store` en nativo, nada en web) en `A/lib/auth/`
- [x] T046 [US1] Hook `useSession` en `A/hooks/session/`, textos y mensajes por `code` en `A/i18n/es/identity.*`
- [x] T047 [US1] Pantalla de login en `A/screens/auth/login.tsx` y ruta `A/app/(auth)/login.tsx`, solo con tokens de `design-system`, texto ≥ 18 px, objetivos ≥ 48 px
- [x] T048 [US1] Guardia de sesión en `A/app/(app)/_layout.tsx`: sin sesión lleva al login, cierre de sesión disponible (US1-3, US1-4)

**Checkpoint**: US1 probable de forma independiente; `./mvnw -B verify` y gates de app en verde.

---

## Phase 4: User Story 2 - Permisos por rol y comunidad (Priority: P1)

**Goal**: las demás features conocen usuario, rol y comunidad; permisos con herencia y aislamiento por comunidad; el administrador elige comunidad.

**Independent Test**: un usuario de cada rol frente a acciones protegidas de prueba; el moderador no alcanza otra comunidad.

**Cubre**: FR-004, FR-005, FR-006, FR-007, FR-008, FR-025, SC-003, SC-004.

### Tests de dominio

- [x] T049 [P] [US2] Tests de la matriz completa de `UserManagementPolicy` (actor, objetivo, cambio; cada rol por cada acción de la tabla de data-model.md, moderador solo `REGISTERED` y solo su comunidad, rol y comunidad solo administrador) en `BT/domain/UserManagementPolicyTest.java` (FR-006, FR-010, SC-003, SC-004)

### Implementación de dominio

- [x] T050 [US2] Implementar `UserManagementPolicy` en `B/domain/UserManagementPolicy.java`

### Tests de aplicación

- [x] T051 [P] [US2] Tests de `IdentityApiService` y `ListCommunitiesService` con fakes en `BT/application/IdentityApiServiceTest.java`: `currentUser()` vacío sin sesión o con pasos pendientes, rol y comunidad leídos de la fila actual, `communities()` ordenadas por número, `community(id)` (FR-004, FR-008, FR-025)

### Implementación de aplicación

- [x] T052 [US2] Puerto `ListCommunities` e `IdentityApiService` (implementa `IdentityApi`) en `B/application/`

### Tests de adaptadores

- [x] T053 [P] [US2] `@ApplicationModuleTest` de `IdentityApi` en `BT/IdentityApiModuleTest.java` (vacío sin sesión o con pasos pendientes; comunidades ordenadas) y verificación de que solo la raíz es accesible desde otros módulos (FR-004, FR-025)
- [x] T054 [P] [US2] Tests MockMvc con Testcontainers en `BT/infrastructure/PermissionsIT.java`: registrado denegado en acción de moderador, moderador A sobre B siempre `403` y `404` solo si no existe, administrador opera en cualquier comunidad, y con la misma sesión el cambio de rol se aplica en la siguiente petición (US2-1..4, FR-008, SC-003, SC-004)
- [x] T055 [P] [US2] Test MockMvc de `GET /api/communities` en `BT/infrastructure/CommunitiesControllerTest.java` (autenticado, ordenadas por número, `401` sin sesión)

### Implementación de adaptadores

- [x] T056 [US2] `CommunitiesController` en `B/infrastructure/in/rest/` y exposición de `IdentityApi` desde los adaptadores de seguridad (`SecurityContextCurrentSession`)

### Cliente

- [x] T057 [P] [US2] Tests de `useCurrentUser` y `useCommunities` y del selector de comunidad para administrador en `A/hooks/{session,communities}/__tests__/` y `A/screens/users/__tests__/community-picker.test.tsx` (FR-007)
- [x] T058 [US2] Hooks `useCurrentUser` y `useCommunities` en `A/hooks/` y selector de comunidad accesible en `A/screens/users/community-picker.tsx`; la UI oculta acciones según rol

**Checkpoint**: US1 y US2 juntas dan el contrato que consume `001-reservas-salones`.

---

## Phase 5: User Story 3 - Alta, edición y borrado de usuarios (Priority: P2)

**Goal**: administrador y moderador gestionan usuarios dentro de sus límites; sin autorregistro; borrado definitivo con confirmación.

**Independent Test**: un moderador crea un registrado en su comunidad, lo edita y lo borra tras confirmar; crear un moderador o un usuario en otra comunidad se deniega.

**Cubre**: FR-009, FR-010, FR-011, FR-012, FR-013, FR-017, FR-018, FR-019, FR-023, FR-024, SC-005, SC-006.

### Tests de dominio

- [x] T059 [P] [US3] Tests de `User.rename`, `changeRole`, `moveTo` y de los invariantes `LAST_ADMINISTRATOR`, `LAST_MODERATOR` (solo rol `MODERATOR`; borrado, cambio de rol y cambio de comunidad) y `SELF_ACTION_FORBIDDEN` en `BT/domain/UserInvariantsTest.java` (FR-018; data-model.md `User.role`, `User.communityId`)

### Implementación de dominio

- [x] T060 [US3] Implementar `rename`, `changeRole`, `moveTo` en `B/domain/User.java` y las comprobaciones de invariantes de conjunto en `B/domain/UserManagementPolicy.java`

### Tests de aplicación

- [x] T061 [P] [US3] Tests de `CreateUserService` en `BT/application/CreateUserServiceTest.java`: moderador crea `REGISTERED` en su comunidad con `accessId` generado de 6 dígitos y contraseña inicial, moderador creando moderador u otra comunidad denegado, administrador cualquier rol y comunidad, datos inválidos sin crear nada, colisión de `AccessId` con generador falso que repite crea ambos con identificadores distintos (US3-1..4, FR-011, FR-012, FR-013; `User.accessId`, `User.name`)
- [x] T062 [P] [US3] Tests de `UpdateUserService`, `DeleteUserService`, `ListUsersService`, `GetUserService` en `BT/application/ManageUsersServicesTest.java`: moderador edita solo nombre de registrados de su comunidad, cambio de rol o comunidad solo administrador, borrado elimina sesiones y publica `UserDeleted`, autoborrado, último moderador y último administrador impedidos (US3-5, US3-6, FR-009, FR-010, FR-017, FR-018, FR-023)

### Implementación de aplicación

- [x] T063 [US3] Puertos `CreateUser`, `UpdateUser`, `DeleteUser`, `ListUsers`, `GetUser` y ampliar `UserRepository` con `lockAdministrators()`, `lockCommunity(id)`, `countModerators(communityId)`, `SessionRepository.deleteAllOf(userId)` en `B/application/port/`
- [x] T064 [US3] Servicios `CreateUserService`, `UpdateUserService`, `DeleteUserService`, `ListUsersService`, `GetUserService` en `B/application/service/`, con reintento de `AccessId` ante colisión y publicación de `UserDeleted` tras el commit

### Tests de adaptadores

- [x] T065 [P] [US3] Tests Testcontainers en `BT/infrastructure/UserConcurrencyIT.java`: dos altas con el mismo identificador generado, dos degradaciones simultáneas del último moderador o administrador (una falla), borrado en cascada de sesiones, `UserDeleted` publicado (FR-012, FR-017, FR-018, R10, R11; `identity_user.access_id`)
- [x] T066 [P] [US3] Tests MockMvc en `BT/infrastructure/UsersControllerTest.java`: `POST /api/users` `201` con contraseña inicial, `422` con códigos de campo, `403` de moderador fuera de límites, `409 SELF_ACTION_FORBIDDEN` / `LAST_MODERATOR` / `LAST_ADMINISTRATOR`, `PUT`, `GET`, `DELETE` `204` y sesión del borrado `401`, listado acotado a comunidad, `POST /api/users` sin sesión `401` y ninguna ruta de autorregistro (FR-011, SC-005, SC-006)
- [x] T067 [P] [US3] Test de logs en `BT/infrastructure/UserManagementLogsTest.java`: alta, edición y borrado no emiten datos personales ni credenciales (SC-007, FR-019)

### Implementación de adaptadores

- [x] T068 [US3] Ampliar el adaptador `UserRepository` con bloqueos de filas y conteos, y `IdentityEventPublisher` con `ApplicationEventPublisher`, en `B/infrastructure/out/persistence/` y `B/infrastructure/config/`
- [x] T069 [US3] `UsersController` (`GET/POST /api/users`, `GET/PUT/DELETE /api/users/{userId}`) y mappers en `B/infrastructure/in/rest/`

### Cliente

- [x] T070 [P] [US3] Tests de `useUsers` y de las pantallas de lista, alta o edición y resultado del alta (identificador y contraseña inicial mostrados una vez), y del diálogo accesible de confirmación de borrado en `A/hooks/users/__tests__/` y `A/screens/users/__tests__/` (FR-017, FR-021)
- [x] T071 [US3] Hook `useUsers` en `A/hooks/users/` y pantallas `A/screens/users/{list,edit,created}.tsx` con rutas `A/app/(app)/users/` y diálogo de confirmación de borrado

**Checkpoint**: US3 funcional; alta en menos de 2 minutos (SC-005) comprobable en la prueba manual.

---

## Phase 6: User Story 4 - Contraseña propia y recuperación (Priority: P2)

**Goal**: contraseña inicial cambiada en el primer acceso, cambio propio, reasignación por moderador o administrador y aviso de datos.

**Independent Test**: un usuario recién creado entra, acepta el aviso, cambia la contraseña inicial y vuelve a cambiarla indicando la actual.

**Cubre**: FR-014, FR-015, FR-016, FR-020, FR-022, FR-027, SC-002.

### Tests de dominio

- [x] T072 [P] [US4] Tests de `PasswordPolicy` en `BT/domain/PasswordPolicyTest.java`: `PASSWORD_TOO_SHORT` con 7 *code points*, `PASSWORD_TOO_LONG` con 65, 64 con tildes aceptada, `PASSWORD_TOO_COMMON` sin distinguir mayúsculas, `PASSWORD_CONTAINS_ACCESS_ID` (FR-015; `PlainPassword.value`)
- [x] T073 [P] [US4] Tests de `User.resetPassword`, `changeOwnPassword`, `acceptPrivacyNotice` y de la versión vigente del aviso en `BT/domain/UserPasswordTest.java` (FR-014, FR-020; `User.passwordChangeRequired`, `User.privacyConsent`)

### Implementación de dominio

- [x] T074 [US4] Implementar `PasswordPolicy`, interfaz `CommonPasswords` y los métodos de `User` en `B/domain/`

### Tests de aplicación

- [x] T075 [P] [US4] Tests de `ChangeOwnPasswordService`, `ResetUserPasswordService`, `AcceptPrivacyNoticeService`, `RejectPrivacyNoticeService` en `BT/application/PasswordServicesTest.java`: cambio exige la actual y política, cierra las demás sesiones y conserva la actual (FR-027), reasignación cierra todas (FR-022), moderador solo registrados de su comunidad, nadie reasigna a sí mismo, rechazo cierra la sesión y el aviso se repite (FR-020, FR-016)

### Implementación de aplicación

- [x] T076 [US4] Puertos `ChangeOwnPassword`, `ResetUserPassword`, `AcceptPrivacyNotice`, `RejectPrivacyNotice`, `SessionRepository.deleteAllOfExcept(userId, sessionId)` y servicios en `B/application/`

### Tests de adaptadores

- [x] T077 [P] [US4] Tests MockMvc en `BT/infrastructure/PendingStepsIT.java`: otras rutas `403 PRIVACY_NOTICE_PENDING` y luego `PASSWORD_CHANGE_REQUIRED`, tras aceptar y cambiar acceso normal, rechazo `204` y token `401`, cambio de contraseña `204` con otra sesión `401` y antigua inválida, intento de cambiar rol o comunidad propios o de reasignarse la contraseña `409 SELF_ACTION_FORBIDDEN`, reasignación `200` con todas las sesiones `401` (US4-1..4, FR-014, FR-016, FR-020, FR-022, FR-027)
- [x] T078 [P] [US4] Tests de `ClasspathCommonPasswords` y de que la columna `password_hash` empieza por `{argon2}` y nunca contiene la contraseña en `BT/infrastructure/PasswordPersistenceIT.java` (FR-015)
- [x] T079 [P] [US4] Test de logs en `BT/infrastructure/PasswordLogsTest.java`: cambio, reasignación y aviso sin datos personales ni contraseñas (SC-007)

### Implementación de adaptadores

- [x] T080 [US4] `ClasspathCommonPasswords` en `B/infrastructure/out/security/` y `PendingStepsAuthorization` en `B/infrastructure/in/security/`
- [x] T081 [US4] `MeController` (`PUT /api/me/password`, `POST /api/me/privacy-notice-acceptance`, `POST /api/me/privacy-notice-rejection`) y `POST /api/users/{userId}/password-reset` en `B/infrastructure/in/rest/`

### Cliente

- [x] T082 [P] [US4] Tests de las pantallas de aviso de datos y cambio de contraseña y de la redirección por pasos pendientes en `A/screens/auth/__tests__/` y `A/app/__tests__/app-layout.test.tsx`: contraseña de hasta 64 caracteres, mostrar u ocultar, errores asociados al campo, aviso antes que contraseña (FR-014, FR-020, FR-021)
- [x] T083 [US4] Pantallas `A/screens/auth/{privacy-notice,change-password}.tsx`, rutas `A/app/(auth)/{privacy-notice,change-password}.tsx`, reasignación de contraseña en `A/screens/users/` y redirección a pasos pendientes en `A/app/(app)/_layout.tsx`; el texto legal del aviso en recursos lo aporta el usuario

**Checkpoint**: las cuatro historias completas.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T084 [P] Confirmar `ApplicationModules.verify()` y `ArchitectureTest` (Jackson fuera de `domain`, `application` sin `infrastructure`) en `backend/src/test/java/io/github/escumartin/neocatecumenal/{ModularityTest,ArchitectureTest}.java`
- [x] T085 [P] Test transversal de logs que recorre login, alta, cambio de contraseña y carga inicial en `BT/infrastructure/NoPersonalDataInLogsIT.java` (SC-007, FR-019)
- [x] T086 [P] Actualizar `.agents/context/dominio.md` con identificador de acceso de 6 dígitos generado, sesión restringida del primer acceso y regla de no dejar una comunidad sin moderador
- [x] T087 Gate backend: `cd backend && ./mvnw -B verify` con Java 21 y `TESTCONTAINERS_RYUK_DISABLED=true`
- [x] T088 Gate app: `cd app && npm run lint && npm run typecheck && npm test && npx expo export -p web`, más `npm run api:check`
- [ ] T089 Prueba manual de `quickstart.md` § 3 (carga inicial, flujo de los tres roles, bloqueo, accesibilidad con fuente al 200 %, tema oscuro y contraste AA; SC-001, SC-002, SC-005)

---

## Dependencies & Execution Order

- Phase 1 sin dependencias; T001 antes de T002, T039, T066 y cualquier código generado.
- Phase 2 depende de Phase 1 y bloquea todas las historias.
- US1 y US2 (P1) tras Phase 2; US2 usa `Session` y el filtro de US1 (T027, T038) para `currentUser()`, así que US2 empieza al cerrar T038.
- US3 depende de US1 (sesión) y US2 (`UserManagementPolicy`, T050).
- US4 depende de US1; la reasignación de contraseña además de US3 (T064).
- Hasta cerrar US4 un usuario con contraseña inicial no queda forzado a cambiarla; no se entrega a producción una versión sin US4.
- Polish tras las historias deseadas.
- Dentro de cada historia: test, implementación, siguiente capa; los tests marcados [P] de una misma capa van en paralelo.

## Parallel Examples

- Phase 1: T004, T005, T006, T007 en paralelo tras T003.
- Phase 2: T009-T015 en paralelo; T017, T019, T020, T021, T022, T023 en paralelo tras sus tests.
- US1: T025 y T026; T032-T035; T043 y T044.
- US3: T059, T061, T062 en paralelo; T065-T067.

## Implementation Strategy

- MVP: Phase 1, Phase 2 y US1 (con T041-T042 para poder sembrar un administrador); validar y parar.
- Después US2 (contrato para `001-reservas-salones`), luego US3 y US4 en orden de prioridad.
- Cada historia deja `verify` y los gates de app en verde antes de empezar la siguiente.
