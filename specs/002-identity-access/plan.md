# Implementation Plan: Identidad y acceso

**Branch**: `002-identity-access-plan` | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-identity-access/spec.md`

## Summary

Los hermanos inician y cierran sesión con su identificador numérico de 6 dígitos y su contraseña. El sistema conoce en
cada petición quién es el usuario, su rol (registrado < moderador < administrador) y su comunidad, y se lo ofrece a las
demás features. El administrador y el moderador (este solo para registrados de su comunidad) dan de alta, editan, borran
y reasignan la contraseña inicial de los usuarios. Sin autorregistro ni recuperación por email.

Enfoque técnico:

- Módulo Spring Modulith `identity` con API pública `IdentityApi` (usuario actual, comunidades) y evento `UserDeleted`.
- Sesiones con token opaco de 256 bits guardado como SHA-256 en PostgreSQL y resuelto en cada petición junto con la fila
  del usuario: rol nuevo en la siguiente acción, borrado y cierre de sesiones inmediatos, caducidad deslizante de 30 días
  con tope de 90. Bearer en nativo y cookie `HttpOnly` en web.
- Spring Security con un filtro propio; sesión restringida mientras falten el aviso de datos o el cambio de la
  contraseña inicial.
- Contraseñas con Argon2id, política de 8 a 64 caracteres, lista local de comunes y sin el identificador.
- Bloqueo por identificador+IP y por IP en tablas de PostgreSQL con retención mínima y purga; el intento se cuenta
  antes de verificar la contraseña, con las filas bloqueadas, para que la concurrencia no dé intentos de más.
- Contrato primero: borrador en `contracts/identity.openapi.yaml`.

## Technical Context

**Language/Version**: Java 25 (backend); TypeScript strict (cliente)

**Primary Dependencies**:

- Backend: Spring Boot 4.2, Spring Modulith 2.x, Spring Data JPA, Flyway, Bean Validation.
- Nuevas: `spring-boot-starter-security`, `org.bouncycastle:bcprov-jdk18on` (Argon2id); en test,
  `spring-security-test`. Sin registro persistente de eventos (R9).
- Generación: `openapi-generator-maven-plugin` (ya en `pom.xml`; generador `spring`, `interfaceOnly`).
- Cliente: Expo 58, Expo Router, NativeWind, React Native Reusables, `openapi-typescript` (ya presente); nuevas
  `openapi-fetch` y `expo-secure-store`.
- Cliente, tests: `jest-expo` y `@testing-library/react-native` (con `jest`), script `npm test`.

**Storage**: PostgreSQL 18; migración `V2__identity.sql`. Claves internas UUID.

**Testing**:

- JUnit 5 y AssertJ sin Spring para dominio (`PasswordPolicy`, `LoginThrottle`, `Session`, `UserManagementPolicy`, `User`)
  y aplicación, con puertos falsos y reloj fijo.
- Testcontainers PostgreSQL: persistencia, bloqueos por fallos, purga, concurrencia (dos degradaciones, dos altas con el
  mismo identificador generado; 30 logins erróneos simultáneos del mismo par verifican ≤ 5 veces y 40 desde la misma
  IP ≤ 20) y cascada de sesiones.
- Carga inicial: sin consola interactiva aborta sin crear nada y sin datos en salida estándar ni logs; con consola
  falsa, la credencial solo sale por ella.
- MockMvc con la cadena de Spring Security real para los adaptadores REST.
- `@ApplicationModuleTest` para `IdentityApi` y la publicación de `UserDeleted`.
- ArchUnit y `ApplicationModules.verify()`; se amplía `ArchitectureTest` con la regla de Jackson en `domain` y la de
  `application` sin `infrastructure`.

**Target Platform**: servidor Linux (JVM) y cliente Expo web, iOS y Android

**Project Type**: web-service + cliente móvil/web

**Performance Goals**: unos 1200 usuarios; resolver la sesión < 10 ms p95; login < 500 ms p95 (Argon2id ≈ 50 ms)

**Constraints**:

- Rol, comunidad y existencia del usuario evaluados en cada petición (FR-008, FR-017).
- Mismo mensaje y tiempo comparable para identificador inexistente y contraseña incorrecta (FR-002).
- IP de los fallos conservada solo hasta `max(último fallo + 15 min, fin del bloqueo)` (FR-028).
- Ni contraseñas, tokens, nombres, identificadores de acceso ni IP en logs (SC-007).
- Web y API en el mismo *site* para la cookie `SameSite=Strict`.

**Scale/Scope**:

- Backend: 1 módulo nuevo (`identity`) y 13 operaciones HTTP.
- Cliente: 6 pantallas: login, aviso de datos, cambio de contraseña, lista de usuarios, alta o edición de usuario y
  resultado del alta o reasignación (identificador y contraseña inicial).

## Dependencies

- Ninguna previa: es la primera feature a implementar.
- **Consumidora: `001-reservas-salones`**. Implementa su `CurrentActorProvider` sobre `IdentityApi.currentUser()` y
  valida el `communityId` del administrador con `IdentityApi.community(id)`. Sus migraciones pasan de `V2`/`V3` a
  `V3__spaces.sql`/`V4__agenda.sql` (esta feature solo añade `V2__identity.sql`), y su `Problem.code` se fusiona con el
  de esta feature.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple el plan | Pre | Post |
|---|---|---|---|
| I. Dominio puro | `identity.domain` sin Spring, JPA ni Jackson; `PasswordPolicy` recibe `CommonPasswords` como interfaz; reloj como argumento; Spring Security solo en `infrastructure` | PASS | PASS |
| II. Puertos y adaptadores | puertos en `application.port.{in,out}`: `PasswordHasher`, `SessionTokenIssuer`, `LoginThrottleStore`, `CurrentSessionContext`, etc.; filtro, REST, JPA, Argon2 y lista de comunes en `infrastructure` | PASS | PASS |
| III. Un módulo por contexto | `identity` exporta solo `IdentityApi`, `CurrentUser`, `Role`, `CommunityRef`, `UserDeleted`; sin tablas compartidas; los demás módulos no leen `SecurityContextHolder`; sin API pública sin consumidor (`PublicEndpoints` descartado, R3) | PASS | PASS |
| IV. Tests primero | cada regla (bloqueo, caducidad, política de contraseñas, matriz de permisos, invariantes) nace de un test de dominio sin Spring; persistencia, purga y concurrencia (invariantes y bloqueo por fallos con N logins simultáneos) con Testcontainers; [quickstart.md](./quickstart.md) mapea cada escenario | PASS | PASS |
| V. Contrato primero | borrador en `contracts/identity.openapi.yaml` (validado con Redocly); primera tarea: fusionarlo en `api/openapi.yaml` y generar interfaces y tipos; nada generado se edita a mano | PASS | PASS |
| VI. Separación en cliente y tokens | sesión y API en `app/src/lib/auth/` y `app/src/lib/api/`; hooks `useSession`, `useCurrentUser`, `useUsers`, `useCommunities`; pantallas solo con tokens de `design-system` | PASS | PASS |
| VII. Accesible para todas las edades | contraseña con mostrar u ocultar, hasta 64 caracteres, texto ≥ 18 px, objetivos ≥ 48 px, errores asociados al campo, confirmación accesible de borrado, contraseña inicial sin caracteres confusos (FR-021) | PASS | PASS |
| VIII. Privacidad por diseño | solo los datos de FR-024 con su justificación ([data-model.md](./data-model.md)); token y contraseña solo como hash; IP con retención mínima; aceptación del aviso registrada con versión y fecha; acceso por `GET /api/me`, rectificación y borrado por moderador o administrador; logs sin datos personales con test (SC-007); la credencial del primer administrador solo sale por una consola interactiva, nunca por la salida estándar ni el logger, con test (R12) | PASS | PASS |
| IX. Higiene de código | sin comentarios; identificadores en inglés; textos y aviso de datos en recursos del cliente; errores como `code` estables | PASS | PASS |

Restricciones adicionales: stack conforme (Spring Security y Bouncy Castle son dependencias estándar del ecosistema;
la lista de contraseñas comunes de SecLists, MIT, se revisa y copia a mano con su aviso de licencia); sin secretos en el
repo; el primer administrador se crea con un modo de arranque de un solo uso que exige `System.console()`: sin terminal
interactivo aborta sin crear nada; con él, muestra identificador y contraseña inicial solo por la consola, nunca por la
salida estándar (que en despliegue va a los logs), y no los guarda en ningún sitio. Simplicidad: ni `PublicEndpoints`
ni registro persistente de eventos mientras no tengan consumidor. Defaults restrictivos: carga inicial desactivada,
`forward-headers-strategy=none`, cookie `Secure`.

Coherencia con `dominio.md`: sin autorregistro; login con identificador numérico y contraseña; herencia de roles; el
moderador solo opera sobre su comunidad; el administrador elige comunidad; borrar es definitivo y pide confirmación.

**Resultado**: sin violaciones. No hace falta *Complexity Tracking*.

## Project Structure

### Documentation (this feature)

```text
specs/002-identity-access/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── identity.openapi.yaml
│   └── identity-module-api.md
├── checklists/
│   └── requirements.md
└── tasks.md                # lo genera /speckit.tasks
```

### Source Code (repository root)

```text
api/
└── openapi.yaml                                  # se le fusiona contracts/identity.openapi.yaml

