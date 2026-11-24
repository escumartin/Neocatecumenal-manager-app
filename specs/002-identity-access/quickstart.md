# Quickstart: validar Identidad y acceso

**Feature**: `002-identity-access` | **Plan**: [plan.md](./plan.md) | **Contrato**: [contracts/identity.openapi.yaml](./contracts/identity.openapi.yaml) | **Modelo**: [data-model.md](./data-model.md)

Guía para comprobar de punta a punta que la feature funciona. No contiene implementación.

## Requisitos previos

- Java 21 en `JAVA_HOME`, Node LTS, Docker (Rancher Desktop vale).
- Contrato fusionado en `api/openapi.yaml` y código generado (`./mvnw -B generate-sources`, `npm run api:generate`).
- Con Rancher Desktop: `export TESTCONTAINERS_RYUK_DISABLED=true`.
- Datos sintéticos: nombres inventados, sin personas reales.

## 1. Gates automáticos

```bash
cd backend && ./mvnw -B verify
cd app && npm run lint && npm run typecheck && npx expo export -p web
```

Esperado: exit 0. `verify` incluye dominio y aplicación sin Spring, `ArchitectureTest`, `ModularityTest` y adaptadores
con Testcontainers.

## 2. Escenarios que deben estar cubiertos por tests

| Escenario | Spec | Nivel de test | Resultado esperado |
|---|---|---|---|
| Login correcto | US1-1, FR-001 | aplicación + REST | `201`, `me` con rol, comunidad y `pendingSteps` |
| Identificador inexistente frente a contraseña errónea | US1-2, FR-002 | REST | mismo `401 INVALID_CREDENTIALS`, mismo cuerpo; tiempos comparables (verificación contra hash ficticio) |
| Cerrar sesión | US1-3 | REST | `204`; el mismo token da `401` |
| Visitante en ruta protegida | US1-4 | REST + cliente | `401`; el cliente lleva al login |
| 5 fallos seguidos mismo par | US1-5, FR-003 | dominio + persistencia | 5.º fallo bloquea; 6.º intento `429` con `Retry-After` ≈ 900 s; tras 15 min, se admite |
| Fallos desde otra IP | FR-003 | dominio | no bloquean al titular en su IP |
| 20 fallos de una IP con identificadores distintos | FR-003 | dominio + persistencia | el 21.º intento `429` para cualquier identificador |
| Identificador inexistente en bloqueo | FR-003 | dominio | cuenta y responde igual que uno existente |
| Logins simultáneos contra el bloqueo | FR-003, R7 | persistencia (Testcontainers, `PasswordHasher` espía) | 30 logins erróneos a la vez del mismo par: ≤ 5 verificaciones; 40 a la vez desde la misma IP con identificadores distintos: ≤ 20; el resto `429` |
| Acierto deshace la reserva | FR-003, R7 | dominio + persistencia | tras un login correcto, la fila del par desaparece y la IP no suma el intento; 25 logins correctos desde una IP no la bloquean |
| Purga de fallos | FR-028 | persistencia | filas eliminadas al pasar `max(último fallo + 15 min, fin del bloqueo)` |
| Matriz de permisos | US2-1..3, FR-005..007, SC-003 | dominio (`UserManagementPolicy`) + REST | cada rol por cada acción concede o deniega según la tabla de [data-model.md](./data-model.md) |
| Moderador A sobre usuario de B | US2-2, SC-004 | aplicación + REST | siempre `403`, nunca datos de B; `404` solo si el usuario no existe |
| Cambio de rol aplicado en la siguiente petición | US2-4, FR-008 | REST (Testcontainers) | con la misma sesión, la acción ya responde según el rol nuevo |
| Alta por moderador | US3-1, FR-012 | aplicación + REST | `201`, rol `REGISTERED`, `accessId` de 6 dígitos, contraseña inicial |
| Moderador crea moderador o en otra comunidad | US3-2 | dominio + REST | `403` |
| Alta inválida | US3-4, FR-013 | REST | `422` con códigos de campo; nada creado |
| Colisión de identificador | Edge case, R11 | aplicación (generador falso que repite) + persistencia | ambos usuarios creados con identificadores distintos |
| Borrado | US3-5, FR-017, SC-006 | REST (Testcontainers) | `204`; la sesión abierta del borrado da `401`; login con sus credenciales `401`; `UserDeleted` publicado |
| Autoborrado, último moderador, último administrador | US3-6, FR-018 | dominio + persistencia (dos hilos) | `409 SELF_ACTION_FORBIDDEN` / `LAST_MODERATOR` / `LAST_ADMINISTRATOR`; con dos degradaciones simultáneas, una falla |
| Primer acceso: aviso y contraseña | US4-1, FR-014, FR-020 | REST | otras rutas `403 PRIVACY_NOTICE_PENDING` y luego `PASSWORD_CHANGE_REQUIRED`; tras aceptar y cambiar, acceso normal |
| Rechazo del aviso | FR-020 | REST | `204`; el token da `401`; al volver a entrar, `PRIVACY_NOTICE` pendiente |
| Cambio de contraseña propia | US4-2, FR-027 | REST | `204`; la sesión actual sigue; otra sesión del mismo usuario `401`; la contraseña antigua no vale |
| Política de contraseñas | FR-015 | dominio | 7 y 65 *code points*, común, con el identificador → códigos de rechazo; 64 con tildes → aceptada |
| Usuario intenta cambiar su rol | US4-3, FR-016 | REST | `409 SELF_ACTION_FORBIDDEN` |
| Reasignación de contraseña | US4-4, FR-022 | REST | `200` con contraseña inicial; todas sus sesiones `401`; la anterior no vale |
| Caducidad | FR-026 | dominio (reloj fijo) + persistencia | 30 d sin uso → `401`; uso cada día → válida hasta 90 d desde el login |
| Sin datos personales en logs | SC-007, FR-019 | integración con el logger capturado | ni nombre, identificador, contraseña, token, cookie ni IP en la salida |
| Carga inicial sin consola | SC-007, R12 | integración (proveedor de consola vacío, salida y logs capturados) | aborta con código ≠ 0; sin filas en `identity_community` ni `identity_user`; ni identificador ni contraseña en salida estándar ni logs |
| Carga inicial con consola | SC-007, R12 | integración (consola falsa) | la credencial solo aparece en la consola falsa; repetirlo no crea otro administrador |
| API del módulo | FR-004, FR-025 | `@ApplicationModuleTest` | `currentUser()` vacío sin sesión o con pasos pendientes; `communities()` ordenadas |
| Hash de contraseña | FR-015 | persistencia | la columna empieza por `{argon2}`; nunca contiene la contraseña |

