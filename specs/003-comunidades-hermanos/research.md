# Research: Comunidades y hermanos

**Feature**: `003-comunidades-hermanos` | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Cada decisión sigue el formato Contexto, Opciones, Decisión, Consecuencias y Constitution Check. Ninguna queda como
NEEDS CLARIFICATION.

Fuentes: `spec.md` (30 FR), `.specify/memory/constitution.md` 1.0.0, `.agents/context/dominio.md`, skill `hexagonal-ddd`,
`specs/002-identity-access/{plan,research,data-model}.md` (R8, R9, R10, R12), `api/openapi.yaml` 0.2.0,
`backend/src/main/resources/db/migration/V1..V4`, código de `identity` (`IdentityApi`, `UserManagementPolicy`,
`UpdateUserService`, `DeleteUserService`, `LockingMembershipCounts`, `CommunitiesController`) y cliente
`app/src/app/(app)/(tabs)`.

## R1. Módulo dueño de comunidades y hermanos

- **Contexto**: hoy las comunidades viven en `identity` (`identity_community`, `IdentityApi.communities()`), cargadas
  por el modo de arranque de 002 (R12). `dominio.md` separa «Identidad y acceso» y «Comunidades y hermanos» como bounded
  contexts distintos. 002 R12 ya anotó que `identity_community` pasaría a ser una proyección alimentada por este contexto.
  `identity_user.community_id` tiene FK a `identity_community`.
- **Opciones**:
  1. **Ampliar `identity`** con hermanos, contacto y consentimiento. Pros: sin coordinación entre módulos; la FK de
     usuarios sigue igual; FR-030 es una consulta local. Contras: mezcla dos contextos del glosario; `identity` pasa a
     guardar datos de contacto de personas que no son usuarios; contradice 002 R12 y el principio III.
  2. **Módulo nuevo `community`** dueño de la comunidad (número, nombre, estado, responsable), los hermanos, su contacto
     y el registro de consentimiento; consume `identity` por su API. `identity` conserva
     `identity_community` como proyección mínima (id, número, activa) que escribe solo `community`, de forma síncrona y
     en la misma transacción, a través de una interfaz exportada por `identity`. Pros: un contexto por módulo; la FK de
     usuarios a comunidades y todo el código de 002 que la usa siguen valiendo; dependencias en un solo sentido
     (`community → identity`). Contras: dos tablas con número y estado, con un único escritor; dos interfaces nuevas en
     la raíz de `identity`.
  3. **Módulo `community` como única fuente y `identity` sin tabla**, leyendo comunidades por una interfaz que
     implementa `community`. Pros: sin duplicar el número. Contras: hay que quitar la FK `identity_user.community_id`
     (no hay FK entre módulos), con lo que el borrado de una comunidad con usuarios solo se protege en aplicación y con
     bloqueos entre módulos; obliga a reescribir los servicios de 002 que validan comunidades.
- **Decisión**: opción 2. Paquete `io.github.escumartin.neocatecumenal.community`,
  `@ApplicationModule(displayName = "Communities and members", allowedDependencies = {"identity", "spaces", "agenda",
  "generated"})` (`spaces` y `agenda` solo para la consulta de historial de R16).
  `identity` no depende de `community` (Modulith prohíbe ciclos); lo que `identity` necesita de `community` se resuelve
  con interfaces que define `identity` en su raíz y que implementa `community` (R4, R5).
- **Consecuencias**:
  - El controlador REST de comunidades (`GET /api/communities`) pasa de `identity` a `community`, porque la interfaz
    generada `CommunitiesApi` agrupa todas las operaciones de la etiqueta `communities` y solo puede implementarla un
    controlador. `IdentityApi.communities()` sigue sirviéndose desde la proyección.
  - La migración copia las comunidades existentes de `identity_community` a `community` (paso único; R13).
  - Orden global de bloqueos `community` → `community_member` → filas de `identity`, respetado por todos los caminos
    (R15).
- **Constitution Check**: III PASS (un módulo por contexto, sin ciclos, sin tablas compartidas: cada tabla tiene un solo
  módulo dueño y escritor).

## R2. Nombre del hermano en el código

- **Contexto**: «hermano» incluye hermanas; el código va en inglés (principio IX).
- **Opciones**: `Brother` (literal, con sesgo de género y extraño fuera del contexto religioso); `Sibling` (sugiere
  parentesco); **`Member`** (miembro de la comunidad, neutro).
- **Decisión**: `Member`, tabla `community_member`, rutas `/api/members`. El glosario de `dominio.md` se amplía con
  «Hermano → `Member`» al cerrar la feature. `User` sigue siendo el usuario de la app de `identity`.
- **Constitution Check**: IX PASS.

## R3. Estado de la comunidad y responsable (FR-004..FR-008, US1, US2)

