# Research: Identidad y acceso

**Feature**: `002-identity-access` | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Cada decisión sigue el formato Contexto, Opciones, Decisión, Consecuencias y Constitution Check. Ninguna queda como
NEEDS CLARIFICATION.

## R1. Módulo y forma del contexto

- **Contexto**: es el primer bounded context real del backend. `001-reservas-salones` lo consume para conocer al actor
  (`CurrentActorProvider`) y la lista de comunidades.
- **Opciones**:
  1. Módulo Modulith `identity` con API pública en su raíz. Pros: encaja con `hexagonal-ddd` y con el principio III.
     Contras: hay que fijar con cuidado qué tipos se exportan.
  2. Identidad repartida como infraestructura transversal (`shared/security`). Pros: menos ceremonia. Contras: la
     gestión de usuarios y sus invariantes (último administrador, último moderador) es negocio, no infraestructura.
- **Decisión**: opción 1, paquete `io.github.escumartin.neocatecumenal.identity`.
- **Consecuencias**: el filtro de seguridad, global por naturaleza, vive en la infraestructura de `identity` y protege
  todas las rutas; la autorización fina sigue en cada módulo (001 R6).
- **Constitution Check**: III PASS.

## R2. Mecanismo de sesión (FR-001, FR-008, FR-017, FR-022, FR-026, FR-027)

- **Contexto**: un mismo backend sirve a Expo web y a Expo nativo. Los cambios de rol y el borrado deben notarse en la
  siguiente petición, hay que poder cerrar todas las sesiones de un usuario salvo la actual, y la caducidad es
  deslizante (30 días sin uso) con tope absoluto (90 días).
- **Opciones**:
  1. **JWT sin estado** (access corto + refresh). Pros: sin consulta por petición. Contras: rol y borrado no se ven hasta
     que caduca el token (incumple FR-008 y FR-017 salvo consultando la base en cada petición, con lo que se pierde la
     ventaja); revocar todas las sesiones exige una lista negra; más superficie (firma, rotación de claves).
  2. **Spring Session JDBC con cookie `SESSION`**. Pros: deslizamiento nativo y búsqueda por principal. Contras: atado a
     `HttpSession` y a cookies, incómodo en React Native; el tope de 90 días y el cierre selectivo («todas menos la
     actual») requieren código propio igualmente; guarda atributos serializados que no necesitamos.
  3. **Token opaco propio guardado en PostgreSQL** (solo su hash), resuelto en cada petición con una consulta que une
     sesión y usuario. Pros: rol y comunidad salen de la fila del usuario en cada petición (FR-008); borrar el usuario
     borra sus sesiones en cascada (FR-017); cerrar sesiones es un `DELETE` (FR-022, FR-027); la caducidad es una
     comparación de dos fechas (FR-026). Contras: una consulta indexada por petición y una escritura para renovar.
- **Decisión**: opción 3.
  - Token de 256 bits de `SecureRandom`, codificado en base64url; en la tabla solo se guarda su SHA-256, de modo que un
    volcado de la base no da sesiones utilizables.
  - Validez: `now < last_used_at + 30 días` y `now < created_at + 90 días`.
  - Renovación: se actualiza `last_used_at` en cada uso, salvo si se actualizó hace menos de 5 minutos, para no escribir
    en cada petición. El error máximo sobre la ventana de 30 días es de 5 minutos, irrelevante para FR-026.
  - Transporte: el mismo token viaja como `Authorization: Bearer` en nativo (guardado en `expo-secure-store`) y como
    cookie `HttpOnly`, `Secure`, `SameSite=Strict` en web. El login indica el transporte (`tokenDelivery: BODY |
    COOKIE`); con `COOKIE` el token no aparece en el cuerpo. En web no se guarda en `localStorage` (riesgo XSS,
    `conventions-security`).
  - CSRF con cookie: `SameSite=Strict` más comprobación de `Origin` contra una lista blanca configurada en las peticiones
    que cambian estado. Bearer no lo necesita.
  - Purga programada de sesiones caducadas.
