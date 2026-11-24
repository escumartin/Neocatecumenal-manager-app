# Feature Specification: Identidad y acceso

**Feature Branch**: `002-identity-access`

**Created**: 2026-11-23

**Status**: Draft

**Input**: User description: "Identidad y acceso: los hermanos inician y cierran sesión con su identificador numérico y contraseña; el sistema sabe quién es el usuario actual y su rol (registrado, moderador, administrador); el administrador y el moderador dan de alta, editan y borran usuarios; sin autorregistro"

## Clarifications

### Session 2026-11-24

- Q: ¿Cómo se aplica el bloqueo temporal tras fallos de acceso? → A: por identificador y origen: 5 fallos seguidos desde el mismo origen bloquean ese identificador 15 minutos en ese origen; además, un tope de fallos por origen frena los barridos de identificadores.
- Q: ¿Cuánto dura la sesión? → A: se renueva con cada uso y caduca tras 30 días sin uso, con un máximo absoluto de 90 días.
- Q: ¿Quién fija el identificador numérico de acceso de un usuario nuevo? → A: lo genera el sistema: número aleatorio de 6 dígitos, único e inmutable.
- Q: ¿Qué contraseña mínima se exige? → A: entre 8 y 64 caracteres, sin reglas de composición; se rechazan las de una lista de contraseñas comunes y las que contienen el identificador.
- Q: ¿Qué impide Identidad al borrar o cambiar a un moderador? → A: que una comunidad se quede sin ningún moderador; el responsable como persona concreta pertenece a «Comunidades y hermanos».

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Iniciar y cerrar sesión (Priority: P1)

Un hermano abre la app, escribe su identificador numérico y su contraseña y entra. Mientras la sesión está activa, el sistema sabe quién es, a qué comunidad pertenece y qué rol tiene. Puede cerrar sesión en cualquier momento. Un visitante sin sesión solo ve las noticias públicas y la pantalla de acceso.

**Why this priority**: sin saber quién es el usuario actual y su rol no se puede proteger nada; es lo que necesita Reservas de salones para distinguir moderador de administrador.

**Independent Test**: con un usuario de prueba creado de antemano, iniciar sesión con credenciales correctas, ver su nombre y rol, cerrar sesión y comprobar que vuelve a ser visitante.

**Acceptance Scenarios**:

1. **Given** un usuario con identificador y contraseña válidos, **When** los introduce, **Then** entra, y el sistema le reconoce con su identificador, comunidad y rol.
2. **Given** un identificador inexistente o una contraseña incorrecta, **When** intenta entrar, **Then** recibe el mismo mensaje genérico en ambos casos y no entra.
3. **Given** un usuario con sesión, **When** cierra sesión, **Then** deja de estar identificado y no puede usar acciones protegidas sin volver a entrar.
4. **Given** un visitante sin sesión, **When** intenta abrir una pantalla o acción que exige rol, **Then** se le lleva a iniciar sesión y no se muestra ningún dato protegido.
5. **Given** un usuario que falla la contraseña varias veces seguidas, **When** supera el límite de intentos, **Then** el acceso se bloquea temporalmente y se le informa de cuánto debe esperar.

---

### User Story 2 - Permisos por rol y comunidad (Priority: P1)

Cada acción protegida de la app se concede según el rol del usuario (registrado < moderador < administrador, con herencia) y, para el moderador, solo dentro de su comunidad. El administrador opera en cualquier comunidad, eligiendo antes sobre cuál. Otras features consultan este contexto para saber el usuario actual, su rol y su comunidad.

**Why this priority**: es el contrato que Reservas de salones y el resto de features necesitan; sin él cada feature reinventaría los permisos.

**Independent Test**: con un usuario de cada rol, comprobar qué acciones de prueba se permiten o se deniegan y que el moderador no alcanza datos de otra comunidad.

**Acceptance Scenarios**:

1. **Given** un registrado, **When** intenta una acción reservada a moderador o administrador, **Then** el sistema la deniega.
2. **Given** un moderador de la comunidad A, **When** intenta operar sobre datos de la comunidad B, **Then** el sistema lo deniega.
3. **Given** un administrador, **When** elige una comunidad y opera, **Then** puede hacerlo sobre cualquier comunidad.
4. **Given** un usuario cuyo rol cambia, **When** realiza su siguiente acción, **Then** se aplica el rol nuevo, sin esperar a volver a iniciar sesión.

