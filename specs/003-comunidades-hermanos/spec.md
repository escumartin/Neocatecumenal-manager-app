# Feature Specification: Comunidades y hermanos

**Feature Branch**: `003-comunidades-hermanos`

**Created**: 2026-11-25

**Status**: Draft

**Input**: User description: "Comunidades y hermanos: el administrador gestiona las comunidades (alta, número, baja con restricciones); cada comunidad tiene un responsable obligatorio; los hermanos de una comunidad tienen datos de contacto mínimos con consentimiento; el registrado consulta el directorio de su comunidad y edita sus propios datos de contacto; el moderador gestiona los hermanos de su comunidad; derecho de supresión"

## Clarifications

### Session 2026-11-25

- Q: ¿«Hermano» y «usuario» son la misma persona registrada una vez o dos registros? → A: dos registros vinculables: el hermano existe sin usuario y se vincula a uno de la app después.
- Q: ¿Qué datos de contacto se guardan y quién los ve? → A: móvil, fijo y correo, visibles solo para los hermanos de su comunidad (y para sus moderadores y los administradores), con consentimiento por campo; no se guardan dirección ni sexo.
- Q: ¿El directorio se limita a la propia comunidad? → A: sí, solo la propia comunidad.
- Q: ¿Cómo se registra el consentimiento de un hermano sin usuario de la app? → A: el moderador lo registra en su nombre tras recibirlo de forma verbal o escrita, con fecha, quién lo anotó y por campo; cuando el hermano tenga usuario vinculado, puede revisarlo y cambiarlo él mismo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Gestionar comunidades (Priority: P1)

El administrador da de alta una comunidad con su número y, si hace falta, un nombre o referencia de la parroquia. Puede cambiarle el número o el nombre, desactivarla o, si está vacía, borrarla. Las comunidades que crea son las que Identidad y acceso (`002-identity-access`) consulta como lista de solo lectura para asignar usuarios y para que el administrador elija sobre cuál operar.

**Why this priority**: sin comunidades no hay hermanos, directorio ni responsables; Identidad y acceso las necesita como referencia real en lugar de datos iniciales.

**Independent Test**: el administrador crea la comunidad 12, le cambia el nombre, la desactiva y comprueba que no admite hermanos nuevos; un moderador no puede hacer nada de esto.

**Acceptance Scenarios**:

1. **Given** un administrador, **When** da de alta una comunidad con un número no usado, **Then** queda creada, activa y en estado «sin responsable», y aparece en la lista de comunidades.
2. **Given** un administrador, **When** da de alta o renumera una comunidad con un número ya usado, **Then** el sistema lo rechaza con un mensaje claro y no cambia nada.
3. **Given** un moderador o un registrado, **When** intenta crear, renumerar, desactivar o borrar una comunidad, **Then** el sistema lo deniega.
4. **Given** una comunidad con responsable, **When** el administrador intenta borrarla, **Then** el sistema lo impide, porque una comunidad con responsable siempre tiene hermanos, y le ofrece desactivarla.
5. **Given** una comunidad sin responsable, sin hermanos y sin usuarios, **When** el administrador la borra y confirma, **Then** desaparece de forma definitiva.
6. **Given** una comunidad con algún usuario con rol moderador o administrador, **When** el administrador la desactiva, **Then** el sistema pide confirmación expresa antes de hacerlo; sin usuarios con esos roles, se desactiva directamente.
7. **Given** una comunidad desactivada, **When** alguien consulta las comunidades para nuevas altas o abre su directorio, **Then** no aparece ni se muestra; sus datos se conservan y el administrador puede reactivarla.
8. **Given** una comunidad desactivada, **When** alguien intenta darle de alta un hermano o un usuario nuevo, **Then** el sistema lo impide.
9. **Given** Identidad y acceso consultando las comunidades, **When** el administrador ha creado una comunidad, **Then** la lista de solo lectura la incluye con su clave interna y su número.

---

### User Story 2 - Responsable obligatorio de cada comunidad (Priority: P1)