- **Contexto**: la comunidad nace activa y «sin responsable»; una vez designado, nunca vuelve a estar sin él; el
  responsable es un hermano de la misma comunidad; ser responsable y ser moderador son hechos distintos.
- **Opciones**:
  1. Enumerado de tres estados (`ACTIVE_WITHOUT_RESPONSIBLE`, `ACTIVE`, `DEACTIVATED`). Contras: mezcla dos ejes
     independientes (activación y responsable): una comunidad desactivada también tiene o no responsable.
  2. **Dos ejes**: `status ∈ {ACTIVE, DEACTIVATED}` y `responsibleMemberId` opcional; «sin responsable» es
     `responsibleMemberId` vacío, expuesto como `hasResponsible`. El agregado `Community` solo ofrece
     `designateResponsible(Member)`; no hay operación para quitarlo.
- **Decisión**: opción 2, con la regla reforzada en tres capas:
  - Dominio: `Community.designateResponsible(member)` exige `member.communityId == id` (`MEMBER_NOT_IN_COMMUNITY`).
    Borrar o trasladar al hermano responsable da `409 RESPONSIBLE_REQUIRED` (FR-007). Cambiar de responsable es una
    sola escritura de la columna: la comunidad no queda sin él en ningún instante.
  - Base de datos: FK compuesta `community(responsible_member_id, id) → community_member(id, community_id)`: impide que
    el responsable sea de otra comunidad, que se borre (sin `on delete`) o que cambie de comunidad (sin `on update`).
  - Concurrencia: designar, borrar y trasladar hermanos bloquean antes la fila de la comunidad (`SELECT ... FOR UPDATE`).
- **Aviso FR-008**: la ficha de la comunidad devuelve `responsibleIsModerator: false` cuando el responsable no tiene
  usuario vinculado o su usuario no tiene rol `MODERATOR` (leído con `IdentityApi.user`). No bloquea nada; la regla del
  último moderador sigue en `identity` (002 FR-018, R10).
- **Borrado y desactivación (FR-004, FR-005)**: borrar exige sin responsable, sin hermanos y sin usuarios
  (`IdentityApi.usersOf(id)` vacío; la FK `identity_user.community_id` es la red de seguridad, traducida a
  `409 COMMUNITY_NOT_EMPTY`) y sin historial en reservas ni agenda (`409 COMMUNITY_HAS_HISTORY`, R16). Desactivar con usuarios de rol distinto de `REGISTERED` exige `confirmed: true`; si no,
  `409 COMMUNITY_HAS_PRIVILEGED_USERS`. Desactivada: no admite altas de hermanos ni de usuarios (`422
  COMMUNITY_DEACTIVATED`), su directorio no se muestra a registrados ni moderadores (`409 COMMUNITY_DEACTIVATED`) y el
  administrador conserva lectura, exportación y reactivación.
- **Reutilización de `identity`**: no se duplica la regla del último moderador ni la del último administrador; cuando
  una operación de `community` afecta a un usuario (borrar o trasladar un hermano vinculado) la ejecuta `identity` con
  su `UserManagementPolicy` (R4).
- **Constitution Check**: I PASS (reglas en el agregado), IV PASS (cada regla con test de dominio y de persistencia).

## R4. Vínculo hermano-usuario y bloqueo del cambio de comunidad (FR-016, FR-018, FR-023, FR-028..FR-030)

- **Contexto**: vínculo uno a uno entre un hermano y un usuario de la misma comunidad. Trasladar un hermano vinculado
  mueve también a su usuario; borrar un hermano vinculado a un registrado borra también al usuario; mientras haya
  vínculo, `identity` debe bloquear el cambio de comunidad del usuario (FR-030). `community` depende de `identity`, así
  que `identity` no puede llamar a tipos de `community`.
- **Opciones para FR-030**:
  1. `identity` guarda una marca «vinculado» actualizada por eventos de `community`. Contras: `identity` escucharía
     eventos de `community` → ciclo, que `ApplicationModules.verify()` rechaza.
  2. `identity` publica un evento síncrono «va a cambiar de comunidad» y `community` lo veta lanzando una excepción.
     Contras: veto implícito por excepción en un listener; se rompe si alguien lo hace asíncrono.
  3. **Interfaz de extensión en la raíz de `identity`** (`UserLinkGuard`) que implementa `community`;
     `identity` la consulta en `UpdateUser` y en `DeleteUser` antes de tomar ningún bloqueo propio. Pros: dependencia en el sentido correcto,
     explícita y comprobable; sin ciclo de tipos. Contras: una interfaz más en la raíz de `identity`.
- **Decisión**: opción 3. La regla del guardián es el invariante, no la operación: permite el cambio si el usuario no
  está vinculado o si su hermano vinculado ya está en la comunidad destino. Así el traslado iniciado desde `community`
  (primero cambia la comunidad del hermano y después llama a `IdentityApi.moveUser`) pasa, y el cambio directo desde
  002 se bloquea con `409 USER_LINKED_TO_MEMBER`.