- **Consecuencias**: el cliente web y la API deben estar en el mismo *site* (mismo dominio registrable) para que la
  cookie `SameSite=Strict` viaje; queda como restricción de despliegue. Con unos 1200 usuarios, la consulta por petición
  es despreciable.
- **Constitution Check**: VIII PASS (no se guarda el token en claro); II PASS (el token y su hash se generan tras un
  puerto de salida).

## R3. Spring Security como marco del filtro

- **Contexto**: hace falta un filtro que autentique, cabeceras de seguridad, CORS y la regla «todo protegido salvo lo
  público».
- **Opciones**:
  1. **Spring Security** con un filtro propio que resuelve el token opaco. Pros: cabeceras, CORS, manejo de 401/403 y
     `SecurityContext` probados; `conventions-security` lo pide. Contras: dependencia nueva y algo de configuración.
  2. Filtro de servlet e interceptor propios. Pros: menos dependencias. Contras: reimplementa cabeceras, CORS y errores;
     más riesgo.
- **Decisión**: opción 1 (`spring-boot-starter-security`, `spring-security-test` en test). Regla por defecto
  `authenticated()`; `permitAll` fijo en la configuración de seguridad solo para `POST /api/sessions` y
  `GET /api/health`.
- **Alternativa descartada**: un bean `PublicEndpoints` exportado por `identity` para que otros módulos declaren rutas
  públicas. Hoy ningún módulo las tiene; sería API pública sin consumidor (simplicidad). Cuando exista Noticias, sus
  rutas públicas se añadirán a la misma configuración, o se introducirá entonces esa extensión.
- **Consecuencias**: el dominio no conoce Spring Security; el principal autenticado se traduce a `CurrentUser` en la
  infraestructura. Abrir una ruta pública exige tocar la configuración de `identity`, cambio visible en la revisión.
- **Constitution Check**: I PASS, II PASS, III PASS (la API del módulo no crece sin consumidor).

## R4. Primer acceso: aviso de datos y cambio de contraseña (FR-014, FR-020)

- **Contexto**: un usuario con la contraseña inicial o sin aceptar el aviso no puede usar acciones protegidas, pero sí
  necesita una sesión para aceptar y cambiar la contraseña.
- **Opciones**:
  1. Sesión **restringida**: el login crea la sesión; mientras haya pasos pendientes, solo se admiten `GET /api/me`, la
     aceptación o el rechazo del aviso, el cambio de contraseña y el cierre de sesión; el resto responde `403` con
     `PRIVACY_NOTICE_PENDING` o `PASSWORD_CHANGE_REQUIRED`. Pros: un solo mecanismo; el backend decide. Contras: una
     regla más en el filtro.
  2. Login en dos fases con un token temporal distinto. Pros: separa los estados. Contras: dos tipos de token y más
     endpoints.
- **Decisión**: opción 1. Orden: primero el aviso (informar antes de tratar), después la contraseña. `GET /api/me`
  devuelve `pendingSteps`. Rechazar el aviso borra la sesión actual. `IdentityApi.currentUser()` devuelve vacío para
  una sesión restringida, como defensa en profundidad.
- **Consecuencias**: se guarda `privacy_notice_version` y `privacy_notice_accepted_at`; si cambia la versión del aviso,
  vuelve a pedirse. El texto del aviso vive en los recursos del cliente; el backend solo conoce la versión vigente
  (configuración).
- **Constitution Check**: VIII PASS (consentimiento registrado: quién, cuándo, qué versión).

## R5. Almacenamiento de contraseñas y política (FR-015)

- **Contexto**: contraseñas de 8 a 64 caracteres sin reglas de composición, ilegibles para todos, y rechazo de
  comunes y de las que contienen el identificador.