---

### User Story 3 - Alta, edición y borrado de usuarios (Priority: P2)

El administrador da de alta, edita y borra usuarios de cualquier comunidad. El moderador lo hace solo para su comunidad y solo con rol registrado. No hay autorregistro: el hermano recibe de quien le da de alta su identificador numérico y una contraseña inicial. El borrado pide confirmación y es definitivo.

**Why this priority**: sin altas no hay usuarios reales; va después del acceso porque el primer administrador puede cargarse de forma inicial.

**Independent Test**: un moderador crea un usuario registrado en su comunidad, lo edita y lo borra tras confirmar; un intento de crear un moderador o un usuario en otra comunidad se deniega.

**Acceptance Scenarios**:

1. **Given** un moderador, **When** da de alta a un hermano de su comunidad, **Then** el usuario queda creado con rol registrado y el sistema le asigna un identificador numérico único de 6 dígitos.
2. **Given** un moderador, **When** intenta dar de alta un usuario con rol moderador o administrador, o en otra comunidad, **Then** el sistema lo deniega.
3. **Given** un administrador, **When** da de alta un usuario en cualquier comunidad con cualquier rol, **Then** queda creado.
4. **Given** un alta con datos inválidos, **When** se envía, **Then** se rechaza con un mensaje claro y no se crea nada.
5. **Given** un usuario existente, **When** quien tiene permiso lo borra y confirma, **Then** deja de poder iniciar sesión y se aplica la política de borrado de datos.
6. **Given** un administrador o moderador, **When** pide borrar su propia cuenta, o borrar, degradar o cambiar de comunidad al último moderador de una comunidad, **Then** el sistema lo impide.

---

### User Story 4 - Contraseña propia y recuperación (Priority: P2)

Un usuario cambia su propia contraseña. La contraseña inicial que recibe de quien le da de alta, o de quien se la reasigna si la olvida, debe cambiarse en el primer acceso. La edición de sus datos de contacto queda fuera de esta feature (pasa a «Comunidades y hermanos»).

**Why this priority**: evita que quien da de alta conozca la contraseña definitiva y permite recuperar el acceso sin depender del email.

**Independent Test**: un usuario recién creado entra, es obligado a cambiar la contraseña inicial, y después cambia de nuevo su contraseña indicando la actual.

**Acceptance Scenarios**:

1. **Given** un usuario con contraseña inicial, **When** inicia sesión por primera vez, **Then** debe fijar una contraseña nueva antes de usar la app.
2. **Given** un usuario con sesión, **When** cambia su contraseña indicando la actual, **Then** la nueva sustituye a la anterior y la antigua deja de valer.
3. **Given** un usuario con sesión, **When** intenta cambiar su rol, comunidad o identificador, **Then** el sistema lo deniega.
4. **Given** un usuario que olvida su contraseña, **When** su moderador (o un administrador) le asigna una contraseña inicial nueva, **Then** puede entrar con ella, debe cambiarla en ese primer acceso y la anterior deja de valer.

---

### Edge Cases

