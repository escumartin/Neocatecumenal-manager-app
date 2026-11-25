# Quickstart: validar Comunidades y hermanos

**Feature**: `003-comunidades-hermanos` | **Plan**: [plan.md](./plan.md) | **Contrato**: [contracts/community.openapi.yaml](./contracts/community.openapi.yaml) | **Modelo**: [data-model.md](./data-model.md)

Guía para comprobar de punta a punta que la feature funciona. No contiene implementación.

## Requisitos previos

- Java 25 en `JAVA_HOME`, Node LTS, Docker (Rancher Desktop vale).
- Contrato fusionado en `api/openapi.yaml` y código generado (`./mvnw -B generate-sources`, `npm run api:generate`).
- Con Rancher Desktop: `export TESTCONTAINERS_RYUK_DISABLED=true`.
- Datos sintéticos: nombres, teléfonos (`600000001`...) y correos (`@example.org`) inventados.

## 1. Gates automáticos

```bash
npx --yes @redocly/cli@2.76.0 lint api/openapi.yaml --config api/redocly.yaml
cd backend && ./mvnw -B verify
cd app && npm run lint && npm run typecheck && npm test && npx expo export -p web
```

Esperado: exit 0. `verify` incluye `ModularityTest` (sin ciclo `identity` ↔ `community`), `ArchitectureTest`, dominio
y aplicación sin Spring y adaptadores con Testcontainers.

## 2. Escenarios que deben estar cubiertos por tests