- **Operaciones que `community` pide a `identity`** (raíz de `identity`, ver
  [contracts/community-module-api.md](./contracts/community-module-api.md)):
  - `IdentityApi.user(UUID)` y `IdentityApi.usersOf(UUID communityId)` → `UserRef(id, name, role, communityId)`: para
    validar el vínculo, sugerir el nombre (FR-029, solo sugerencia en el cliente), el aviso FR-008 y FR-005.
  - `IdentityApi.moveUser(UUID userId, UUID communityId)` y `IdentityApi.deleteUser(UUID userId)`: ejecutan los casos de
    uso existentes de 002 con el actor de la petición y su `UserManagementPolicy` (último moderador, último
    administrador, sin acción sobre uno mismo). Errores como `IdentityRuleViolation(code)` exportada.
- **Vínculo**:
  - `community_member.user_id` con índice único: uno a uno (`409 MEMBER_ALREADY_LINKED` / `USER_ALREADY_LINKED`).
  - Validación (dominio `MemberAccessPolicy`): misma comunidad siempre; el moderador solo en su comunidad y con usuarios
    `REGISTERED`; desvincular: administrador siempre, moderador solo en su comunidad con registrados; el propio usuario
    nunca (`403`). No se copia nada del usuario (FR-029).
  - Carreras (vincular frente a cambiar de comunidad y vincular frente a borrar el usuario) y orden de bloqueos: R15.
- **Borrado del usuario en `identity`** (FR-028, US6-11): `DeleteUser` llama a `UserLinkGuard.releaseBeforeDeletion
  (userId)` como primer paso, antes de `LockingMembershipCounts`. `community` bloquea la fila `community` de la
  comunidad del usuario y la del hermano vinculado, si lo hay, y lo desvincula con su nombre intacto. Si después
  `identity` rechaza el borrado (`LAST_MODERATOR`...), la transacción entera se deshace y el vínculo vuelve. No hay
  listener de `UserDeleted` en `community`: un listener escribiría `community_member` después de que `identity` haya
  bloqueado sus filas, al revés del orden global, con riesgo de interbloqueo frente a un traslado (R15). Tampoco hace
  falta el registro persistente de publicaciones que 002 R9 dejó pendiente: el desvínculo ocurre en la misma
  transacción, antes del borrado.
- **Alternativa descartada para borrar en cascada**: que `community` reaccione al borrado del usuario borrando al
  hermano. Contradice US6-11 (el hermano permanece). La cascada va en sentido contrario y es explícita: borrar el hermano
  llama a `IdentityApi.deleteUser` (FR-018).
- **Constitution Check**: III PASS (solo API pública e interfaces de extensión; sin ciclo), II PASS (las llamadas a `identity` pasan por
  un puerto de salida `UserDirectory` de `community.application`, implementado en `community.infrastructure` sobre
  `IdentityApi`).

## R5. Carga inicial de comunidades tras el cambio de dueño

- **Contexto**: el modo de arranque de 002 (R12) crea comunidades en `identity_community` y el primer administrador. Si
  siguiera haciéndolo, habría dos escritores de comunidades.
- **Opciones**:
  1. El arranque sigue escribiendo `identity_community` y publica un evento que `community` copia. Contras: dos
     escritores; el estado puede divergir si falla la copia.
  2. **Interfaz de extensión `CommunityProvisioner`** en la raíz de `identity`, implementada por `community`: el arranque
     pide «asegura la comunidad número N» y `community` la crea (y su proyección) si no existe, devolviendo el id.
  3. Mover el arranque a `community`. Contras: el administrador inicial es de `identity`; partir el arranque en dos.
- **Decisión**: opción 2. Un solo escritor; el arranque de 002 conserva su forma y sus tests de consola.
- **Consecuencias**: los tests de módulo de `identity` usan un `CommunityProvisioner` falso en una `@TestConfiguration`; en producción no hay reserva y un bean ausente falla el arranque; el guardián permisivo es un bean explícito de `identity` que `community` sustituye con `@Primary`.
- **Constitution Check**: III PASS.

## R6. Datos de contacto y su validación (FR-009, FR-010)

- **Decisión**:
  - `PersonName(givenName, surnames?)`: NFC, recortados; nombre 1-100, apellidos 0-100 (vacío → ausente).
  - `PhoneNumber`: se quitan espacios, puntos, guiones y paréntesis; válido si casa con `^[6-9][0-9]{8}$` (nacional) o
    `^\+[1-9][0-9]{1,14}$` (internacional E.164, hasta 15 dígitos). Se guarda normalizado. Mismo tipo para móvil y fijo,
    como pide FR-010. Código `PHONE_INVALID`.
  - `EmailAddress`: recortado, ≤ 254 caracteres y la expresión de la especificación HTML (WHATWG) con al menos un punto
    en el dominio. Código `EMAIL_INVALID`. Sin comprobar entrega (no hay envío de correo).
  - Sin dirección ni sexo (FR-010, decisión de la sesión 2026-11-25).
  - Las mismas expresiones en `app/src/lib/members/contact-validation.ts` para avisar antes de enviar; el servidor manda.