Una comunidad nueva nace activa y «sin responsable», un estado explícito y visible, no un error. Se le pueden añadir hermanos. El administrador designa como responsable a uno de los hermanos de esa comunidad. Desde ese momento la comunidad ya nunca puede quedarse sin responsable. El responsable es quien lleva la comunidad y normalmente es su moderador en la app, pero ser responsable y tener rol de moderador son dos hechos distintos.

**Why this priority**: es una regla de negocio central del dominio («cada comunidad tiene un responsable obligatorio»); sin ella una comunidad queda sin nadie a quien dirigirse.

**Independent Test**: crear una comunidad sin responsable, añadirle hermanos, designar uno, y comprobar que no se puede borrar ni sacar de la comunidad a ese hermano hasta designar otro.

**Acceptance Scenarios**:

1. **Given** una comunidad sin responsable, **When** se consulta su ficha o la lista de comunidades, **Then** figura de forma visible como «sin responsable».
2. **Given** una comunidad sin responsable, **When** el moderador o el administrador le añaden hermanos, **Then** se crean con normalidad.
3. **Given** un administrador, **When** designa como responsable a un hermano de esa comunidad, **Then** queda registrado y la comunidad deja de estar «sin responsable».
4. **Given** un administrador, **When** designa como responsable a un hermano de otra comunidad, **Then** el sistema lo rechaza.
5. **Given** una comunidad con responsable, **When** alguien intenta borrar o mover a otra comunidad a ese hermano sin designar antes otro, **Then** el sistema lo impide indicando el motivo.
6. **Given** un administrador, **When** cambia el responsable por otro hermano de la comunidad, **Then** el nuevo queda registrado y el anterior deja de serlo, sin que la comunidad quede sin responsable en ningún instante.
7. **Given** una comunidad cuyo responsable no tiene rol de moderador, **When** se consulta su ficha, **Then** el sistema avisa de la diferencia sin impedir el trabajo.
8. **Given** un moderador, **When** intenta designar o cambiar el responsable, **Then** el sistema lo deniega.

---

### User Story 3 - Hermanos de una comunidad y sus datos de contacto (Priority: P1)

Una comunidad tiene sus hermanos. De cada uno se guarda solo lo imprescindible para localizarlo: nombre (obligatorio), apellidos (opcionales) y datos de contacto opcionales: móvil, fijo y correo. No se guardan dirección ni sexo. Cada dato de contacto se guarda solo si el hermano ha dado su consentimiento para ese campo; sin consentimiento, no se guarda. Un hermano puede o no tener usuario en la app; son dos registros distintos que se pueden vincular (ver historia 6).

**Why this priority**: son los datos que dan sentido al directorio; hasta que existen, la comunidad es solo un número.

**Independent Test**: el moderador añade a un hermano con nombre y móvil, con su consentimiento, y comprueba que figura en la comunidad y que no se ha guardado ningún otro dato.

**Acceptance Scenarios**:

1. **Given** un hermano dado de alta, **When** se consulta su ficha, **Then** solo contiene nombre, apellidos (si los hay), comunidad y los datos de contacto consentidos.
2. **Given** un alta sin nombre, **When** se envía, **Then** se rechaza con un mensaje claro y no se crea nada; un alta sin apellidos es válida.
3. **Given** un móvil o un fijo que no es un número nacional de 9 dígitos que empieza por 6, 7, 8 o 9 ni uno internacional con + y hasta 15 dígitos, o un correo sin sintaxis de dirección válida, **When** se envía, **Then** se rechaza con un mensaje claro y no se guarda nada.
4. **Given** un dato de contacto sin consentimiento, **When** se intenta guardar, **Then** el sistema no lo guarda.
5. **Given** un hermano que retira el consentimiento sobre un dato, **When** se registra, **Then** el valor se borra y deja de mostrarse a todos.
6. **Given** un hermano sin usuario, **When** el moderador registra su consentimiento, **Then** debe confirmar que le ha dado el aviso de datos (qué se guarda, para qué y quién lo ve) de forma verbal o en papel, y esa confirmación queda dentro del registro de consentimiento.
7. **Given** un hermano sin usuario que da su consentimiento de forma verbal o escrita, **When** el moderador lo registra, **Then** queda guardado por campo con la fecha, quién lo anotó y que se recibió en su nombre, y solo entonces el dato se guarda y se muestra.
8. **Given** un hermano con usuario vinculado, **When** se registra o cambia su consentimiento, **Then** solo puede hacerlo él mismo desde su usuario.
9. **Given** un hermano con usuario vinculado, **When** se consulta su ficha, **Then** muestra su propio nombre y apellidos, no se duplican los datos de acceso del usuario y el directorio muestra siempre el nombre del hermano.
10. **Given** un hermano vinculado a un usuario, **When** se editan los apellidos del hermano, **Then** el nombre del usuario en Identidad y acceso no cambia.