- Dos altas simultáneas que reciben el mismo identificador generado: el sistema genera otro para una de ellas y ambas se crean con identificadores distintos.
- Sesión caducada durante una acción: se pide volver a entrar y la acción no se ejecuta.
- Usuario borrado con sesión abierta: su siguiente acción se deniega y se le lleva a iniciar sesión.
- Moderador que cambia de comunidad o de rol: pierde de inmediato los permisos de la anterior; si era el último moderador de su comunidad, el cambio se impide.
- Usuario de 90 años con poca práctica: contraseña de hasta 64 caracteres, mensajes de error claros y opción de mostrar u ocultar la contraseña.
- Último administrador: no puede borrarse ni perder el rol.
- Usuario con datos asociados en otras features (reservas, eventos): su historial se conserva anonimizado al borrarlo (FR-023).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Los usuarios MUST poder iniciar sesión con su identificador numérico y su contraseña, y cerrar sesión.
- **FR-002**: El sistema MUST responder con el mismo mensaje genérico a un identificador inexistente y a una contraseña incorrecta.
- **FR-003**: El origen de un intento es su dirección IP. El sistema MUST bloquear 15 minutos un identificador en un origen tras 5 fallos seguidos desde ese origen, y MUST bloquear 15 minutos un origen tras 20 fallos en 15 minutos, sea cual sea el identificador; en ambos casos informa del tiempo de espera. Los fallos desde otro origen no bloquean al titular. Los identificadores inexistentes cuentan y se responden igual que los existentes, para no revelar cuáles existen (FR-002).
- **FR-004**: El sistema MUST ofrecer a las demás features, para cada petición, el usuario actual (clave interna, identificador, nombre), su rol y su comunidad, o la condición de visitante.
- **FR-005**: El sistema MUST distinguir cuatro niveles con herencia: no identificado < registrado < moderador < administrador.
- **FR-006**: El sistema MUST denegar toda acción protegida a quien no tenga el rol exigido, y toda acción sobre datos de otra comunidad a un moderador.
- **FR-007**: El administrador MUST elegir una comunidad antes de operar sobre ella y MUST poder operar sobre cualquiera.
- **FR-008**: Los cambios de rol o de comunidad de un usuario MUST aplicarse en su siguiente acción.
- **FR-009**: El administrador MUST poder dar de alta, editar y borrar usuarios de cualquier comunidad y con cualquier rol.
- **FR-010**: El moderador MUST poder dar de alta, editar y borrar solo usuarios con rol registrado de su comunidad. El moderador edita el nombre y reasigna la contraseña inicial; cambiar el rol o la comunidad de un usuario es solo del administrador.
- **FR-011**: El sistema MUST NOT ofrecer autorregistro: un usuario solo se crea por el alta de un administrador o moderador.
- **FR-012**: Cada usuario MUST tener una clave interna inmutable y un identificador numérico de acceso único, que el sistema genera al dar de alta (aleatorio entre 100000 y 999999, inmutable); cada comunidad MUST tener un número único. Ambos números son atributos de negocio, no la clave interna.
- **FR-013**: El alta MUST validar los datos obligatorios, garantizar la unicidad del identificador generado y rechazar el alta inválida sin crear nada.
- **FR-014**: La contraseña inicial MUST cambiarse en el primer acceso; el usuario MUST poder cambiarla después indicando la actual.
- **FR-015**: Las contraseñas MUST guardarse de forma que ni administradores ni moderadores puedan leerlas, MUST tener entre 8 y 64 caracteres sin reglas de composición y MUST rechazar las de una lista de contraseñas comunes y las que contengan el identificador.
- **FR-016**: Los usuarios MUST NOT poder editar su propio rol, comunidad ni identificador. La edición de datos de contacto (teléfono fijo, móvil, email) queda fuera de esta feature y pertenece a «Comunidades y hermanos».
- **FR-017**: El borrado de un usuario MUST pedir confirmación, ser definitivo e impedir su acceso de inmediato.
- **FR-018**: El sistema MUST impedir borrar la propia cuenta, quitar el rol al último administrador y dejar a una comunidad sin ningún moderador (por borrado, cambio de rol o cambio de comunidad).
- **FR-019**: Los datos personales MUST limitarse a lo que justifica cada campo, MUST NOT aparecer en registros técnicos y MUST poder consultarse, rectificarse y borrarse a petición del titular.
- **FR-020**: Se MUST informar al usuario, en el primer acceso, del tratamiento de sus datos y registrar su aceptación. Si no acepta, no puede usar ninguna acción protegida, su sesión se cierra y el aviso se le vuelve a mostrar en el siguiente acceso.
- **FR-021**: Las pantallas de acceso y gestión de usuarios MUST cumplir los requisitos de accesibilidad y uso móvil del proyecto (texto legible, objetivos táctiles amplios, contraste AA, etiquetas en todos los controles).
- **FR-022**: Un usuario que olvida su contraseña MUST recuperar el acceso porque su moderador (para usuarios con rol registrado de su comunidad) o un administrador le asigna una contraseña inicial nueva, que debe cambiar al entrar. La reasignación MUST cerrar todas las sesiones abiertas del usuario. No hay recuperación por email.
- **FR-023**: Al borrar un usuario, su historial en otras features (p. ej. solicitudes de reserva) MUST anonimizarse: se conserva sin datos personales.
- **FR-024**: Este contexto MUST guardar de cada usuario solo los datos de acceso (identificador, nombre, rol, comunidad) y la aceptación del aviso de datos; los datos de contacto pertenecen a «Comunidades y hermanos».
- **FR-025**: El sistema MUST ofrecer a las demás features una lista de solo lectura de las comunidades (clave interna UUID y número) para que el administrador elija sobre cuál operar (FR-007).
- **FR-026**: La sesión MUST renovarse con cada uso y caducar tras 30 días sin uso, con un máximo absoluto de 90 días desde el inicio de sesión.
- **FR-027**: Cuando un usuario cambia su propia contraseña, MUST cerrarse el resto de sus sesiones abiertas y conservarse la actual.
- **FR-028**: Base legal del tratamiento: art. 9.2.d RGPD (datos de miembros tratados por una entidad religiosa en su actividad legítima, sin comunicarlos fuera de ella sin consentimiento) y art. 6.1.f. Justificación de cada dato: identificador y contraseña, para el acceso; nombre, para que el moderador reconozca al hermano; rol y comunidad, para los permisos (FR-005, FR-006); aceptación del aviso, para acreditar que se informó al titular (FR-020); IP de los intentos fallidos, para frenar ataques de fuerza bruta (FR-003, art. 6.1.f), conservada solo mientras dura su ventana de bloqueo de 15 minutos.