- **Alternativas descartadas**: `libphonenumber` (dependencia grande para una regla que la spec fija en una línea);
  `@Email` de Bean Validation en el dominio (dependencia de Jakarta en `domain` y semántica distinta en cliente).
- **Constitution Check**: I PASS (validación en value objects de Java puro), VIII PASS.

## R7. Registro de consentimiento por campo (FR-011, FR-019, FR-022, FR-026, FR-027)

- **Contexto**: cada dato de contacto existe solo con consentimiento vigente para ese campo; retirar borra el valor; la
  constancia (fecha, quién, campos, origen y, en nombre de un hermano sin usuario, la confirmación del aviso) se
  conserva sin valores mientras exista el hermano y se borra con él.
- **Opciones**:
  1. Columnas de estado en `community_member` (`mobile_consented_at`, ...). Contras: pierde el historial de retiradas
     que exige FR-027.
  2. **Registro de solo inserción** `community_contact_consent`, una fila por campo y decisión, dentro del agregado
     `Member`; el estado vigente de cada campo es su última fila.
- **Decisión**: opción 2.
  - Fila: campo (`MOBILE`, `LANDLINE`, `EMAIL`), decisión (`GRANTED`, `WITHDRAWN`), canal (`APP` si lo decide el
    propio hermano desde su usuario; `VERBAL` o `WRITTEN` si lo anota un moderador o administrador en su nombre),
    `recorded_by_user_id`, `recorder_role` (`MEMBER`, `MODERATOR`, `ADMINISTRATOR`), `notice_given` (obligatorio
    `true` con `VERBAL`/`WRITTEN`) y `recorded_at`. Nunca el valor.
  - Invariante del agregado: un campo tiene valor solo si su última decisión es `GRANTED`; registrar `WITHDRAWN` pone el
    valor a `null` en la misma escritura. Test de propiedad sobre secuencias de cambios (SC-007).
  - Quién decide: hermano sin usuario → moderador de su comunidad o administrador, con canal y aviso; hermano vinculado
    → solo él, desde `PUT /api/me/member/contact` (canal `APP`). Un moderador o administrador que intenta cambiar qué
    campos se comparten de un hermano vinculado recibe `403 CONSENT_MANAGED_BY_MEMBER`; sí puede rectificar el valor de
    un campo ya consentido (FR-015, FR-020), sin fila nueva.
  - Consentimiento anotado por el moderador y vinculación posterior: sigue vigente; el hermano lo ve en «Mis datos de
    contacto» con su origen y fecha y su decisión crea una fila `APP` que prevalece (US5-6).
  - Retención: mientras exista el hermano; `on delete cascade` desde `community_member` (FR-027, SC-008).
  - `recorded_by_user_id` es un UUID sin FK (otro módulo); si ese usuario se borra, el UUID deja de enlazar con datos
    personales.
- **Entrada explícita**: cada campo llega como `{ shared: boolean, value?: string }`; `shared` es obligatorio, así que
  un campo olvidado por el cliente no se interpreta como retirada (el generador usa `openApiNullable=false`, que no
  distingue ausente de `null`).
- **Constitution Check**: VIII PASS (consentimiento por campo, retirada efectiva, sin valores en el registro, borrado en
  cascada), I PASS.

## R8. Autorización y visibilidad (FR-010, FR-012..FR-016, FR-021, SC-005)

- **Decisión**: política de dominio `MemberAccessPolicy` con el `Actor` local de `community` (resuelto en el adaptador
  REST con `IdentityApi.currentUser()`, como `spaces`):
  - Ver directorio y ficha: registrado, moderador o administrador de esa comunidad; el administrador indica
    `communityId`. Comunidad desactivada: solo administrador.
  - Gestionar hermanos (alta, nombre, contacto, vínculo, exportación): moderador de su comunidad y administrador.
  - Trasladar y designar responsable: solo administrador.
  - Borrar: moderador de su comunidad si el hermano no tiene usuario o está vinculado a un `REGISTERED` y no es él
    mismo; administrador siempre, con las reglas de `identity` aplicadas por `IdentityApi.deleteUser`.
  - Respuestas: directorio de otra comunidad → `403` (la lista de comunidades ya es visible); ficha de un hermano de otra
    comunidad → `404 NOT_FOUND`, igual que uno inexistente (US4-2: no revelar si existe). Se aparta a propósito de 002
    R13 (`403` para usuarios de otra comunidad) porque esta spec lo pide expresamente.
- **Constitution Check**: VIII PASS, IV PASS (matriz de permisos como test parametrizado de dominio y REST).

## R9. Orden y búsqueda del directorio (FR-009, FR-012, US4-1, US4-4)