- **Opciones de hash**:
  1. **BCrypt**. Pros: incluido en Spring Security. Contras: límite de 72 bytes; 64 caracteres con tildes o emojis
     pueden pasar de 72 bytes en UTF-8, y Spring Security rechaza o trunca.
  2. **PBKDF2-HMAC-SHA256** (600 000 iteraciones). Pros: sin dependencias. Contras: no es *memory-hard*.
  3. **Argon2id** (`Argon2PasswordEncoder`, requiere `org.bouncycastle:bcprov-jdk18on`). Pros: recomendación actual de
     OWASP, sin límite práctico de longitud. Contras: una dependencia más y unos 50 ms por verificación.
- **Decisión**: Argon2id con m = 19 MiB, t = 2, p = 1, sal de 16 bytes y hash de 32, dentro de un
  `DelegatingPasswordEncoder` (prefijo `{argon2}`) para poder migrar parámetros. La contraseña se normaliza a NFC antes
  de medir y de hashear; la longitud se mide en *code points*.
- **Tiempo constante (FR-002)**: con un identificador inexistente se verifica contra un hash ficticio precalculado, para
  que el tiempo de respuesta no revele qué identificadores existen.
- **Opciones de lista de comunes**:
  1. **Lista local** en recursos: las 10 000 más comunes de SecLists (licencia MIT, revisada y copiada a mano, regla 9)
     filtradas a ≥ 8 caracteres, más una lista curada en castellano (equipos, nombres frecuentes, términos religiosos
     habituales, secuencias de teclado). Pros: sin red ni terceros en tiempo de ejecución. Contras: hay que mantenerla.
  2. **API k-anonymity de Have I Been Pwned**. Pros: cobertura enorme. Contras: dependencia de red en el alta y en el
     cambio de contraseña; servicio externo.
- **Decisión**: lista local; comparación sin distinguir mayúsculas tras NFC. La política (`PasswordPolicy`) es de
  dominio y recibe la lista como interfaz; la carga del fichero es un adaptador.
- **Consecuencias**: el aviso de licencia de SecLists va en un fichero `THIRD-PARTY-NOTICES` de recursos.
- **Constitution Check**: VIII PASS; IX PASS (los motivos de rechazo son códigos que el cliente traduce).

## R6. Contraseña inicial (FR-014, FR-022, US3, US4)

- **Contexto**: el hermano recibe una contraseña inicial de quien le da de alta o se la reasigna. La spec no dice
  quién la elige.
- **Opciones**:
  1. **La genera el sistema** y se muestra una sola vez en la respuesta del alta o de la reasignación. Pros: nunca es
     débil ni reutilizada; el alta no falla por la política (SC-005); evita la carrera «la contraseña contiene el
     identificador recién generado». Contras: quien da de alta debe transcribirla en ese momento.
  2. La escribe quien da de alta. Pros: puede dictar algo memorable. Contras: tiende a contraseñas débiles o repetidas;
     el alta puede fallar por la política.
- **Decisión**: opción 1. Diez caracteres de un alfabeto sin ambigüedades (minúsculas y dígitos sin `0`, `o`, `1`,
  `l`), unos 50 bits, que además pasa `PasswordPolicy`. No se puede recuperar después; perderla exige otra reasignación.
- **Consecuencias**: la pantalla del alta muestra el identificador y la contraseña inicial con opción de copiar. Es una
  decisión de UX revisable sin tocar el modelo.
- **Constitution Check**: VII PASS (texto grande, sin caracteres confusos), VIII PASS.

## R7. Bloqueo por fallos de acceso (FR-003, FR-028)

- **Contexto**: dos reglas (5 fallos seguidos por identificador+IP; 20 fallos por IP en 15 minutos), ambas con bloqueo
  de 15 minutos, aviso del tiempo de espera y la misma respuesta para identificadores inexistentes. La IP solo puede
  conservarse mientras sirve a la ventana. Con login concurrente, contar el fallo después de verificar deja una
  carrera: N peticiones simultáneas leen «4 fallos», verifican las N y el atacante obtiene N intentos en lugar de 5.