backend/
├── pom.xml                                       # + security, bcprov, spring-security-test
└── src/
    ├── main/java/io/github/escumartin/neocatecumenal/identity/
    │   ├── IdentityApi.java, CurrentUser.java, Role.java, CommunityRef.java, UserDeleted.java
    │   ├── domain/                               # User, UserId, AccessId, DisplayName, UserRole, PlainPassword,
    │   │                                         # PasswordHash, PasswordPolicy, CommonPasswords, PrivacyConsent,
    │   │                                         # Community, CommunityId, CommunityNumber, Session, LoginThrottle,
    │   │                                         # Actor, UserManagementPolicy, excepciones
    │   ├── application/
    │   │   ├── port/in/                          # LogIn, LogOut, AuthenticateSession, GetMyProfile,
    │   │   │                                     # Accept/RejectPrivacyNotice, ChangeOwnPassword, ListUsers, GetUser,
    │   │   │                                     # CreateUser, UpdateUser, ResetUserPassword, DeleteUser,
    │   │   │                                     # ListCommunities, BootstrapIdentity
    │   │   ├── port/out/                         # UserRepository, CommunityRepository, SessionRepository,
    │   │   │                                     # LoginThrottleStore, PasswordHasher, CommonPasswordsProvider,
    │   │   │                                     # AccessIdGenerator, InitialPasswordGenerator, SessionTokenIssuer,
    │   │   │                                     # CurrentSessionContext, IdentityEventPublisher, PrivacyNoticeSettings
    │   │   └── service/                          # casos de uso transaccionales + IdentityApiService
    │   └── infrastructure/
    │       ├── in/rest/                          # SessionsController, MeController, UsersController,
    │       │                                     # CommunitiesController, ProblemDetails handler, mappers
    │       ├── in/security/                      # SecurityFilterChain, SessionAuthenticationFilter,
    │       │                                     # PendingStepsAuthorization, ClientIpResolver, SessionCookies,
    │       │                                     # OriginCheck, SecurityContextCurrentSession
    │       ├── in/bootstrap/                     # IdentityBootstrapRunner (modo de un solo uso), proveedor de consola
    │       ├── out/persistence/                  # entidades JPA, repos Spring Data, adaptadores, mappers
    │       ├── out/security/                     # Argon2PasswordHasher, ClasspathCommonPasswords,
    │       │                                     # SecureRandom generators, Sha256SessionTokenIssuer
    │       └── config/                           # wiring, Clock, purgas programadas, propiedades identity.*
    ├── main/resources/
    │   ├── db/migration/V2__identity.sql
    │   └── identity/common-passwords.txt, identity/THIRD-PARTY-NOTICES
    └── test/java/io/github/escumartin/neocatecumenal/identity/
        ├── domain/
        ├── application/                          # servicios con fakes en memoria
        └── infrastructure/                       # Testcontainers, MockMvc con seguridad, logs sin datos personales,
                                                  # concurrencia del bloqueo, carga inicial sin consola

