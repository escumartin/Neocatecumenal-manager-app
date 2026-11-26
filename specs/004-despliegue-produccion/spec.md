# Feature Specification: Despliegue a producción

**Feature Branch**: `004-despliegue-produccion`

**Created**: 2026-11-26

**Status**: Draft

**Input**: User description: "despliegue a producción: imagen Docker del backend con perfil prod configurado por variables de entorno, PostgreSQL gestionado con backups, proveedor con HTTPS y despliegue continuo desde master vía GitHub Actions, creación del primer administrador sin datos de prueba, builds móviles con EAS (TestFlight/App Store y Play Store) y EAS Update, y web estática"

## Clarifications

### Session 2026-11-26

- Q: ¿Dónde se alojan el servicio y la base de datos? → A: en un servidor propio en casa del mantenedor (mini PC), compartido con otros servicios domésticos; nada de proveedor en la nube para el servicio.
- Q: ¿Cómo se distribuyen las apps? → A: en las tiendas públicas (App Store y Play Store) desde el principio.
- Q: ¿Dónde se guardan las copias? → A: solo en una partición aparte del propio servidor; sin copia fuera de casa. El mantenedor asume perder los datos si se pierde o se rompe el equipo entero.
- Q: ¿Qué dirección pública se usa? → A: sin dominio propio; un subdominio gratuito de un túnel de salida desde el servidor de casa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Servicio en producción accesible por HTTPS (Priority: P1)

El mantenedor publica el servicio en una dirección pública y segura. Los hermanos y moderadores de la parroquia entran desde la app o el navegador con su identificador y contraseña y usan la app con datos reales, sin depender del ordenador del mantenedor.

**Why this priority**: sin un servicio público no hay producción; las apps móviles y la web dependen de él.

**Independent Test**: desde un móvil con datos (fuera de la red de casa) se abre la dirección pública, se comprueba el candado de conexión segura y se inicia sesión con el administrador inicial.

**Acceptance Scenarios**:

1. **Given** el servicio desplegado, **When** alguien accede por la dirección pública, **Then** la conexión es cifrada y una petición sin cifrar se redirige o se rechaza.
2. **Given** el servicio desplegado, **When** se consulta su estado de salud, **Then** responde que está disponible sin revelar detalles internos.
3. **Given** la configuración de producción, **When** falta un valor obligatorio (base de datos, orígenes permitidos), **Then** el servicio no arranca y lo dice en su registro sin mostrar secretos.
4. **Given** el servicio en producción, **When** se revisa la base de datos tras la puesta en marcha, **Then** solo contiene el administrador inicial y su comunidad (US2); ni salones, ni otros usuarios, ni datos de prueba.

---

### User Story 2 - Primer administrador sin datos de prueba (Priority: P1)

El mantenedor crea una única vez la cuenta del primer administrador de la parroquia en producción, junto con la comunidad real a la que pertenece. El sistema genera una contraseña fuerte, la muestra una sola vez al mantenedor y obliga a cambiarla en el primer acceso. El arranque de administrador actual se modifica para que aborte si ya hay uno. A partir de ahí, ese administrador da de alta comunidades, salones y usuarios desde la app.

**Why this priority**: sin una primera cuenta nadie puede entrar; y no puede existir una cuenta conocida como «admin/admin».

**Independent Test**: se ejecuta la creación del administrador contra producción, se inicia sesión con él y se comprueba que una segunda ejecución no crea otro.

**Acceptance Scenarios**:

1. **Given** una base de datos sin administradores, **When** el mantenedor ejecuta la creación indicando la comunidad del administrador, **Then** se crean esa comunidad y el administrador con una contraseña generada que solo se muestra en ese momento y debe cambiarse al primer acceso.
2. **Given** que ya existe un administrador, **When** se vuelve a ejecutar la creación, **Then** se aborta sin crear ni modificar cuentas ni comunidades.
3. **Given** la creación del administrador, **When** se revisan los registros, **Then** no aparece la contraseña.

---

### User Story 3 - Despliegue continuo desde la rama principal (Priority: P2)

Cada vez que se mergea una PR a la rama principal con los checks en verde, el servicio se actualiza solo en producción, aplicando los cambios de esquema de la base de datos, sin pasos manuales.

**Why this priority**: evita despliegues a mano propensos a error; pero la primera puesta en marcha puede hacerse sin él.

**Independent Test**: se mergea un cambio menor y, en minutos, la versión publicada refleja ese commit.

**Acceptance Scenarios**:

1. **Given** un merge a la rama principal, **When** sus checks pasan, **Then** se construye y publica la nueva versión del servicio.
2. **Given** un merge cuyos checks fallan, **When** termina la integración continua, **Then** no se despliega nada.
3. **Given** una nueva versión con migraciones de esquema, **When** arranca, **Then** aplica las migraciones antes de atender peticiones y, si una falla, la versión anterior sigue atendiendo.
4. **Given** un despliegue, **When** se consulta la versión publicada, **Then** se puede identificar el commit desplegado.

---

### User Story 4 - Copias de seguridad y restauración (Priority: P2)