- **Ambigüedad de la spec resuelta**: FR-028 dice «conservada solo mientras dura su ventana de bloqueo de 15 minutos».
  Se fija así: un registro de fallo se conserva hasta 15 minutos después del último fallo o hasta que termine el
  bloqueo, lo que llegue después. Por tanto, «seguidos» significa sin un acceso correcto entre medias y sin 15 minutos
  de calma.
- **Opciones de almacenamiento**:
  1. **Memoria** (mapa con caducidad o Bucket4j). Pros: rápido y sin tablas. Contras: se pierde al reiniciar (un
     reinicio desbloquea) y no se comparte entre instancias.
  2. **PostgreSQL** con tablas propias y purga programada. Pros: consistente entre reinicios e instancias; la retención
     se puede probar con Testcontainers. Contras: escrituras en cada intento (volumen bajo).
- **Opciones de concurrencia**:
  1. Leer el contador, verificar y, si falla, incrementar. Contras: la carrera descrita; no cumple FR-003 bajo carga.
  2. `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` con la regla escrita en SQL. Pros: una sola sentencia. Contras: la
     regla de dominio (`LoginThrottle`) se duplicaría en SQL y dejaría de probarse sin base de datos.
  3. **Reserva pesimista**: asegurar la fila (`INSERT ... ON CONFLICT DO NOTHING`), bloquearla con
     `SELECT ... FOR UPDATE`, contar el intento como fallo provisional y verificar la contraseña en la misma
     transacción; si acierta, se deshace el conteo. Pros: la regla sigue en el dominio puro; como mucho 5 (par) o 20
     (IP) verificaciones por ventana, porque los intentos del mismo par o IP se serializan sobre su fila. Contras: la
     fila queda bloqueada durante la verificación (≈ 50 ms).
- **Decisión**: almacenamiento 2 y concurrencia 3.
  - `identity_login_pair_throttle` (identificador, IP, fallos seguidos, último fallo, bloqueado hasta): una fila por
    par.
  - `identity_login_ip_throttle` (IP, instantes de los fallos recientes como `timestamptz[]`, bloqueado hasta): una
    fila contadora por IP. Guardar los instantes (como mucho 20) mantiene la ventana deslizante exacta de FR-003 sin
    una fila por fallo.
  - Transacción del login, siempre en este orden para evitar interbloqueos: fila de la IP, fila del par.
    1. Asegurar y bloquear ambas filas.
    2. `LoginThrottle.reserve(ip, pair, now)` (dominio puro): si alguna está bloqueada, rechaza sin verificar ni contar
       (`429` con `Retry-After` y `retryAfterSeconds`). Si no, cuenta el intento como fallo: par `consecutive + 1`
       (vuelve a 1 si el último fallo tiene ≥ 15 min); IP, añade `now` y descarta instantes fuera de
       `(now - 15 min, now]`. Al llegar a 5 (par) o 20 (IP), fija `blockedUntil = now + 15 min`.
    3. Guardar el estado reservado y verificar la contraseña (o el hash ficticio si el identificador no existe).
    4. Acierto: se borra la fila del par y la de la IP vuelve al estado leído bajo bloqueo (sin el instante añadido ni
       el bloqueo que hubiera fijado este intento); nadie más pudo cambiarla mientras tanto. Fallo: se queda la reserva.
  - Las consultas ignoran filas vencidas aunque la purga aún no haya pasado; la purga corre cada minuto. La corrección
    no depende de la purga.
  - Cambio de contraseña propia (`PUT /api/me/password`): columna `identity_user.failed_password_changes` (puerto
    `PasswordChangeAttempts`, UPDATE atómico con `RETURNING`); el 5.º fallo seguido cierra todas las sesiones del
    usuario y responde `401`, un acierto la pone a cero. Las IPv6 se agrupan por /64 en ambas tablas.
  - El identificador debe tener formato de 6 dígitos (si no, `422`, que no revela nada). Los inexistentes cuentan igual.