- **Decisión**: el servidor devuelve la comunidad completa (≤ 80 hermanos, sin paginar) ordenada en aplicación con
  `java.text.Collator` español de fuerza primaria: clave `surnames` si existen y si no `givenName`, después `givenName`,
  después `id` para estabilidad. La búsqueda la hace el cliente sobre la lista cargada, sin distinguir mayúsculas ni
  tildes.
- **Alternativas descartadas**: `ORDER BY` con colación ICU de PostgreSQL (depende de cómo se construyó la imagen);
  búsqueda en servidor (una petición por tecla para 80 filas).
- **Constitution Check**: I PASS (`Collator` es Java estándar).

## R10. Supresión, rectificación y copia (FR-017..FR-020, US7, SC-008)

- **Decisión**:
  - Borrar hermano: bloquea la comunidad; `RESPONSIBLE_REQUIRED` si es el responsable; si está vinculado, primero
    `IdentityApi.deleteUser` (consulta el guardián, que ya tiene la comunidad bloqueada en esta transacción; borra
    usuario y sesiones y publica `UserDeleted`, que anonimiza `spaces` y `agenda`), y
    después borra la fila del hermano con su registro de consentimiento en cascada. Todo en una transacción: si
    `identity` lo impide (`LAST_MODERATOR`, ...), no se borra nada.
  - Rectificar: sobrescribe el valor; no se guarda el anterior ni en el registro de consentimiento ni en logs.
  - Autor de cada registro de consentimiento (FR-027, art. 15 RGPD): la copia y «Mis datos de contacto» muestran
    `recordedBy = {role, name?, deleted}` (R17).
  - Copia: `GET /api/members/{memberId}/data-export` devuelve todo lo que guarda `community` del hermano (nombre,
    comunidad, contacto, vínculo y registro de consentimiento completo); el cliente lo muestra y permite compartirlo como
    texto con la hoja nativa. Los datos de usuario se obtienen aparte con `GET /api/users/{userId}` de 002.
  - Ninguna otra feature guarda el id del hermano, así que no hay historial que anonimizar fuera de lo que ya cubre
    `UserDeleted`.
- **Constitution Check**: VIII PASS.

## R11. Logs y errores (FR-021, SC-009)

- **Decisión**: logs solo con `actorId`, `memberId`, `communityId`, `userId` (UUID) y códigos; nunca nombre, apellidos,
  teléfono ni correo. `Problem.detail` nunca incluye datos personales: el cliente compone el mensaje desde el `code`. Un
  test captura el logger durante alta, edición de contacto, vínculo, traslado y borrado y comprueba que no aparecen los
  valores sintéticos usados.
- **Constitution Check**: VIII PASS, IX PASS.

## R12. Contrato HTTP

- **Decisión**: borrador en [contracts/community.openapi.yaml](./contracts/community.openapi.yaml), con el estilo y las
  convenciones `x-merge` de 001.
  - Comunidades: `GET /api/communities` (existente; se le añaden el parámetro opcional `includeDeactivated` y
    propiedades opcionales a `Community`), `POST /api/communities`, `GET/PUT/DELETE /api/communities/{communityId}`,
    `POST /api/communities/{communityId}/deactivation`, `POST /api/communities/{communityId}/reactivation`,
    `PUT /api/communities/{communityId}/responsible`.
  - Hermanos: `GET/POST /api/members`, `GET/PUT/DELETE /api/members/{memberId}`,
    `PUT /api/members/{memberId}/contact`, `POST /api/members/{memberId}/transfer`,
    `PUT/DELETE /api/members/{memberId}/link`, `GET /api/members/{memberId}/data-export`.
  - Propio: `GET /api/me/member`, `PUT /api/me/member/contact`.
  - `PUT /api/users/{userId}` de 002 gana el código `409 USER_LINKED_TO_MEMBER` y el de validación
    `COMMUNITY_DEACTIVATED`; `POST /api/users`, este último.
  - `includeDeactivated=true` (solo administrador) sirve también de búsqueda id → número para pantallas con
    historial, como `pending-requests.tsx` de 001, que hoy resuelve números con `useCommunities` y dejaría de ver los de
    comunidades desactivadas.
  - Sin endpoint de usuarios vinculables: el cliente cruza `GET /api/users?communityId=` con los `linkedUserId` del
    listado de gestión; el servidor valida.
- **Compatibilidad**: solo aditivo; `info.version` 0.2.0 → 0.3.0. `GET /api/communities` sin parámetro devuelve las
  activas: antes de esta feature todas lo eran, así que no cambia el resultado para clientes existentes.
- **Constitution Check**: V PASS.

## R13. Migraciones