Los datos de la parroquia (incluidos datos de contacto con consentimiento) se copian a diario y se pueden restaurar si hay un fallo o un borrado accidental.

**Why this priority**: la pérdida de datos personales es también un incidente RGPD; pero no bloquea el primer uso.

**Independent Test**: se restaura la copia del día anterior en una base de datos aparte y se comprueba que contiene los datos esperados.

**Acceptance Scenarios**:

1. **Given** la base de datos de producción, **When** pasa un día, **Then** existe una copia de seguridad de ese día.
2. **Given** una copia, **When** el mantenedor sigue el procedimiento documentado, **Then** la restaura en una base de datos aparte sin tocar la de producción.
3. **Given** las copias, **When** pasan más días que el periodo de retención, **Then** las más antiguas se eliminan.

---

### User Story 5 - App móvil instalable en iPhone y Android (Priority: P2)

Los hermanos instalan la app en su iPhone o Android desde App Store o Play Store y la app trabaja contra el servicio de producción.

**Why this priority**: la mayoría de usuarios usará el móvil, pero la web ya permite empezar.

**Independent Test**: se instala la build en un iPhone y en un Android, se inicia sesión y se crea una reserva que luego se ve desde la web.

**Acceptance Scenarios**:

1. **Given** una build de producción, **When** se instala, **Then** se conecta solo al servicio de producción por conexión cifrada.
2. **Given** la app publicada, **When** se corrige una pantalla sin cambiar código nativo, **Then** la corrección llega a los móviles instalados sin una nueva revisión de las tiendas.
3. **Given** un cambio que requiere código nativo, **When** se publica, **Then** se genera una build nueva con número de versión mayor.
4. **Given** una app instalada por debajo de la versión mínima que declara el servicio, **When** se abre, **Then** pide actualizar desde la tienda en lugar de fallar.

---

### User Story 6 - Web pública (Priority: P3)

La app se puede usar desde un navegador en una dirección pública, para quien no quiera o no pueda instalarla.

**Why this priority**: complemento a las apps móviles; muy barata de servir.

**Independent Test**: desde un ordenador se abre la web, se inicia sesión y se navega por todas las pestañas.

**Acceptance Scenarios**:

1. **Given** la web publicada, **When** se recarga una ruta interna, **Then** se muestra la pantalla correcta y no un error.
2. **Given** la web publicada, **When** hace peticiones al servicio, **Then** el servicio solo acepta su origen y los de las apps.

### Edge Cases

- El servicio arranca antes de que la base de datos esté disponible: reintenta y no queda a medias.
- Una migración falla en producción: la versión anterior sigue atendiendo y el despliegue se marca como fallido.
- Se filtra un secreto en un registro o en el repositorio: el escaneo lo bloquea y existe un procedimiento para rotarlo.
- Un usuario tiene una versión antigua de la app tras un cambio de contrato incompatible: la app le pide actualizar en lugar de fallar en silencio.
- Falla el disco principal pero no la partición de copias: el mantenedor restaura desde ella. Si se pierde el equipo entero, los datos se pierden (riesgo aceptado).
- Se ejecuta la creación del administrador sin indicar su comunidad: se aborta sin crear nada.
- El servidor de casa está apagado o sin conexión cuando se mergea: la versión nueva se aplica sola al volver la conexión, sin repetir el merge.
- La copia diaria falla o la partición de copias se llena: el mantenedor recibe un aviso ese mismo día.
- Una tienda rechaza la app por ser de uso restringido a una organización: se publica como app no listada (accesible por enlace) sin cambiar el producto.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El servicio MUST empaquetarse como un artefacto reproducible a partir de un commit de la rama principal, sin secretos dentro.
- **FR-002**: La configuración de producción MUST leerse del entorno (base de datos, orígenes permitidos, versión del aviso de privacidad) y MUST fallar al arrancar si falta un valor obligatorio.
- **FR-003**: Producción MUST NOT activar el perfil local ni los datos de prueba, ni tener cuentas o contraseñas definidas en código o configuración.
- **FR-004**: El tráfico público MUST ir cifrado; las cabeceras del túnel MUST tratarse de forma que la dirección del cliente y el esquema se resuelvan bien sin permitir suplantación.
- **FR-005**: El servicio MUST exponer un estado de salud sin detalles internos.
- **FR-006**: El esquema de la base de datos MUST migrarse automáticamente al arrancar cada versión.
- **FR-007**: El primer administrador y su comunidad MUST crearse con un procedimiento explícito de un solo uso que genere una contraseña fuerte de cambio obligatorio, la muestre solo al mantenedor, no la registre y aborte sin cambios si ya existe un administrador. En producción solo se crea la comunidad del administrador, sin comunidades adicionales.
- **FR-008**: La integración continua MUST desplegar en producción solo tras un merge a la rama principal con todos sus checks en verde.
- **FR-009**: La base de datos MUST tener copia de seguridad diaria con retención de 30 días, guardada en una partición distinta de la de los datos del servicio, y un procedimiento de restauración documentado y probado una vez. Los datos suprimidos desaparecen de las copias al caducar estas, y el aviso de privacidad lo indica. El procedimiento de restauración incluye volver a aplicar las supresiones solicitadas después de la fecha de la copia restaurada.
- **FR-010**: Los datos personales MUST alojarse en la Unión Europea (servicio, base de datos y copias).
- **FR-011**: Los secretos MUST vivir solo en el servidor (fuera del repositorio) y en el gestor de secretos de la integración continua; nunca en el repositorio ni en los registros. La clave de cifrado de las copias y la de recuperación del disco MUST custodiarse además fuera del servidor (gestor de contraseñas del mantenedor), para poder abrirlas si falla el TPM o se cambia la placa.
- **FR-019**: El disco del servidor MUST estar cifrado en reposo, porque pertenecer a una comunidad puede revelar creencias religiosas (art. 9 RGPD).
- **FR-020**: El fallo de una copia diaria MUST notificarse al mantenedor el mismo día.
- **FR-021**: El servicio MUST declarar la versión mínima de app soportada, y la app MUST pedir actualizar cuando esté por debajo.
- **FR-012**: La app móvil y la web MUST apuntar al servicio de producción por conexión cifrada en sus builds de producción.
- **FR-013**: Las correcciones que no tocan código nativo MUST poder publicarse a los móviles sin pasar por la revisión de las tiendas, en un canal separado del de desarrollo.
- **FR-014**: La web MUST publicarse como contenido estático en una dirección pública y el servicio MUST aceptar solo los orígenes configurados.
- **FR-015**: Debe existir un documento de operación con: puesta en marcha desde cero, creación del administrador, despliegue, restauración de copia, rotación de secretos y publicación de builds móviles.
- **FR-016**: Las apps se publican en App Store y Play Store, con ficha y política de privacidad pública. Para la revisión de las tiendas, el administrador crea desde la app una cuenta de revisor en una comunidad propia sin datos reales, con datos ficticios; se desactiva al terminar la revisión.
- **FR-017**: El servicio y la base de datos se alojan en un servidor doméstico del mantenedor, aislados de los demás servicios de esa máquina (red de contenedores propia, volúmenes propios, base de datos sin puerto publicado en el equipo y solo el túnel llega al servicio), sin abrir puertos de entrada en el router de casa.
- **FR-018**: La dirección pública es un subdominio gratuito de un servicio de túnel de salida (sin dominio propio); el servicio y la web se publican bajo esa dirección y la política de privacidad tiene una URL pública estable para las fichas de las tiendas.