- **IP de origen**: por defecto, la dirección remota de la conexión (`server.forward-headers-strategy=none`, valor
  restrictivo). Detrás de un proxy se activa `framework` con la lista de proxies de confianza explícita; si no, un
  atacante falsearía `X-Forwarded-For`.
- **Alternativa descartada**: guardar un HMAC de la IP en lugar de la IP. Reduce poco el riesgo con una retención de
  minutos y añade un secreto que gestionar.
- **Consecuencias**:
  - Los logins desde una misma IP se serializan (≈ 50 ms cada uno): 30 hermanos entrando a la vez tras el NAT de la
    parroquia esperan como mucho 1,5 s. Los aciertos no consumen el cupo de la IP. La conexión del pool queda ocupada
    durante la verificación; el pool se dimensiona con eso en cuenta.
  - La IP nunca se escribe en logs.
  - Tests de dominio para las dos reglas y los límites (4.º y 5.º fallo, 19.º y 20.º, minuto 15, acierto que deshace
    la reserva). Test Testcontainers de concurrencia: 30 logins simultáneos con contraseña errónea para el mismo par
    verifican como mucho 5 veces, y 40 simultáneos con identificadores distintos desde la misma IP, como mucho 20
    (contador de verificaciones en un `PasswordHasher` espía); 25 logins correctos desde la misma IP no la bloquean.
- **Constitution Check**: VIII PASS (retención mínima justificada por FR-028, art. 6.1.f); I PASS (la regla de reserva
  es de dominio; el bloqueo de filas es del adaptador); IV PASS (test de concurrencia antes de la implementación).

## R8. API pública del módulo (FR-004, FR-025)

- **Contexto**: otros módulos necesitan el usuario actual y la lista de comunidades sin ver dominio, aplicación ni
  infraestructura de `identity`.
- **Opciones**:
  1. Los demás módulos leen `SecurityContextHolder` y hacen *cast* al principal. Pros: nada que diseñar. Contras: acopla
     cada módulo a Spring Security y al tipo interno del principal; viola el principio III.
  2. **Interfaz `IdentityApi` en la raíz** con tipos propios (`CurrentUser`, `Role`, `CommunityRef`) y eventos
     publicados. Pros: contrato estable y verificable por `ApplicationModules.verify()`. Contras: un mapeo más.
  3. Cada petición pasa el token a `IdentityApi.resolve(token)`. Pros: explícito. Contras: el token sale del módulo de
     identidad y cada módulo tendría que extraerlo.
- **Decisión**: opción 2. Contrato detallado en [contracts/identity-module-api.md](./contracts/identity-module-api.md).
  - `IdentityApi.currentUser(): Optional<CurrentUser>`: el usuario de la petición en curso con rol y comunidad leídos de
    la base en esa petición (FR-008); vacío para visitantes o sesiones restringidas.
  - `IdentityApi.communities(): List<CommunityRef>` y `IdentityApi.community(UUID): Optional<CommunityRef>` (FR-025).
  - Evento `UserDeleted(UUID userId)` publicado en la transacción del borrado y entregado tras el commit, para que los
    contextos que guardan algo del usuario lo anonimicen o borren (FR-023).
  - `Role` con `REGISTERED`, `MODERATOR`, `ADMINISTRATOR` y `includes(Role)` para la herencia (FR-005).
  - Ids como `UUID`; cada consumidor los envuelve en sus propios value objects (como hace 001 con `Actor`).
- **Implementación**: `IdentityApi` la implementa un servicio de `application`, que obtiene la sesión en curso por el
  puerto de salida `CurrentSessionContext`, implementado en la infraestructura sobre el `SecurityContext`.