| Escenario | Spec | Nivel de test | Resultado esperado |
|---|---|---|---|
| Alta de comunidad | US1-1, FR-001, FR-006 | aplicación + REST | `201`, `ACTIVE`, `hasResponsible=false`; aparece en `GET /api/communities` y en `IdentityApi.communities()` |
| Nombre de comunidad opcional | FR-003 | dominio + REST | sin nombre `201`; nombre en blanco se guarda como ausente; 101 caracteres → `422 TOO_LONG`; el número sigue siendo la referencia |
| Número repetido, en serie y en paralelo | US1-2, FR-002, edge case | dominio + persistencia (dos hilos) | `422 COMMUNITY_NUMBER_TAKEN`; solo una creada |
| Moderador o registrado sobre comunidades | US1-3 | REST | `403` en alta, edición, desactivación, reactivación, borrado y responsable |
| Borrar con responsable | US1-4, FR-004 | aplicación + REST | `409 COMMUNITY_HAS_RESPONSIBLE` |
| Borrar vacía | US1-5 | REST (Testcontainers) | `204`; desaparece de `community` e `identity_community` |
| Borrar con usuarios y sin hermanos | FR-004 | persistencia | `409 COMMUNITY_NOT_EMPTY` (FK de `identity_user` como red) |
| Borrar con reservas o eventos | FR-004, R16 | aplicación + REST (Testcontainers) | `409 COMMUNITY_HAS_HISTORY`; la comunidad sigue y se puede desactivar |
| Búsqueda de números con desactivadas | R12, R14 | cliente (`pending-requests`) + REST | el administrador ve el número de una solicitud de una comunidad desactivada; `includeDeactivated=true` como no administrador → `403` |
| Desactivar con moderador | US1-6, FR-005 | aplicación + REST | sin `confirmed` → `409 COMMUNITY_HAS_PRIVILEGED_USERS`; con `confirmed=true` → `204` |
| Comunidad desactivada | US1-7, US1-8, FR-005 | REST | fuera de `GET /api/communities`; directorio `409 COMMUNITY_DEACTIVATED` para registrado y moderador; alta de hermano o de usuario `422 COMMUNITY_DEACTIVATED`; el administrador la lee y la reactiva |
| Lista de solo lectura para identidad | US1-9, FR-025 | `@ApplicationModuleTest` | `IdentityApi.communities()` incluye la nueva con id y número |
| «Sin responsable» visible y admite hermanos | US2-1, US2-2 | REST | `hasResponsible=false`; alta de hermano `201` |
| Designar responsable propio y ajeno | US2-3, US2-4, FR-006 | dominio + persistencia | propio `200`; de otra comunidad `422 MEMBER_NOT_IN_COMMUNITY` (dominio) y la FK compuesta lo rechaza también |
| Borrar o trasladar al responsable | US2-5, FR-007 | dominio + persistencia | `409 RESPONSIBLE_REQUIRED`; actualizar la fila a mano viola la FK |
| Cambio de responsable sin hueco | US2-6, SC-006 | persistencia (secuencias y dos hilos) | en ninguna lectura concurrente aparece sin responsable |
| Responsable sin rol de moderador | US2-7, FR-008 | aplicación | `responsibleIsModerator=false`; ninguna operación bloqueada |
| Moderador designa responsable | US2-8 | REST | `403` |
| Ficha mínima | US3-1, FR-009, FR-010 | REST | solo nombre, apellidos, comunidad y contactos consentidos |
| Alta sin nombre / sin apellidos | US3-2 | dominio + REST | `422 REQUIRED` / `201` |
| Teléfonos y correo | US3-3, FR-010 | dominio (parametrizado) + REST | válidos: `600 000 001`, `912-345-678`, `+351912345678`; inválidos: `512345678`, `60000000`, `+1234567890123456`, `a@b`; `422 PHONE_INVALID`/`EMAIL_INVALID` y nada guardado |
| Contacto sin consentimiento | US3-4, FR-011, SC-007 | dominio (propiedad) + REST | sin `onBehalfConsent` → `422 CONSENT_REQUIRED`; ninguna secuencia deja un valor sin `GRANTED` vigente |
| Consentimiento en nombre de un hermano sin usuario | US3-6, US3-7, FR-022, FR-026 | aplicación + persistencia | `noticeGiven=false` → `422 NOTICE_NOT_GIVEN`; con `true`, fila por campo con fecha, autor, canal y aviso; sin valores |
| Retirada | US3-5, US5-5, US7-4, FR-019, FR-027 | aplicación + persistencia | valor `null`, fila `WITHDRAWN`; el directorio deja de mostrarlo |
| Consentimiento de un vinculado | US3-8, US6-3, FR-015, FR-026 | dominio + REST | moderador cambia `shared` → `403 CONSENT_MANAGED_BY_MEMBER`; rectifica un valor compartido → `200` sin fila nueva |
| Nombres independientes | US3-9, US3-10, FR-029 | REST (Testcontainers) | renombrar el hermano no cambia `identity_user.name`; vincular no copia nada |
| Directorio propio, orden | US4-1, FR-009 | dominio (`DirectoryOrder`) + REST | por apellidos y después nombre; sin apellidos, por nombre; tildes y mayúsculas sin efecto |
| Directorio y ficha ajenos | US4-2, SC-005 | REST | directorio de B `403`; ficha de un hermano de B `404` idéntico a uno inexistente |
| Visitante | US4-3, FR-013 | REST + cliente | `401`; el cliente lleva al login |
| Moderador y administrador ven lo mismo | US4-5, FR-013 | REST | mismos campos de contacto; además `linkedUserId` |
| Error sin datos personales | US4-6, FR-021 | REST | `Problem` sin nombre, teléfono ni correo |
| Mis datos de contacto | US5-1, US5-4, US5-5, FR-014 | REST | `200` con valores válidos; inválido `422` y valores anteriores intactos; `shared=false` borra |
| Editar a otro o mi nombre o comunidad | US5-2, US5-3 | REST | `PUT /api/members/{otro}/contact` como registrado `403`; `MemberContactInput` no admite nombre ni comunidad |
| Sin hermano vinculado | US5 | REST + cliente | `GET /api/me/member` → `409 MEMBER_NOT_LINKED`; el cliente explica pedir el vínculo |
| Revisión tras consentimiento anotado | US5-6 | aplicación | `currentConsents` muestra canal `VERBAL`/`WRITTEN`; la decisión del hermano crea fila `APP` que prevalece |
| Moderador gestiona su comunidad y no otra | US6-1, US6-2 | REST | A `201`; B `403` (alta) y `404` (edición, borrado) |
| Moderador traslada o cambia responsable | US6-4 | REST | `403` |
| Traslado por administrador | US6-5, FR-016 | REST (Testcontainers, `identity` real) | hermano y usuario en B; destino desactivado `422 COMMUNITY_DEACTIVATED`; responsable `409 RESPONSIBLE_REQUIRED`; último moderador `409 LAST_MODERATOR` y nada cambia |
| Vincular | US6-6, US6-7, FR-028 | dominio + REST | registrado de A por moderador de A `200`; moderador con moderador, administrador o usuario de B `403`; administrador con usuario de B `422 USER_NOT_IN_COMMUNITY` |
| Uno a uno | US6-8 | persistencia (dos hilos) | `409 MEMBER_ALREADY_LINKED` / `USER_ALREADY_LINKED`; nunca dos vínculos |
| Desvincular | US6-9 | REST | administrador `204`; moderador con registrado de su comunidad `204`; el propio vinculado `403` |
| Cambio de comunidad desde identidad estando vinculado | US6-10, FR-030 | REST (Testcontainers) | `PUT /api/users/{id}` con otra comunidad → `409 USER_LINKED_TO_MEMBER` |
| Carrera vínculo frente a cambio de comunidad | FR-028, FR-030, R15 | persistencia (Testcontainers, dos hilos con barrera) | nunca queda un vínculo entre comunidades distintas; ninguna transacción acaba en `40P01` |
| Carrera borrar usuario (002) frente a trasladar su hermano | FR-016, FR-028, R15 | persistencia (Testcontainers, dos hilos con barrera) | una de las dos espera a la otra; sin `40P01`; el resultado final es coherente (o usuario borrado y hermano desvinculado en origen, o ambos trasladados y después usuario borrado) |
| Carrera borrar usuario (002) frente a borrar su hermano | FR-018, R15 | persistencia (Testcontainers, dos hilos) | sin `40P01`; una termina y la otra recibe `404`/`409` sin efectos parciales |
| Carrera vincular frente a borrar el usuario | FR-028, R15 | persistencia (Testcontainers, dos hilos con barrera) | nunca queda `community_member.user_id` apuntando a un usuario inexistente; el vínculo tardío recibe `422 USER_NOT_FOUND` |
| Carrera vincular frente a cambio de rol a moderador | FR-028, R15 | persistencia (Testcontainers, dos hilos) | con `lockUser`, el moderador nunca queda vinculado por otro moderador |
| Carrera trasladar frente a desactivar el destino | FR-005, FR-016, R15 | persistencia (Testcontainers, dos hilos con barrera) | nunca entra un hermano en una comunidad desactivada; el traslado tardío recibe `422 COMMUNITY_DEACTIVATED` |
| Traslado A→B confirmado mientras espera el borrado del usuario | FR-016, FR-018, R15 | persistencia (Testcontainers, dos hilos con barrera; el borrado se invoca a través de `TransactionRetry`, el mismo componente que usa el adaptador REST) | el borrado (iniciado con la lectura previa en A) relee el usuario en B, reintenta y bloquea B; `LAST_MODERATOR` se evalúa contra B (si era el último moderador de B → `409`, si no → borrado), nunca contra A |
| Relectura tras bloqueo | R15 | aplicación (fakes que cambian la fila entre lectura previa y bloqueo) | se lanza `StaleLockOrderException` cuando la relectura no coincide; nada más |
| Reintento en el adaptador | R15 | REST (MockMvc, caso de uso falso que siempre lanza `StaleLockOrderException`) en `identity` y `community` | el caso de uso se llama 3 veces y la respuesta es `409 CONCURRENT_MODIFICATION`; con una sola excepción, segunda llamada y `2xx` |
| Traslados cruzados A→B y B→A simultáneos | FR-016, R15 | persistencia (Testcontainers, dos hilos) | bloqueo por id: ambos terminan, sin `40P01` |
| Borrar usuario vinculado en identidad | US6-11, FR-028 | REST (Testcontainers) | el guardián desvincula antes del borrado: hermano sigue, `linkedUserId` vacío, nombre intacto; si `identity` rechaza el borrado (`LAST_MODERATOR`), el vínculo sigue |
| Borrar hermano | US7-1, FR-017, FR-018, SC-008 | REST (Testcontainers) | `204`; sin fila de hermano ni de consentimiento; si estaba vinculado a un registrado, usuario borrado y sesiones `401` |
| Moderador borra el suyo o a uno de moderador | US7-2 | REST | `403`; el administrador lo hace con `409 SELF_ACTION_FORBIDDEN`/`LAST_MODERATOR`/`LAST_ADMINISTRATOR` según 002 |
| Historial anonimizado | US7-3, SC-008 | REST (Testcontainers) | reservas y eventos del usuario borrado sin vínculo a datos personales (ya cubierto por `UserDeleted` en 001) |
| Copia de datos | US7-5, FR-020 | REST | todo lo guardado del hermano con su registro de consentimiento, nada más |
| Autor del consentimiento | FR-027, R17 | aplicación + REST | `recordedBy` con rol y nombre del moderador; tras borrar ese usuario, `deleted=true` sin nombre y el cliente muestra «usuario eliminado»; `MEMBER` sin nombre |
| Rectificación | US7-6, FR-020 | persistencia | el valor anterior no queda en ninguna tabla |
| Logs | SC-009, FR-021 | integración con el logger capturado | ni nombres, apellidos, teléfonos ni correos sintéticos en la salida |
| Carga inicial | R5 | integración (002 R12) | el arranque crea la comunidad en `community` y en `identity_community` con el mismo id |
| Migración | R13 | persistencia | con comunidades previas en `identity_community`, `V6` las copia a `community` activas y sin responsable |
| Módulos | principio III | `ModularityTest` | `community` solo depende de `identity`, `spaces`, `agenda` y `generated`; ninguno de ellos depende de `community` |