## 3. Prueba manual de punta a punta

1. Base local y carga inicial (modo de un solo uso, ver [research.md](./research.md) R12), desde un terminal
   interactivo:

   ```bash
   cd backend && ./mvnw -B -q package -DskipTests
   java -jar target/neocatecumenal-manager-0.0.1-SNAPSHOT.jar --spring.profiles.active=bootstrap \
     --identity.bootstrap.communities=1,2,3 \
     --identity.bootstrap.administrator-name=Admin \
     --identity.bootstrap.administrator-community=1
   ```

   - `communities`: lista de números de comunidad separados por comas; las que no existen se crean. La comunidad del
     administrador se crea también si falta.
   - `administrator-name` y `administrator-community` son obligatorios; sin ellos aborta antes de crear nada.
   - El perfil `bootstrap` arranca sin servidor web y con `identity.bootstrap.enabled=true`.

   Esperado: la consola muestra una vez el identificador de acceso y la contraseña inicial del administrador; el
   proceso termina. La credencial sale solo por la consola, nunca por la salida estándar redirigida ni por los logs.
   Repetirlo no crea otro administrador. Sin terminal interactivo (p. ej. `... > bootstrap.log`), aborta con código 2
   sin crear nada y el fichero no contiene la credencial.

2. Arrancar backend y cliente web (`./mvnw spring-boot:run`, `npx expo start --web`).
3. Entrar como administrador: aviso de datos, aceptar; cambio de contraseña obligatorio; acceso a la app.
4. Crear un moderador en la comunidad 2 y un registrado en la 2; apuntar sus contraseñas iniciales.
5. En otro navegador, entrar como moderador: crear un registrado (≤ 2 min, SC-005); intentar crear un moderador
   (denegado); no ver usuarios de la comunidad 1.
6. Como administrador, degradar al moderador de la comunidad 2: debe impedirse (último moderador).
7. Como administrador, reasignar la contraseña del registrado: su sesión abierta pasa al login.
8. Borrar al registrado tras confirmar: su sesión pasa al login y sus credenciales dejan de valer.
9. Fallar 5 veces la contraseña de un usuario: aparece el tiempo de espera; desde otra red, el titular entra.
10. Accesibilidad en móvil: lector de pantalla en los campos del login, botón de mostrar contraseña, escalado de fuente
    del sistema al 200 %, tema oscuro y contraste AA.