- **Consecuencias**: el `CurrentActorProvider` de `spaces` y `agenda` (001) se implementa llamando a
  `IdentityApi.currentUser()` y mapeando `Role` a su `Role` local. Solo los tipos de la raíz son visibles.
- **Nota**: el paquete `generated` (interfaces generadas desde `api/openapi.yaml`) es un módulo `OPEN` de Spring Modulith: el contrato es compartido y no es un bounded context.
- **Constitution Check**: II PASS, III PASS.

## R9. Anonimización al borrar (FR-017, FR-023)

- **Contexto**: el historial de otras features se conserva sin datos personales.
- **Opciones**:
  1. Identidad llama a cada módulo para que anonimice. Contras: dependencia inversa y acoplamiento.
  2. Evento `UserDeleted` con el registro persistente de publicaciones de Spring Modulith (`spring-modulith-starter-jpa`,
     tabla `event_publication`). Pros: no se pierde si el proceso cae tras el commit. Contras: dependencia, tabla y
     migración sin ningún consumidor hoy.
  3. **Evento `UserDeleted` sin registro persistente** (`ApplicationEventPublisher`, consumido con
     `@ApplicationModuleListener`). Pros: nada que mantener mientras no haya consumidores. Contras: si el proceso cae
     entre el commit y la entrega, el evento se pierde.
- **Decisión**: opción 3. En los módulos actuales (`spaces`, `agenda`) solo se guarda el UUID del usuario: borrada la
  fila de `identity_user`, ese UUID deja de enlazar con ningún dato personal, así que no necesitan escuchar el evento.
  Hoy no hay consumidor, de modo que perder un evento no deja datos personales huérfanos.
- **Consecuencias**: sin tabla `event_publication` ni `spring-modulith-starter-jpa`. Trabajo anotado para cuando exista
  «Comunidades y hermanos» (primer consumidor que borrará datos de contacto): añadir el registro persistente de
  publicaciones con su migración, porque entonces sí habría datos personales que no deben quedar sin borrar.
- **Constitution Check**: III PASS; VIII PASS (ningún dato personal depende hoy de la entrega del evento).

## R10. Invariantes de gestión de usuarios y concurrencia (FR-009, FR-010, FR-016, FR-018)

- **Contexto**: ni último administrador sin rol, una comunidad con moderadores no puede quedarse sin ninguno, ni autoborrado; el moderador solo opera
  sobre registrados de su comunidad.
- **Decisión**:
  - Política de dominio `UserManagementPolicy` que recibe actor y usuario objetivo y decide crear, editar nombre,
    cambiar rol o comunidad, reasignar contraseña y borrar.
  - Un moderador que actúa sobre un usuario de otra comunidad recibe siempre `403`; `404` solo si el usuario no existe.
  - «Moderador» en FR-018 cuenta solo los usuarios con rol `MODERATOR` (el responsable de la comunidad), no los
    administradores que pertenezcan a ella. Una comunidad que aún no tiene moderador (recién cargada) puede seguir sin
    él: la regla impide quitar el último, no exige tener uno.
  - Nadie cambia su propio rol ni su comunidad, ni se borra ni se reasigna su propia contraseña por esta vía (usa el
    cambio de contraseña propio).
  - Concurrencia: antes de contar moderadores se bloquea la fila de la comunidad afectada (`SELECT ... FOR UPDATE`);
    antes de contar administradores se bloquean las filas de administradores. Así, dos degradaciones simultáneas no
    dejan la comunidad sin moderador ni el sistema sin administrador. Bloqueo optimista (`version`) en `identity_user`
    para ediciones concurrentes del mismo usuario.
- **Alternativa descartada**: comprobar solo en la aplicación sin bloqueo; dos peticiones simultáneas pasarían la
  comprobación.
- **Consecuencias**: tests de concurrencia con Testcontainers (dos hilos).
- **Constitution Check**: IV PASS.

## R11. Generación del identificador de acceso (FR-012, FR-013)