## 3. Prueba manual de punta a punta

Con backend y cliente en local (ver «Arranque local» de [002 quickstart](../002-identity-access/quickstart.md)) y 80
hermanos sintéticos en una comunidad de prueba. Los pasos cronometrados los hace una persona que no conoce las
pantallas; se anota el tiempo y si necesitó ayuda.

1. **SC-001, cronometrado (< 3 min)**. Como administrador: Gestión → Comunidades → nueva comunidad 12 (figura «Sin
   responsable») → Gestión → Hermanos (12) → alta de un hermano → Comunidades → 12 → Responsable: elegirlo. El reloj
   para al ver la ficha con responsable. Intentar borrar al responsable: se impide con el motivo.
2. Gestión → Usuarios: alta de un moderador y de un registrado en la 12.
3. **Sesión de moderador (US6) y SC-002, cronometrado (< 2 min)**. Como el moderador de la 12: Gestión → Hermanos →
   alta de un hermano con móvil, consentimiento verbal y aviso confirmado. El reloj para al verlo en la lista con su
   móvil. Después, en la misma sesión: vincular otro hermano con el registrado; intentar trasladar un hermano o cambiar
   el responsable (no aparece la acción y la API da `403`); intentar abrir un hermano de otra comunidad por URL (`404`).
4. Como moderador: **rectificación** del móvil del hermano sin usuario (el nuevo sustituye al anterior) y **retirada**
   del consentimiento de su móvil en su nombre (el valor desaparece del directorio; la copia de datos muestra la
   retirada con fecha, canal y «quién», sin el número).