- **Decisión**: dos ficheros nuevos, sin tocar V1-V4:
  - `V5__identity_community_active.sql`: `identity_community.active boolean not null default true`.
  - `V6__community.sql`: tablas `community`, `community_member`, `community_contact_consent`, FK compuesta del
    responsable y copia única `insert into community (id, number, status, version) select id, number, 'ACTIVE', 0 from
    identity_community`. Es la única lectura de una tabla de otro módulo y ocurre una vez, en la migración.
- **Constitution Check**: III PASS (justificado: copia de arranque, no acceso en tiempo de ejecución).

## R14. Cliente Expo

- **Contexto**: pestañas nativas actuales: Agenda, Reservas, Gestión (moderador+) y Cuenta; el registrado solo ve
  Cuenta. Componentes iOS disponibles: `ListSection`, `ListRow`, `StateView`, `FormField`, `RadioSection`, `DateField`.
- **Opciones**:
  1. Todo en Gestión y Cuenta. Contras: el registrado no tiene dónde ver el directorio, su principal beneficio.
  2. **Pestaña nueva Comunidad** (directorio de solo lectura, para todos los roles); gestión en Gestión; «Mis datos de
     contacto» en Cuenta.
  3. Pestaña Comunidad con la gestión dentro de la ficha. Contras: mezcla consulta y edición en la pantalla que usan
     personas de 90 años; rompe el patrón de Gestión → Usuarios.
- **Decisión**: opción 2.
  - `comunidad/index`: directorio con búsqueda (`headerSearchBarOptions` nativo), `ListSection`/`ListRow` por hermano;
    `comunidad/[memberId]`: ficha con filas «Llamar al móvil», «Llamar al fijo», «Escribir correo» (`Linking` con
    `tel:`/`mailto:`, objetivo ≥ 48 px, etiqueta accesible con el tipo de acción). Administrador: elige comunidad con
    el `community-picker` de 002. Estados de carga, vacío y error con `StateView`, sin datos personales en el error.
  - `gestion/comunidades/*` (administrador): lista con «Sin responsable» como `trailing` visible, alta, edición,
    desactivar con confirmación expresa si hay moderadores, reactivar, borrar con confirmación, elegir responsable con
    `RadioSection`, aviso FR-008 en la ficha.
  - `gestion/hermanos/*` (moderador y administrador): lista, alta, edición de nombre, contacto con consentimiento en su
    nombre (`RadioSection` verbal/escrito y conmutador «He informado del aviso de datos»), vincular (lista de usuarios
    vinculables con el nombre sugerido como texto, sin rellenar nada), desvincular, trasladar (administrador), copia de
    datos (hoja nativa de compartir) y borrar con confirmación que avisa si borra también el usuario.
  - `cuenta/contacto`: «Mis datos de contacto» con el aviso de datos arriba, un `FormField` y un conmutador «Compartir
    con mi comunidad» por campo; apagarlo vacía el valor y, al guardar, retira el consentimiento. Muestra el origen del
    consentimiento anotado por el moderador (US5-6). Sin hermano vinculado: `StateView` que explica pedir el vínculo.
  - `navigationFor`: añade `comunidad` para todos los roles y `canManageMembers`, `canManageCommunities`.
  - Se reutilizan y amplían (MODIFY) `app/src/lib/api/communities-api.ts` (alta, edición, estado, responsable y
    parámetro `includeDeactivated`), `app/src/hooks/communities/use-communities.tsx` (opción `includeDeactivated`),
    `app/src/i18n/es/communities.ts` y `app/src/i18n/es/navigation.ts`. Nuevos: `app/src/lib/api/members-api.ts`,
    `app/src/lib/members/` (validación, búsqueda, texto de exportación), `app/src/hooks/members/` e
    `app/src/i18n/es/members.ts`.
  - 001: `app/src/screens/bookings/pending-requests.tsx` pide `useCommunities` con `includeDeactivated: true` (es pantalla
    de administrador) para seguir mostrando el número de solicitudes de comunidades desactivadas;
    `app/src/screens/users/community-picker.tsx` sigue con solo activas (FR-005).
  - Mensajes de validación pensados para acertar a la primera (SC-004): cada campo muestra el formato esperado antes de
    escribir («9 cifras, o + y prefijo»), el error aparece junto al campo con un ejemplo válido y nunca borra lo escrito;
    el teclado es `phone-pad` o `email-address` según el campo.
- **Constitution Check**: VI PASS (solo tokens de `design-system`), VII PASS (texto ≥ 18 px, objetivos ≥ 48 px,
  etiquetas, AA, escalado, movimiento reducido y temas claro y oscuro), IX PASS.

## R15. Orden global de bloqueos y concurrencia (FR-007, FR-016, FR-028, FR-030, SC-006)

- **Contexto**: el borrado de un usuario en `identity` bloquea `identity_community` y las filas de administradores
  (`LockingMembershipCounts`). Si `community` escribiera `community_member` después (listener de `UserDeleted`) mientras
  un traslado de ese hermano tiene `community_member` bloqueada y espera `identity_community` en `moveUser`, habría
  interbloqueo. Además, sin FK entre módulos, vincular a la vez que se borra el usuario podría dejar un `user_id`
  colgando, y un traslado podría meter un hermano en una comunidad que se está desactivando.