### Key Entities *(include if feature involves data)*

- **Usuario**: persona con acceso a la app; atributos: clave interna inmutable, identificador numérico de acceso (único), nombre, rol, comunidad, contraseña inicial pendiente de cambio, aceptación del tratamiento de datos. El bloqueo por fallos de acceso no es un estado del usuario: se aplica al par identificador y origen (FR-003). Sin datos de contacto (FR-024).
- **Rol**: registrado, moderador o administrador, con herencia; el visitante es la ausencia de sesión.
- **Comunidad (referencia)**: UUID y número único; el usuario pertenece a una. Identidad solo expone la lista de solo lectura; el alta, la edición y el responsable de la comunidad pertenecen a «Comunidades y hermanos».
- **Sesión**: periodo en que un usuario está identificado; termina al cerrar sesión, por caducidad (FR-026), por borrado del usuario, por no aceptar el aviso de datos (FR-020) o por cambio o reasignación de contraseña (FR-022, FR-027).
- **Intento de acceso**: resultado de un inicio de sesión fallido, usado para el bloqueo temporal; guarda el identificador y la IP de origen, y se descarta al vencer la ventana de 15 minutos (FR-003, FR-028).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un hermano inicia sesión en menos de 30 segundos desde el móvil.
- **SC-002**: El 90 % de los usuarios entra a la primera y cambia su contraseña inicial sin ayuda.
- **SC-003**: El 100 % de las pruebas de permisos (cada rol por cada acción protegida) concede o deniega según la matriz de roles.
- **SC-004**: 0 casos en que un moderador lee o modifica datos de otra comunidad en las pruebas de aislamiento.
- **SC-005**: Un moderador da de alta a un hermano en menos de 2 minutos.
- **SC-006**: Tras borrar un usuario, 0 accesos posteriores con sus credenciales.
- **SC-007**: Ninguna contraseña ni dato personal aparece en los registros técnicos, comprobado en las pruebas.

## Assumptions

- Es la primera feature a implementar; Reservas de salones (`001-reservas-salones`) la consume para conocer al usuario actual y su rol.
- Decisión: Identidad no crea un contexto de Comunidad; consume la referencia (UUID y número) que mantiene «Comunidades y hermanos» y la expone de solo lectura (FR-025). Hasta que ese contexto exista, la lista se alimenta con datos iniciales.
- Fuera de alcance: edición de datos de contacto (teléfonos, email, dirección, sexo) y recuperación por email.
- Los roles y la herencia son los de `dominio.md`; el rol vocal y las notificaciones por email o push quedan fuera de alcance.
- La gestión de comunidades (alta, número, responsable) pertenece a «Comunidades y hermanos»; esta feature solo referencia la comunidad por su número y asume que existe.
- Se usan una clave interna UUID y dos atributos de negocio con unicidad: identificador numérico de acceso y número de comunidad.
- El primer administrador se crea en la puesta en marcha, fuera de la app.
- La sesión se renueva con el uso y caduca tras 30 días sin uso, con un máximo absoluto de 90 días.
- Los usuarios tienen entre 14 y 90 años; el consentimiento de tutores de menores se gestiona fuera de la app.
- Textos de la interfaz en castellano, mediante ficheros de recursos.