5. **SC-003, cronometrado (< 30 s)**. Como el registrado: pestaña Comunidad → buscar un hermano por nombre → llamar con
   un toque.
6. **SC-004, primer intento**. Como el registrado, Cuenta → Mis datos de contacto: añadir correo y móvil compartidos y
   guardar, sin indicaciones previas. Se repite con 10 personas distintas (incluidas mayores de 70 si es posible): éxito
   si ≥ 9 guardan sin error ni ayuda. Después, **retirada propia**: apagar «Compartir» del correo y guardar; otro
   hermano ya no lo ve. Ver el origen del consentimiento anotado por el moderador y cambiarlo.
7. Como administrador: Gestión → Usuarios → cambiar la comunidad del registrado vinculado: se bloquea indicando
   trasladar al hermano. Trasladarlo desde Hermanos: usuario y hermano cambian juntos.
8. Desactivar la 12 (pide confirmación por tener moderador): la pestaña Comunidad del registrado muestra el estado sin
   datos; Reservas → Pendientes sigue mostrando el número 12 en sus solicitudes; reactivar.
9. Copia de datos de un hermano (con el autor de cada consentimiento) y borrado con confirmación; el usuario vinculado
   deja de poder entrar. Borrar el usuario del moderador y comprobar que la copia muestra «usuario eliminado» como
   autor.
10. Con VoiceOver o TalkBack, fuente al 200 %, movimiento reducido activado y en temas claro y oscuro: directorio, ficha
    y Mis datos de contacto legibles, con etiquetas en cada acción.