- **Opciones**:
  1. El listener de `UserDeleted` solo bloquea `community_member` y se demuestra que el traslado nunca espera a
     `identity` con esa fila bloqueada. Contras: obliga a llamar a `moveUser` antes de escribir el hermano, lo que choca
     con el guardián (que exige el hermano ya en destino); prueba frágil.
  2. **Un único orden para todos los caminos, con `community` siempre primero**: `identity` consulta `UserLinkGuard`
     antes de cualquier bloqueo propio, tanto al cambiar de comunidad como al borrar; el guardián toma los bloqueos de
     `community`. Sin listener.
- **Regla general (lectura previa, bloqueo, relectura, reintento)**: toda fila leída antes de bloquear (la comunidad
  del usuario, la del hermano) solo sirve para decidir qué bloquear y en qué orden. Con los bloqueos tomados se vuelve a
  leer la fila con bloqueo (`FOR UPDATE`) y se valida contra lo bloqueado; todas las decisiones posteriores (políticas,
  recuentos, invariantes) usan el valor releído. Si la relectura no coincide con la comunidad bloqueada, no se amplían
  bloqueos fuera de orden (PostgreSQL no libera bloqueos dentro de la transacción): el caso de uso lanza
  `StaleLockOrderException` (declarada en la raíz de `identity`; la lanzan el guardián de `community` y `DeleteUser` y
  `UpdateUser` de `identity`), se deshace la transacción y la captura `TransactionRetry`, un componente pequeño en
  `infrastructure/in/rest` de `identity` y de `community`, que reintenta el caso de uso entero, como mucho 3 llamadas; agotados los reintentos, `409 CONCURRENT_MODIFICATION`. Se aplica igual en todos los caminos de la tabla.
- **Decisión**: opción 2. Orden global: (1) filas `community`, en orden ascendente de id; (2) filas `community_member`;
  (3) filas de `identity` (`identity_user`, administradores, `identity_community`). Ningún camino toma un bloqueo de un
  nivel inferior y luego espera uno de nivel superior.

| Camino | Bloqueos, en orden |
|---|---|
| Trasladar hermano | lectura previa del hermano → `community` origen y destino por id (`FOR UPDATE`) → relee ambas (destino activo) → `community_member` `FOR UPDATE` y relee: sigue en el origen bloqueado, si no reintento → `IdentityApi.moveUser` → guardián (reentrante) → `identity` |
| Borrar hermano | lectura previa → `community` → `community_member` `FOR UPDATE` y relee su comunidad (si cambió, reintento) → `IdentityApi.deleteUser` → guardián (reentrante) → `identity` |
| Borrar usuario desde 002 | lectura previa del usuario y de su hermano vinculado → guardián: `community` del usuario y del hermano, por id → `community_member` `FOR UPDATE`, relee su comunidad (si cambió, reintento) y desvincula → `DeleteUser` relee el usuario con `findByIdForUpdate` y, si su comunidad no es la bloqueada, reintento → `LockingMembershipCounts` y `UserManagementPolicy` con la comunidad releída |
| Cambiar comunidad desde 002 | lectura previa → guardián: `community` de origen y destino por id → relee el vínculo y el usuario con bloqueo (si el origen cambió, reintento) → `identity` con los valores releídos |
| Vincular / desvincular | `community` del hermano → `community_member` → `IdentityApi.lockUser(userId)` (`SELECT ... FOR SHARE` de `identity_user`) |
| Designar responsable, alta de hermano | `community` → `community_member` |
| Desactivar, reactivar, borrar comunidad | `community` → `CommunityCatalog` (`identity_community`) |
| Cambio de rol, contraseña (002) | solo `identity` |

- **Carrera vincular frente a borrar usuario**: ambos bloquean la fila `community` de la comunidad del usuario. Si el
  borrado va primero, vincular espera, y al leer con `IdentityApi.lockUser` el usuario ya no existe → `422
  USER_NOT_FOUND`. Si vincular va primero, el guardián del borrado espera, ve el vínculo confirmado y lo deshace antes de
  borrar. `lockUser` además impide que el usuario cambie de comunidad o rol entre la validación y la escritura del
  vínculo.
- **FK `community_member.user_id → identity_user(id) on delete set null`**: descartada. Sería una FK entre tablas de
  dos módulos, que 001 y 002 evitan y la skill `hexagonal-ddd` prohíbe, y ocultaría errores del guardián en lugar de
  hacerlos visibles. La garantía la dan el orden de bloqueos y los tests de dos hilos.
- **Traslado frente a desactivación**: la desactivación bloquea la fila `community`; el traslado bloquea origen y destino
  en orden de id y revalida `status = ACTIVE` del destino con el bloqueo tomado; el alta de hermano hace lo mismo con
  su comunidad.