### Key Entities

- **Entorno de producción**: servicio, base de datos y web públicos; su configuración y secretos viven fuera del repositorio.
- **Versión desplegada**: identificada por el commit de la rama principal del que procede.
- **Copia de seguridad**: instantánea diaria de la base de datos con fecha y caducidad.
- **Build móvil**: versión instalable para iOS o Android con número de versión y canal de actualizaciones.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde que un hermano abre la app instalada o la web en su móvil con datos hasta que ve la agenda tras iniciar sesión pasa menos de 1 minuto.
- **SC-002**: Desde el merge a la rama principal hasta tener la versión publicada pasan menos de 15 minutos sin pasos manuales.
- **SC-003**: Restaurar la copia del día anterior en una base de datos aparte lleva menos de 30 minutos siguiendo el documento.
- **SC-004**: La base de datos de producción tiene 0 registros de prueba tras la puesta en marcha.
- **SC-005**: 0 secretos en el repositorio y en los registros, verificado por escaneo.
- **SC-006**: Una corrección de pantalla llega a los móviles instalados en menos de 1 hora tras publicarla.
- **SC-007**: El coste recurrente de alojamiento y copias no supera 5 € al mes, sin contar el equipo, la luz ni las cuotas de las tiendas.

## Assumptions

- El volumen es el de una parroquia: decenas a pocos cientos de usuarios; no hace falta escalado horizontal ni alta disponibilidad multizona.
- El mantenedor crea las cuentas de terceros (túnel, aviso de fallos, Apple Developer, Google Play Console, Expo) y mete los secretos; el asistente nunca los ve.
- Se reutiliza el arranque de administrador de Identidad y acceso, modificado para abortar si ya existe un administrador; ya genera la contraseña y crea la comunidad indicada.
- Los responsables de tratamiento y el texto legal definitivo los aporta la parroquia; quedan fuera de esta feature.
- Monitorización avanzada, alertas y entorno de preproducción quedan fuera, salvo el aviso de fallo de copia de FR-020; basta el estado de salud y los registros del servidor.
- Los datos de prueba locales no se migran a producción.
- El servidor doméstico aún no existe: el despliegue se prepara para cualquier máquina con contenedores y se valida en local; la puesta en marcha real se hace cuando llegue el equipo.
- Play Store exige a las cuentas personales nuevas una prueba cerrada (al menos 12 testers durante 14 días) antes de publicar; ese plazo es del mantenedor y no bloquea esta feature.
- Sin copia fuera de casa, un robo, incendio o avería total del equipo supone perder los datos; el mantenedor lo asume por decisión propia.
- La disponibilidad depende de la luz y la conexión de casa; se acepta para el volumen de una parroquia.