- **Decisión**: número aleatorio uniforme en [100000, 999999] con `SecureRandom`, índice único en `access_id`. Si el
  `INSERT` choca con el índice, el servicio reintenta con otro número en una transacción nueva, hasta 5 veces; agotados
  los intentos, `500` sin crear nada (probabilidad despreciable con unos 1200 usuarios sobre 900 000 valores).
- **Alternativas descartadas**: secuencia (predecible y revela el número de usuarios); comprobar antes de insertar sin
  índice único (carrera entre dos altas).
- **Constitution Check**: VIII PASS.

## R12. Carga del primer administrador y de las comunidades (Assumptions)

- **Contexto**: el primer administrador se crea fuera de la app; las comunidades las mantendrá «Comunidades y
  hermanos», que aún no existe. La contraseña inicial es una credencial: no puede acabar en la salida estándar, que en
  despliegue es el flujo que recoge el sistema de logs (SC-007, principio VIII).
- **Opciones**:
  1. **Migración Flyway con datos**. Pros: automática. Contras: el administrador (y su contraseña) acabaría en el repo;
     los datos irían a todos los entornos, incluidos los de test.
  2. **Modo de arranque de un solo uso que escribe la credencial en la salida estándar**. Pros: simple. Contras: en un
     contenedor o servicio la salida estándar va al recolector de logs; la credencial quedaría almacenada fuera de
     control.
  3. **Modo de arranque de un solo uso que exige consola interactiva** (`--identity.bootstrap=true`): un
     `ApplicationRunner` que obtiene `System.console()` antes de tocar nada. Sin consola (entrada o salida no son un
     terminal, como ocurre cuando se redirigen a un recolector), aborta con código de salida distinto de 0, sin crear
     comunidades ni administrador y con un único log sin datos («carga inicial cancelada: hace falta una consola
     interactiva»). Con consola, aplica la [Carga inicial](../../.agents/context/dominio.md) (definición canónica en el
     glosario; 004 R7 quitó la lista de comunidades) con nombre y comunidad dados por argumentos; muestra identificador y contraseña inicial
     solo con `Console.writer()`, nunca por `System.out` ni por el logger, y termina sin levantar el servidor HTTP.
     Pros: nada sensible en el repo, en variables de entorno ni en logs; idempotente. Contras: un paso manual en un
     terminal del servidor.
  4. Endpoint de instalación abierto mientras no haya administrador. Contras: ventana de ataque en el primer arranque.
- **Decisión**: opción 3. Desactivado por defecto (valor restrictivo). Las comunidades se guardan en
  `identity_community`, que es la referencia de solo lectura de FR-025; cuando exista «Comunidades y hermanos», esa
  tabla pasará a ser una proyección alimentada por sus eventos. La consola llega al runner a través de un proveedor
  inyectable del adaptador `in/bootstrap`, para poder sustituirla en los tests.
- **Consecuencias**:
  - La contraseña debe cambiarse en el primer acceso. En Java 21, `System.console()` es `null` si la entrada o la
    salida no son un terminal; al subir a Java 22 o posterior hay que añadir la comprobación `Console.isTerminal()`,
    porque allí `System.console()` puede devolver una consola aunque la salida esté redirigida.
  - El despliegue ejecuta la carga con un terminal interactivo (por ejemplo, `docker run -it` o una sesión SSH), no
    como paso desatendido.
  - Tests (con datos sintéticos y la salida y los logs capturados): sin consola, el proceso aborta, no hay filas en
    `identity_community` ni en `identity_user` y ni la salida estándar ni los logs contienen identificador ni
    contraseña; con una consola falsa, la credencial solo aparece en el escritor de esa consola y no en la salida
    estándar ni en los logs; repetirlo no crea otro administrador.
- **Constitution Check**: VIII PASS (la credencial nunca pasa por la salida estándar ni por los logs, con test);
  restricción «secretos fuera del repo» PASS.