---

### User Story 4 - Directorio de la propia comunidad (Priority: P2)

El registrado abre el directorio de su comunidad y ve a sus hermanos con nombre, apellidos y los datos de contacto consentidos. Puede llamar o escribir desde el móvil con un toque. No ve el directorio de otras comunidades. Los moderadores de la comunidad y los administradores ven los mismos datos.

**Why this priority**: es el beneficio visible para el hermano de a pie, pero necesita que antes existan comunidades y hermanos.

**Independent Test**: con un registrado de la comunidad A, comprobar que ve a los hermanos de A con sus datos consentidos y ninguno de la comunidad B.

**Acceptance Scenarios**:

1. **Given** un registrado de la comunidad A, **When** abre el directorio, **Then** ve a los hermanos de A ordenados por apellidos y después por nombre; los que no tienen apellidos se ordenan por nombre.
2. **Given** un registrado de la comunidad A, **When** intenta ver el directorio o la ficha de un hermano de B, **Then** el sistema lo deniega sin revelar si existe.
3. **Given** un visitante sin sesión, **When** intenta abrir el directorio, **Then** se le lleva a iniciar sesión y no ve ningún dato.
4. **Given** un directorio de hasta 80 hermanos, **When** se abre en el móvil, **Then** la lista es legible y se puede buscar por nombre.
5. **Given** un administrador que ha elegido una comunidad, o un moderador de ella, **When** abre su directorio o las pantallas de gestión, **Then** ve los mismos datos que un registrado de esa comunidad.
6. **Given** un error al consultar el directorio, **When** se muestra el mensaje, **Then** no incluye datos personales de ningún hermano.

---

### User Story 5 - Editar mis propios datos de contacto (Priority: P2)

El registrado cambia su teléfono fijo, su móvil y su correo, y decide cuáles quiere dar a conocer a los demás hermanos de su comunidad. Ocultar un dato equivale a retirar su consentimiento: el valor se borra. No puede cambiar su nombre, su comunidad, su rol ni su identificador. Esta historia sustituye a la edición de contacto que `002-identity-access` dejó fuera de su alcance (FR-016).

**Why this priority**: mantiene vivo el directorio sin cargar al moderador y respeta el control del titular sobre sus datos.

**Independent Test**: un registrado cambia su móvil y retira el correo; otro hermano de la comunidad ve el móvil nuevo y no ve el correo, que ya no existe en el sistema.

**Acceptance Scenarios**:

1. **Given** un registrado con sesión, **When** modifica su móvil, fijo o correo con valores válidos y consiente, **Then** los cambios se guardan y se ven en el directorio.
2. **Given** un registrado, **When** intenta modificar los datos de contacto de otro hermano, **Then** el sistema lo deniega.
3. **Given** un registrado, **When** intenta cambiar su nombre, comunidad o rol, **Then** el sistema lo deniega.
4. **Given** un registrado, **When** envía un teléfono o correo inválido, **Then** se rechaza con un mensaje claro y se conservan los valores anteriores.
5. **Given** un registrado que oculta o borra un dato de contacto, **When** guarda, **Then** se registra como retirada de consentimiento, el valor se borra y desaparece del directorio.
6. **Given** un registrado que entra por primera vez tras haberle anotado su moderador el consentimiento, **When** revisa su consentimiento, **Then** puede mantenerlo o cambiarlo, y su decisión prevalece.

---

### User Story 6 - El moderador gestiona los hermanos de su comunidad (Priority: P2)