app/src/
├── lib/api/                                      # cliente openapi-fetch con interceptor de sesión
├── lib/auth/                                     # almacén de sesión (secure-store en nativo; cookie en web)
├── hooks/{session,users,communities}/
├── screens/{auth,users}/
├── i18n/es/identity.*                            # textos y mensajes por code
└── app/
    ├── (auth)/{login,privacy-notice,change-password}.tsx
    └── (app)/_layout.tsx, (app)/users/...        # guardia de sesión y pasos pendientes
```

**Structure Decision**: web-service en `backend/` con un módulo Modulith nuevo (`identity`) más cliente Expo en `app/`.
El contrato vive en `api/`. Se mantiene la convención de `hexagonal-ddd`; `infrastructure/in/security` e
`infrastructure/in/bootstrap` son adaptadores de entrada como `in/rest`.

## Decisiones principales

Detalle, alternativas y consecuencias en [research.md](./research.md).

1. **Módulo `identity`** con la gestión de usuarios como negocio, no como infraestructura transversal (R1).
2. **Sesión con token opaco en PostgreSQL** (hash SHA-256), resuelta con el usuario en cada petición; renovación como
   mucho cada 5 min; bearer en nativo, cookie `HttpOnly`/`SameSite=Strict` en web; JWT y Spring Session descartados
   (R2).
3. **Spring Security** con filtro propio; todo autenticado salvo login y health, `permitAll` fijo en la configuración
   de seguridad (R3).
4. **Sesión restringida** con pasos pendientes: primero el aviso de datos, después la contraseña (R4).
5. **Argon2id** (m = 19 MiB, t = 2, p = 1) con NFC y verificación contra hash ficticio; lista local de comunes (R5).
6. **Contraseña inicial generada por el sistema**, mostrada una sola vez (R6).
7. **Bloqueo en PostgreSQL**: par identificador+IP (5 seguidos) e IP (20 en 15 min, fila contadora por IP); el intento
   se reserva como fallo antes de verificar, con `SELECT ... FOR UPDATE` en la misma transacción, y se deshace si
   acierta; retención `max(último fallo + 15 min, fin del bloqueo)`; `429` con `Retry-After` (R7).
8. **`IdentityApi`** en la raíz con `CurrentUser`, `Role`, `CommunityRef`; evento `UserDeleted` sin registro
   persistente mientras no haya consumidores (R8, R9).
9. **Invariantes con bloqueo de filas**: último administrador, último moderador (solo rol `MODERATOR`), sin acciones
   sobre uno mismo (`409 SELF_ACTION_FORBIDDEN`). Moderador sobre usuario de otra comunidad: siempre `403` (R10, R13).
10. **Identificador aleatorio** con índice único y reintento (R11).
11. **Carga inicial** con modo de arranque de un solo uso que exige consola interactiva; sin secretos en repo, entorno
    ni logs (R12).

## Pendientes fuera del ámbito de este plan

- Fusionar `contracts/identity.openapi.yaml` en `api/openapi.yaml` (primera tarea de implementación) y unificar
  `Problem` y `ValidationProblem` con los de 001 cuando se fusione el suyo.
- Plan de 001: renumerar sus migraciones a `V3__spaces.sql`/`V4__agenda.sql` y apuntar su `CurrentActorProvider` a
  `IdentityApi`.
- Cuando exista «Comunidades y hermanos» (primer consumidor de `UserDeleted`): añadir el registro persistente de
  publicaciones de Spring Modulith con su migración (R9).
- Cuando exista Noticias: añadir sus rutas públicas a la configuración de seguridad (R3).
- `dominio.md`: añadir el identificador de acceso de 6 dígitos generado, la sesión restringida del primer acceso y la
  regla «no dejar una comunidad sin moderador».
- Texto del aviso de datos (contenido legal) para los recursos del cliente: lo redacta el usuario.

## Complexity Tracking

Sin violaciones de la constitución.