## R13. Contrato HTTP y errores

- **Decisión**: borrador en [contracts/identity.openapi.yaml](./contracts/identity.openapi.yaml), con el mismo estilo
  que 001: Problem Details (RFC 9457) con `code` estable. Esquemas de seguridad `bearerAuth` y `cookieAuth`.
  - Sesiones: `POST /api/sessions`, `DELETE /api/sessions/current`.
  - Usuario actual: `GET /api/me`, `PUT /api/me/password`, `POST /api/me/privacy-notice-acceptance`,
    `POST /api/me/privacy-notice-rejection`.
  - Gestión: `GET/POST /api/users`, `GET/PUT/DELETE /api/users/{userId}`, `POST /api/users/{userId}/password-reset`.
  - Comunidades: `GET /api/communities`.
  - Respuesta determinista: un moderador que accede a un usuario de otra comunidad recibe siempre `403`; `404` queda
    solo para usuarios que no existen.
- **Compatibilidad**: al fusionar con 001, el `enum` de `Problem.code` y de los códigos de validación es la unión de
  ambos; los esquemas comunes (`Problem`, `ValidationProblem`, respuestas `Unauthorized`, `Forbidden`) se definen una
  vez. Añadir valores no rompe a ningún cliente existente porque aún no hay clientes desplegados.
- **Alternativas descartadas**: `PATCH` parcial de usuario (el generador `spring` trata mal los campos opcionales
  nulos frente a ausentes); `/api/auth/login` como verbo (se prefiere el recurso sesión); `404` para usuarios de otra
  comunidad (ocultaría su existencia, pero los ids son UUID no adivinables y dos respuestas posibles para el mismo caso
  hacen el contrato no determinista).
- **Constitution Check**: V PASS.

## R14. Privacidad y logs (FR-019, FR-024, FR-028, SC-007)

- **Decisión**:
  - `identity_user` guarda solo identificador, nombre, rol, comunidad, hash de contraseña, marca de contraseña inicial
    pendiente y aceptación del aviso (versión y fecha). Sin datos de contacto.
  - Acceso: `GET /api/me` devuelve todo lo que el contexto guarda del titular. Rectificación del nombre: su moderador o
    un administrador. Supresión: borrado por su moderador o un administrador (el autoborrado está prohibido por FR-018).
  - Logs: solo el UUID interno del usuario, el resultado y códigos; nunca nombre, identificador de acceso, contraseña,
    token, cabecera `Authorization`, cookie ni IP. Un test captura la salida del logger durante login, alta, cambio de
    contraseña y carga inicial y comprueba que no aparecen (SC-007).
- **Constitution Check**: VIII PASS, IX PASS.

## R15. Cliente Expo

- **Decisión**:
  - `app/src/lib/auth/`: almacén de sesión (`expo-secure-store` en nativo; nada en web, la cookie la gestiona el
    navegador) y cliente `openapi-fetch` con el interceptor que añade el bearer y trata `401` (vuelta al login) y `403`
    con pasos pendientes.
  - Hooks `useSession`, `useCurrentUser`, `useUsers`, `useCommunities`; las pantallas no contienen lógica.
  - Rutas: `(auth)/login`, `(auth)/privacy-notice`, `(auth)/change-password` y `(app)/users/...`; el `_layout` de
    `(app)` redirige al login sin sesión y a los pasos pendientes si los hay (US1-4).
  - Accesibilidad: campo de contraseña con mostrar u ocultar, etiquetas, objetivos ≥ 48 px, texto ≥ 18 px, mensajes de
    error asociados al campo; diálogo de confirmación accesible para borrar. Solo tokens de `design-system`.
  - Textos en ficheros de recursos; los `code` del contrato se traducen allí.
- **Dependencias nuevas**: `expo-secure-store`, `openapi-fetch` (también la usa 001).
- **Constitution Check**: VI PASS, VII PASS, IX PASS.