El moderador da de alta, edita y borra a los hermanos de su comunidad, incluidos los que no usan la app, y corrige los datos de contacto de cualquier hermano de su comunidad. Puede vincular un hermano de su comunidad con un usuario registrado de la misma comunidad. No puede cambiar el responsable ni mover hermanos entre comunidades. El administrador puede hacer todo esto en cualquier comunidad, eligiendo antes sobre cuál, y además mueve hermanos entre comunidades.

**Why this priority**: es la forma de mantener los datos de quienes no usan la app y de ayudar a los mayores; va después del directorio porque este ya aporta valor con los datos que cada uno edita.

**Independent Test**: el moderador de A crea un hermano sin usuario, corrige su móvil, lo vincula a un usuario registrado de A y lo borra tras confirmar; un intento sobre un hermano de B se deniega.

**Acceptance Scenarios**:

1. **Given** un moderador de la comunidad A, **When** da de alta a un hermano de A, **Then** queda creado en A.
2. **Given** un moderador de la comunidad A, **When** intenta dar de alta, editar o borrar un hermano de B, **Then** el sistema lo deniega.
3. **Given** un moderador, **When** corrige un dato de contacto de un hermano de su comunidad, **Then** el cambio se guarda; el consentimiento de un hermano vinculado a un usuario no lo gestiona el moderador.
4. **Given** un moderador, **When** intenta cambiar el responsable de su comunidad o mover un hermano a otra, **Then** el sistema lo deniega.
5. **Given** un administrador, **When** mueve un hermano a otra comunidad, **Then** el hermano deja el directorio anterior de inmediato y, si estaba vinculado, su usuario se mueve con él; el sistema lo impide si dejaría a la comunidad de origen sin moderador, si es el responsable (hasta designar otro) o si la comunidad de destino está desactivada.
6. **Given** un hermano sin usuario y un usuario con rol registrado de la misma comunidad, **When** el moderador o el administrador los vinculan, **Then** quedan vinculados uno a uno; la pantalla puede sugerir el nombre del usuario, pero no se copia nada de forma implícita.
7. **Given** un moderador, **When** intenta vincular un hermano con un moderador, un administrador o un usuario de otra comunidad, **Then** el sistema lo deniega; si lo hace un administrador, solo exige que ambos sean de la misma comunidad.
8. **Given** un hermano ya vinculado, **When** se intenta vincular a otro usuario, o un usuario ya vinculado a otro hermano, **Then** el sistema lo rechaza.
9. **Given** un vínculo, **When** el administrador lo desvincula, **Then** se deshace; un moderador solo puede desvincular los vínculos de su comunidad con usuarios registrados, y el usuario vinculado no puede desvincularse él mismo.
10. **Given** un hermano vinculado, **When** el administrador cambia su comunidad desde Identidad y acceso, **Then** el sistema lo impide con un mensaje que indica mover antes al hermano.
11. **Given** un hermano vinculado a un usuario, **When** se borra ese usuario en Identidad y acceso, **Then** el hermano permanece, desvinculado y con su propio nombre.

---

### User Story 7 - Supresión, rectificación y copia de datos (Priority: P2)

Un hermano puede pedir que se borren sus datos de contacto, que se le borre por completo, que se rectifique algo o que se le dé una copia. El moderador (o el administrador) lo ejecuta y el sistema elimina los datos personales de forma definitiva, conservando solo lo anonimizado que otras features necesiten para su historial.

**Why this priority**: es obligación legal y principio de la constitución del proyecto (privacidad por diseño); se prioriza después del núcleo funcional pero no puede faltar al publicar.

**Independent Test**: borrar a un hermano con historial en otra feature y comprobar que desaparece del directorio, que sus datos y su registro de consentimiento no se pueden recuperar y que el historial queda sin datos personales.

**Acceptance Scenarios**:

1. **Given** un moderador y un hermano de su comunidad sin usuario o vinculado a un usuario registrado, **When** lo borra y confirma, **Then** sus datos personales se eliminan junto con su registro de consentimiento, y si tenía usuario, este también se borra conforme a Identidad y acceso (FR-010).
2. **Given** un moderador, **When** intenta borrar a su propio hermano o a uno vinculado a un moderador o administrador, **Then** el sistema lo deniega; lo hace el administrador, siempre con las protecciones de Identidad y acceso (FR-018): sin borrado propio, sin dejar la comunidad sin moderador y sin borrar al último administrador.
3. **Given** un hermano con historial en otras features (p. ej. reservas), **When** se le borra, **Then** el historial se conserva anonimizado, sin nombre ni contacto.
4. **Given** un hermano que retira el consentimiento sobre su contacto, **When** se registra (él mismo o, si no tiene usuario, el moderador en su nombre), **Then** los valores se borran, dejan de mostrarse y se conserva la constancia de la retirada con fecha, quién y campos, sin los valores.
5. **Given** un hermano que pide una copia de sus datos, **When** el moderador la prepara, **Then** recibe todo lo que el sistema guarda sobre él, incluido su registro de consentimiento, y solo eso.
6. **Given** un hermano que pide rectificar un dato, **When** el moderador lo corrige, **Then** el dato nuevo sustituye al anterior y el anterior no se conserva.

---

### Edge Cases