- **Consecuencias**: `IdentityApi` gana `lockUser`; `DeleteUserService` y `UpdateUserService` cambian el orden de sus
  pasos (guardián primero). Cada carrera tiene un test de dos hilos con Testcontainers ([quickstart.md](./quickstart.md)
  § 2) que comprueba el resultado y que ninguna transacción termina con `40P01` (interbloqueo).
- **Constitution Check**: III PASS (sin FK ni ciclo entre módulos), IV PASS.

## R16. Borrar una comunidad con historial en reservas o agenda (FR-004)

- **Contexto**: `booking_request.community_id` (spaces) y `event.community_id` (agenda) guardan el UUID sin FK. Una
  comunidad vacía de hermanos y usuarios puede tener historial; si se borra, ese historial apunta a una comunidad
  inexistente y el cliente no puede mostrar su número.
- **Opciones**:
  1. Borrar y dejar una lápida (id y número) para el historial. Contras: contradice «desaparece de forma definitiva»
     (US1-5) y deja una comunidad a medias.
  2. Borrar sin más y que el historial muestre «comunidad eliminada». Contras: pierde un dato no personal útil.
  3. **Prohibir el borrado si hay historial** y ofrecer desactivar, como ya hace FR-004 con el responsable. Pros:
     coherente con la spec. Contras: `community` pasa a depender de `spaces` y `agenda` para consultarlo.
- **Decisión**: opción 3. `SpacesApi.hasBookingRequestsFor(UUID communityId)` y un `AgendaApi.hasEventsFor(UUID
  communityId)` nuevo en la raíz de `agenda`; `community` los consulta por un puerto de salida `CommunityHistory`.
  `allowedDependencies = {"identity", "spaces", "agenda", "generated"}`; sin ciclo, porque ni `spaces` ni `agenda`
  dependen de `community`. Error `409 COMMUNITY_HAS_HISTORY`.
- **Riesgo residual**: un administrador que crea una reserva para esa comunidad en el mismo instante del borrado. Las
  reservas de 001 solo validan el id con `IdentityApi.community`; con la proyección ya borrada en la misma transacción,
  la reserva posterior falla con `COMMUNITY_NOT_FOUND`; la anterior se ve en la comprobación. La ventana entre ambas
  lecturas existe pero exige dos administradores simultáneos; se acepta.
- **Constitution Check**: III PASS.

## R17. Autor del registro de consentimiento en la copia (FR-020, FR-027)

- **Decisión**: cada `ConsentRecord` expuesto lleva `recordedBy = {role, name?, deleted}`:
  - `role = MEMBER`: el propio hermano; sin `name`.
  - `role = MODERATOR`/`ADMINISTRATOR`: `name` es el nombre de usuario de quien lo anotó, leído con `IdentityApi.user`
    al servir la respuesta; nada más (ni identificador de acceso ni comunidad).
  - Usuario autor borrado: `deleted = true`, sin `name`; el cliente muestra «usuario eliminado».
  - Solo se expone en la copia de datos (moderador o administrador) y en «Mis datos de contacto» (el titular).
- **Alternativa descartada**: copiar el nombre del autor en el registro al anotarlo. Guardaría un dato personal del
  moderador más allá de su vida como usuario.
- **Constitution Check**: VIII PASS.

## R18. Comunidad desactivada y operaciones sobre hermanos (FR-005, FR-019, FR-020)

- **Decisión**:
  - Moderador: toda operación sobre hermanos de una comunidad desactivada (consulta, listado, renombrado, contacto,
    vínculo, desvínculo, borrado y copia de datos) responde `409 COMMUNITY_DEACTIVATED`. Se aplica en
    `MemberLocks.lockedForManagement` y en los servicios de consulta y copia con `requireDirectoryAvailable`.
  - Administrador: exento. Puede renombrar y renumerar la comunidad, designar responsable, editar, borrar y exportar
    hermanos para gestionar la comunidad y atender solicitudes RGPD.
  - Usuario vinculado: `GET /api/me/member` y `PUT /api/me/member/contact` funcionan siempre (derechos RGPD, FR-019 y
    FR-020); no devuelven `COMMUNITY_DEACTIVATED`.
  - Trasladar un hermano a su propia comunidad es un `200` sin cambios.
  - Desactivar una comunidad ya desactivada y reactivar una activa son `204` idempotentes, sin efectos ni
    confirmación.
- **Texto libre**: los nombres (`PersonName`, `CommunityName`) rechazan con `422 INVALID_FORMAT` los caracteres de
  control y de formato (`\p{Cc}`, `\p{Cf}`: NUL, saltos de línea, controles bidi) y los sustitutos sueltos; la
  longitud se cuenta en puntos de código.
- **Migración V6**: copia el estado con `active` y añade el `check` de sintaxis de `email`.
- **Constitution Check**: VIII PASS.