- Dos altas de comunidad con el mismo número a la vez: solo se crea una y la otra se rechaza por número en uso.
- Comunidad recién creada y sin responsable: es un estado válido y visible; admite hermanos hasta que se designe uno.
- Borrar o sacar de su comunidad al hermano responsable: se impide hasta designar otro responsable.
- Responsable que además es el último moderador de la comunidad: no puede borrarse ni perder el rol (regla de Identidad y acceso, FR-018).
- Desactivar una comunidad con moderadores o administradores: pide confirmación expresa; sus datos se conservan.
- Comunidad desactivada con usuarios que aún inician sesión: pueden consultar su historial, pero no se dan altas nuevas hasta reactivarla.
- Hermano sin teléfono ni correo, o sin apellidos: es válido; solo es obligatorio el nombre.
- Dos hermanos con el mismo nombre y apellidos en una comunidad: se permite; se distinguen por su contacto.
- Hermano que cambia de comunidad: solo lo hace el administrador; si está vinculado, su usuario va con él.
- Mover a un hermano vinculado a un moderador que es el último de su comunidad: se bloquea (Identidad y acceso, FR-018).
- Mover al responsable: se bloquea hasta designar otro responsable.
- Mover a un hermano a una comunidad desactivada: se bloquea, como un alta nueva (FR-005).
- Cambio de comunidad del usuario desde Identidad y acceso mientras está vinculado: se bloquea y se indica al administrador que mueva al hermano.
- Hermano sin usuario de la app: existe y aparece en el directorio, pero no inicia sesión; si se le vincula un usuario después, no se duplican datos y cada registro conserva su propio nombre.
- Borrar un usuario en Identidad y acceso mientras está vinculado: el hermano permanece, desvinculado y con su propio nombre, que nunca cambió.
- Usuario sin hermano vinculado (p. ej. el primer administrador): puede iniciar sesión; no aparece en ningún directorio hasta vincularse.
- Consentimiento anotado por el moderador y usuario vinculado después: sigue vigente hasta que el hermano lo cambie; su decisión prevalece.
- Moderador que anota un consentimiento sin haberlo recibido: el registro queda con su autoría y fecha, lo que permite auditarlo y corregirlo si el hermano lo niega.
- Retirada de consentimiento: el valor se borra, pero la constancia de la retirada (fecha, quién, campos y origen) permanece mientras exista el hermano.
- Moderador que intenta borrar a su propio hermano o a uno vinculado a un moderador o administrador: se deniega y lo gestiona el administrador.
- Usuario de 90 años con poca práctica: pantallas con texto grande, mensajes claros y acciones de llamada con un toque.
- Intento de enseñar a un visitante o a otra comunidad datos de contacto: se deniega sin revelar si el hermano existe.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El administrador MUST poder dar de alta, renumerar, renombrar, desactivar, reactivar y borrar comunidades; ningún otro rol puede.
- **FR-002**: Cada comunidad MUST tener un número único; el sistema MUST rechazar el alta o el cambio de número si ya está en uso.
- **FR-003**: Una comunidad MAY tener un nombre o referencia de parroquia opcional, que no sustituye a su número.
- **FR-004**: El sistema MUST permitir borrar solo una comunidad sin hermanos ni usuarios (la que está sin responsable y vacía); una comunidad con responsable siempre tiene hermanos y MUST NOT poder borrarse, solo desactivarse. Borrar pide confirmación y es definitivo.
- **FR-005**: El sistema MUST permitir desactivar una comunidad solo si no tiene usuarios con un rol distinto de registrado o si el administrador lo confirma expresamente. Una comunidad desactivada MUST ocultarse para nuevas altas y de su directorio, MUST conservar sus datos, MUST NOT admitir altas nuevas de hermanos ni de usuarios y MUST poder reactivarse.
- **FR-006**: Una comunidad nueva MUST crearse activa y en el estado explícito y visible «sin responsable», y MUST admitir hermanos en ese estado. El administrador MUST designar su responsable, que es uno de los hermanos de esa misma comunidad; solo él lo designa y lo cambia. Una vez designado, la comunidad MUST NOT quedarse sin responsable.
- **FR-007**: Una vez designado el responsable, el sistema MUST impedir borrar o mover a otra comunidad a ese hermano sin designar antes otro, y el cambio de responsable MUST hacerse sin que la comunidad quede sin él en ningún instante.
- **FR-008**: El sistema MUST avisar, sin bloquear, cuando el responsable de una comunidad no tenga rol de moderador; la regla de que una comunidad no se quede sin moderador sigue siendo de Identidad y acceso (FR-018).
- **FR-009**: Cada hermano MUST pertenecer a una única comunidad y MUST tener nombre; los apellidos y los datos de contacto son opcionales. El orden del directorio MUST ser por apellidos y después por nombre, y los que no tengan apellidos MUST ordenarse por nombre.
- **FR-010**: Los datos de contacto MUST limitarse a móvil, fijo y correo, sin dirección ni sexo. Un móvil o fijo MUST ser un número nacional de 9 dígitos que empieza por 6, 7, 8 o 9, o internacional con + inicial y hasta 15 dígitos; un correo MUST tener sintaxis de dirección válida. Los datos consentidos MUST ser visibles para los hermanos de esa comunidad, sus moderadores y los administradores, y no para nadie más.
- **FR-011**: El sistema MUST guardar un dato de contacto solo si el hermano ha consentido ese campo; sin consentimiento no se guarda nada. Ocultar un dato MUST equivaler a retirar el consentimiento y MUST borrar el valor de ese campo.
- **FR-012**: El registrado MUST poder consultar el directorio de su propia comunidad (nombre, apellidos y contactos consentidos) y MUST NOT ver el de otra comunidad; MUST poder buscar por nombre y llamar o escribir con un toque.
- **FR-013**: El moderador MUST ver el directorio y las pantallas de gestión de su comunidad, y el administrador las de la comunidad sobre la que opera, con los mismos datos que un registrado (los datos son solo los consentidos); un visitante MUST NOT ver ningún dato.
- **FR-014**: El registrado MUST poder editar solo sus propios datos de contacto y su consentimiento, y MUST NOT poder cambiar su nombre, comunidad, rol ni identificador.
- **FR-015**: El moderador MUST poder dar de alta, editar y borrar hermanos solo de su comunidad, incluidos los que no usan la app, y corregir los datos de contacto de cualquier hermano de su comunidad. Editar un hermano edita solo datos del hermano, nunca los del usuario de Identidad y acceso. El borrado se rige por FR-018. MUST NOT poder cambiar el responsable ni mover hermanos entre comunidades, ni gestionar el consentimiento de un hermano vinculado a un usuario.
- **FR-016**: El administrador MUST poder dar de alta, editar, mover entre comunidades y borrar hermanos de cualquier comunidad, eligiendo antes sobre cuál opera. Mover a un hermano vinculado MUST mover también a su usuario, y MUST bloquearse si dejaría sin moderador a la comunidad de origen (FR-018 de Identidad y acceso), si el hermano es el responsable (FR-007) o si la comunidad de destino está desactivada (FR-005).
- **FR-017**: El borrado de un hermano MUST pedir confirmación, ser definitivo y eliminar de inmediato sus datos personales del directorio y del sistema, junto con su registro de consentimiento.
- **FR-018**: El moderador MUST poder borrar hermanos de su comunidad que no tengan usuario o que estén vinculados a un usuario registrado; en ese caso el usuario se borra también conforme a Identidad y acceso (FR-010). El moderador MUST NOT poder borrar a su propio hermano ni a uno vinculado a un moderador o administrador: eso lo hace el administrador, siempre con las protecciones de Identidad y acceso (FR-018: sin borrado propio, sin dejar la comunidad sin moderador, sin borrar al último administrador). Al borrar, el historial en otras features MUST conservarse anonimizado (FR-023 de Identidad y acceso).
- **FR-019**: Un hermano MUST poder retirar su consentimiento en cualquier momento (él mismo, o el moderador en su nombre si no tiene usuario); el sistema MUST borrar el valor afectado y dejar de mostrarlo.
- **FR-020**: Un hermano MUST poder pedir una copia de todos sus datos guardados, incluido su registro de consentimiento, y su rectificación; la ejecuta el moderador de su comunidad o el administrador, y el dato rectificado sustituye al anterior.
- **FR-021**: Los datos personales MUST NOT aparecer en los registros técnicos ni en mensajes de error, y la lista de hermanos MUST ser accesible solo a quien tenga permiso (FR-006 de Identidad y acceso).
- **FR-022**: El sistema MUST informar al hermano de qué datos se guardan, para qué y quién los ve, y registrar su consentimiento antes de guardar un dato de contacto. Para un hermano sin usuario, el moderador MUST confirmar que le ha dado ese aviso, de forma verbal o en papel, y la confirmación forma parte del registro de consentimiento. Base legal como en Identidad y acceso (FR-028): art. 9.2.d RGPD y art. 6.1.f, y consentimiento para la comunicación de contactos a otros hermanos.
- **FR-023**: Un mismo hermano MUST poder existir sin usuario de la app y MUST poder vincularse después a uno, sin duplicar sus datos de acceso, que siguen siendo de Identidad y acceso. Hermano y usuario son dos registros distintos.
- **FR-024**: Las pantallas de comunidades, directorio y edición de contacto MUST cumplir la accesibilidad y el uso móvil del proyecto (texto legible, objetivos táctiles amplios, contraste AA, etiquetas en todos los controles) y permitir llamar o escribir con un toque.
- **FR-025**: El sistema MUST seguir ofreciendo a Identidad y acceso la lista de solo lectura de comunidades (clave interna y número), que desde esta feature pasa de datos iniciales a datos reales.
- **FR-026**: Para un hermano sin usuario, el moderador (o el administrador) MUST poder registrar su consentimiento por campo en su nombre, tras recibirlo de forma verbal o escrita, y el sistema MUST guardar de cada registro la fecha, quién lo anotó, los campos y que se recibió en su nombre. Sin ese registro, el dato MUST NOT guardarse ni mostrarse. Cuando el hermano tenga un usuario vinculado, solo él MUST poder revisar y cambiar su consentimiento, y su decisión prevalece. (Complementa FR-011 y FR-022.)
- **FR-027**: El registro de consentimiento MUST conservarse mientras exista el hermano, incluidas las retiradas, con fecha, quién, campos, origen y, para hermanos sin usuario, la confirmación del aviso; nunca MUST guardar los valores de los datos. MUST borrarse junto con el hermano al suprimirlo.
- **FR-028**: El administrador, o un moderador dentro de su comunidad y solo para usuarios con rol registrado, MUST poder vincular un hermano con un usuario; ambos MUST ser de la misma comunidad. El vínculo MUST ser uno a uno. El administrador MUST poder desvincular cualquier vínculo; el moderador solo los de su comunidad con usuarios registrados; el usuario vinculado MUST NOT poder desvincularse él mismo. Al borrarse un usuario en Identidad y acceso, el hermano MUST permanecer, desvinculado.
- **FR-029**: El nombre del hermano (nombre y apellidos) y el nombre del usuario de Identidad y acceso MUST ser independientes: vincular no los comparte ni sincroniza, editar el hermano nunca cambia al usuario, y el directorio muestra siempre el nombre del hermano. Al vincular, la pantalla MAY sugerir el nombre del usuario, pero MUST NOT copiarse nada de forma implícita. Desvincular o borrar al usuario deja intacto el nombre del hermano.
- **FR-030**: Mientras un usuario esté vinculado a un hermano, el cambio de su comunidad desde Identidad y acceso MUST bloquearse con un mensaje que indique al administrador mover al hermano (FR-016).

### Key Entities *(include if feature involves data)*

- **Comunidad**: grupo de hermanos identificado por un número único; atributos: número, nombre o referencia de parroquia opcional, responsable. Estados: activa sin responsable (al crearse), activa con responsable y desactivada. Extiende la referencia que Identidad y acceso ya consume (clave interna y número).
- **Hermano**: persona que pertenece a una comunidad; atributos: nombre, apellidos opcionales, comunidad y los datos de contacto consentidos (móvil, fijo, correo). Puede o no estar vinculado a un usuario de la app; ambos son registros distintos y sus nombres son independientes.
- **Vínculo hermano-usuario**: relación uno a uno entre un hermano y un usuario de la misma comunidad; la crea un administrador o un moderador (solo con usuarios registrados) y puede deshacerla el administrador o, dentro de su comunidad y con usuarios registrados, el moderador.
- **Responsable**: hermano designado de una comunidad; una vez designado, una comunidad tiene siempre exactamente uno.
- **Registro de consentimiento**: constancia, por campo, de que el hermano consiente o retira; guarda fecha, quién lo registró (el propio hermano, o el moderador o administrador en su nombre), origen (en la app, verbal o escrito), campos y, si el hermano no tiene usuario, la confirmación de que se le dio el aviso de datos. No guarda valores; permanece mientras exista el hermano y se borra con él.
- **Solicitud de supresión, rectificación o copia**: petición de un hermano de borrar sus datos, corregirlos o recibir una copia, ejecutada por el moderador o el administrador.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador da de alta una comunidad y designa su responsable en menos de 3 minutos.
- **SC-002**: Un moderador da de alta a un hermano con sus datos de contacto en menos de 2 minutos.
- **SC-003**: Un registrado encuentra el teléfono de un hermano de su comunidad en menos de 30 segundos desde el móvil.
- **SC-004**: El 90 % de los registrados actualiza su propio contacto a la primera, sin ayuda.
- **SC-005**: 0 casos en que un registrado o un moderador ve o modifica hermanos de otra comunidad en las pruebas de aislamiento.
- **SC-006**: 0 comunidades que, una vez designado su responsable, queden sin él en cualquier secuencia de altas, bajas, movimientos y cambios probada.
- **SC-007**: 0 datos de contacto guardados o mostrados sin consentimiento registrado en las pruebas.
- **SC-008**: Tras borrar a un hermano, 0 datos personales suyos, ni registro de consentimiento, recuperables y su historial en otras features queda anonimizado.
- **SC-009**: Ningún dato personal aparece en los registros técnicos, comprobado en las pruebas.

## Assumptions

- Depende de `002-identity-access`: usuarios, roles, permisos por comunidad, aviso de privacidad, anonimización del historial y lista de comunidades de solo lectura. Esta feature no los redefine; los amplía con la gestión real de comunidades y los datos de contacto que 002 dejó fuera (FR-016, FR-024), y añade a 002 el bloqueo del cambio de comunidad de un usuario vinculado (FR-030).
- Los roles y la herencia son los de `dominio.md` (no identificado < registrado < moderador < administrador); el moderador solo opera sobre su comunidad.
- Una comunidad tiene hasta unos 80 hermanos y la parroquia unas 25 comunidades (unos 1200 hermanos en total).
- Se guarda lo mínimo: nombre, apellidos opcionales y contacto opcional (móvil, fijo, correo); dirección y sexo, que aparecen en el glosario del dominio, no se guardan (decisión de la sesión 2026-11-25).
- Los hermanos tienen entre 14 y 90 años; el consentimiento de tutores de menores se gestiona fuera de la app.
- La comunicación de datos de contacto se limita a los hermanos de la misma comunidad, a sus moderadores y a los administradores, y nunca sale de la entidad sin consentimiento.
- Fuera de alcance: roles dentro de la comunidad distintos del responsable (salmistas, acólita), importación masiva desde hojas de cálculo, notificaciones por correo o push y agrupación de hermanos en grupos (otra feature).
- Textos de la interfaz en castellano.
