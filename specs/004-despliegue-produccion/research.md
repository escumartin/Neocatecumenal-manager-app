# Research: Despliegue a producción

**Feature**: `004-despliegue-produccion` | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Cada decisión sigue el formato Contexto, Opciones, Decisión, Consecuencias y Constitution Check. Ninguna queda como
NEEDS CLARIFICATION; las versiones no comprobadas se marcan «a verificar».

Fuentes: `spec.md` (21 FR), `.specify/memory/constitution.md` 1.0.0, `.agents/context/dominio.md`, skill
`hexagonal-ddd`, `api/openapi.yaml` 0.3.0, migraciones `V1..V6`, `backend/pom.xml`, `application{,-local,-bootstrap}.yml`,
`BootstrapIdentityService`, `IdentityBootstrapRunner`, `IdentityBootstrapConfig`, `IdentityProperties`,
`SessionCookies`, `OriginCheck`, `ClientIpResolver`, `LocalCorsConfig`, `HealthController`, `AccessDataPurgeJob`,
`.github/workflows/{ci,pr-review}.yml`, `docker-compose.yml`, `scripts/local-run.sh`, `app/app.json`,
`app/package.json`, `app/src/lib/api/{api-url,index}.ts` y `specs/003-comunidades-hermanos/plan.md` (R7: retirar el
consentimiento borra el valor).

Versiones comprobadas el 2026-11-26 contra Docker Hub, la API de GitHub y npm: `postgres:18.7`,
`eclipse-temurin:25.0.5_8-jre-noble`, `nginx:1.30.8-alpine` (rama estable), `tailscale/tailscale:v1.104.7`,
`restic/restic:0.20.0`, `actions/checkout` v7.0.1, `docker/setup-buildx-action` v4.4.1, `docker/login-action` v4.8.0,
`docker/metadata-action` v6.2.0, `docker/build-push-action` v7.4.0, `expo/expo-github-action` 9.0.2, `eas-cli`
27.11.0, `expo-updates` 58.0.22. Los digest `sha256` de imágenes y los SHA de commit de las actions se fijan en la
implementación (a verificar). `containrrr/watchtower` está archivado (comprobado: `archived: true`).

## R1. Topología: un hostname de Funnel para API y web

- **Contexto**: Funnel publica un único hostname por nodo (`<nodo>.<tailnet>.ts.net`) y solo los puertos 443, 8443 y
  10000. La web usa la cookie `__Host-session` con `Secure`, `SameSite=Strict` y `Path=/`, y `OriginCheck` exige un
  origen permitido en las peticiones que cambian estado; `resolveApiUrl` ya acepta una URL vacía en web fuera de
  desarrollo (peticiones relativas) y `LocalCorsConfig` solo existe con el perfil `local`. El diseño previo ya supone
  mismo origen en producción.
- **Opciones**:
  1. **Mismo origen por rutas, con un proxy propio**: `tailscale` (Funnel 443) → contenedor `edge` (nginx con la web
     estática dentro) que sirve `/` y reenvía `/api/` al backend. Pros: mismo origen, sin CORS en producción, la cookie
     `__Host-` funciona tal cual; nginx fija cabeceras de seguridad, el reenvío de IP y no publica `/actuator`. Contras:
     un contenedor y una imagen más.
  2. **Mismo origen por rutas en `tailscale serve`** (handler `/api` → backend, `/` → servidor estático). Pros: sin
     nginx propio. Contras: el recorte del prefijo de ruta y las cabeceras quedan en manos de Tailscale (a verificar);
     sin control de cabeceras de seguridad ni del retorno a `index.html` en rutas internas (US6-1).
  3. **Puertos distintos** (443 web, 8443 API). Pros: separación total. Contras: orígenes distintos → CORS en
     producción, cookie `SameSite=Strict` y `__Host-` siguen valiendo pero la web hace peticiones entre orígenes; el
     8443 lo bloquean algunas redes corporativas y de datos.
  4. **El backend sirve la web** (estáticos en el jar). Pros: un solo contenedor detrás del túnel. Contras: código de
     servido estático y retorno a `index.html` en Spring, el filtro de seguridad tiene que dejar pasar los estáticos y
     la imagen del backend crece con el cliente.
- **Decisión**: opción 1. Rutas: `/api/**` → backend; `/actuator/**` → 404; resto → estáticos con retorno a
  `index.html`. `identity.allowed-origins` en producción = `https://<nodo>.<tailnet>.ts.net`. El `access_log` de nginx usa un formato
  propio sin `$remote_addr`, sin `X-Forwarded-For`, sin cadena de consulta, sin cuerpo y sin `Authorization` (hora,
  método, ruta, estado, bytes y duración). Sin filtro CORS en
  producción (las apps nativas no envían `Origin` y van con token en el cuerpo, `BODY`).
- **Consecuencias**: la web se compila sin `EXPO_PUBLIC_API_URL` (relativa); el puerto 80 no existe en Funnel, así que
  el HTTP sin cifrar se rechaza (US1-1); nginx añade `Strict-Transport-Security` sin `includeSubDomains`,
  `X-Content-Type-Options`, `Referrer-Policy`, `Content-Security-Policy` con `default-src 'self'` (los ajustes de
  `script-src` para Expo, a verificar en la implementación) y caché larga solo en `/_expo/static/`. El tráfico de Funnel
  pasa por los relés de Tailscale con TLS terminado en el nodo de casa; Tailscale no ve el contenido.
- **Constitution Check**: VIII (sin datos personales en el proxy: el log de acceso no guarda IPs ni consultas; los logs
  de todos los contenedores van al driver `local` de Docker con `max-size: 10m` y `max-file: 3`, rotación real por
  tamaño; el log de acceso sustituye los UUID del path por `:id` y los logs de borrado y anonimización no llevan ids). Sin violaciones.

## R2. IP del cliente y esquema tras el túnel (FR-004)

- **Contexto**: `server.forward-headers-strategy: none` y `ClientIpResolver` usa `getRemoteAddr()`. Detrás de dos
  proxys, todas las peticiones llegarían con la IP de `edge` y `identity_login_ip_throttle` bloquearía a todos a la vez
  tras unos pocos fallos: un ataque de denegación trivial.
- **Opciones**:
  1. **`forward-headers-strategy: native`** (`RemoteIpValve` de Tomcat) confiando solo en la IP fija de `edge`; `edge`
     confía solo en la IP fija del contenedor `tailscale`, toma la última dirección de `X-Forwarded-For` (la que añade
     Tailscale) y la reenvía como valor único con `X-Forwarded-Proto: https`. Pros: sin código; imposible suplantar
     desde fuera porque cada salto solo acepta la cabecera de su vecino. Contras: depende de que `tailscale serve`
     añada `X-Forwarded-For` con la IP real en Funnel (a verificar en la tarea de exploración, T001).
  2. **`framework`** (`ForwardedHeaderFilter`). Pros: también resuelve `Forwarded`. Contras: confía en cualquier
     emisor salvo que se filtre a mano; más superficie.
  3. **Resolver la IP en `ClientIpResolver`** leyendo la cabecera. Pros: control total. Contras: duplica lo que hace
     Tomcat y es fácil equivocarse con la lista de confianza.
- **Decisión**: opción 1, solo en el perfil `prod`: `server.tomcat.remoteip.internal-proxies` = expresión exacta de la
  IP de `edge`, `remote-ip-header: x-forwarded-for`, `protocol-header: x-forwarded-proto`. `edge` borra `Forwarded`,
  `X-Forwarded-Host` y `X-Forwarded-Port` entrantes. Redes `edge-net` y `tailscale-net` con subred fija en el compose; `edge` confía en la IP de `tailscale` en `tailscale-net` y el backend en la de `edge` en `edge-net`.
- **Consecuencias**: `ClientIpResolver` no cambia. Plan B si T001 demuestra que Funnel no da la IP real: queda a
  decisión del usuario desactivar el freno por IP en producción y mantener el de par identificador-IP (que pasaría a
  ser por identificador). Test de integración: con `prod`, una petición de una IP no confiable con `X-Forwarded-For`
  conserva su `remoteAddr`.
- **Constitution Check**: II (configuración de adaptador, sin tocar `application`). Sin violaciones.

## R3. Modelo de despliegue continuo: pull desde casa

- **Contexto**: GitHub no llega a la casa y no se abren puertos (FR-017). Hay que publicar la imagen y que el servidor
  la recoja; FR-008 (solo con checks en verde), US3-3 (si la migración falla sigue la versión anterior) y el caso límite
  del servidor apagado durante el merge.
- **Opciones**:
  1. **GHCR + actualizador en el host** (script propio con temporizador de systemd cada 2 min): consulta el digest de
     la etiqueta `production`, migra con la imagen nueva mientras la vieja sigue atendiendo, cambia el contenedor,
     espera a `readiness` y, si no llega, vuelve a las imágenes `:previous` registradas en `previous.env`. Pros: sin credenciales de GitHub con escritura en
     casa; nada de terceros con el socket de Docker; si el servidor estaba apagado, aplica la última versión al volver.
     Contras: script propio que mantener; latencia de sondeo.
  2. **GHCR + Watchtower**. Pros: cero código. Contras: proyecto archivado (comprobado); monta el socket de Docker
     (equivale a root sobre Immich y Home Assistant); no sabe migrar antes ni volver atrás.
  3. **Runner self-hosted**. Pros: el despliegue es un job más y su estado se ve en GitHub. Contras: repo público →
     riesgo de ejecutar código ajeno en casa si se equivoca el disparador; el runner necesita el socket de Docker y un
     token de registro; un proceso más con salida a GitHub.
- **Decisión**: opción 1. En `ci.yml`, job `publish` con `needs: [backend, contract, app]` e
  `if: github.event_name == 'push' && github.ref == 'refs/heads/master'`: construye las dos imágenes, pasa el humo de
  compose (R4) y solo entonces publica `sha-<commit>` y mueve `production`. Imágenes públicas en GHCR (el repo es
  público y las imágenes no llevan secretos, FR-001), así el servidor no guarda token de lectura. Un paquete nuevo de
  GHCR nace privado: tras el primer `publish`, el mantenedor lo cambia a público en *Package settings* de cada imagen
  (alternativa: token clásico de solo `read:packages` en el host con `docker login ghcr.io`).
- **Consecuencias**: «no despliega si los checks fallan» se cumple por `needs` y por la protección de rama de `master`;
  `production` nunca apunta a un commit con checks en rojo. El actualizador guarda en `$NEOCAT_HOME/state/current.env` las referencias inmutables
  `BACKEND_IMAGE` y `WEB_IMAGE` (`repo@sha256:` en producción) y, antes de cambiar, etiqueta en local las imágenes en
  marcha como `neocatecumenal-{backend,web}:previous` y escribe `previous.env` apuntando a esas etiquetas; la vuelta
  atrás usa `previous.env` y funciona igual con imágenes locales sin digest de registro (`apply`). Cada ejecución
  termina con ping de éxito o `/fail` al canal de R9. SC-002: CI (≈ 8 min, a medir) + publicación (≈ 4 min) + sondeo (≤ 2 min); si se pasa de 15 min, el sondeo baja a
  1 min. Si la versión de `production` no migra o no llega a `healthy`, el actualizador vuelve atrás, anota sus digest en
  `state/failed.env` y los salta mientras `production` no cambie, con un único `/fail` (sin ello repetiría el ciclo y
  tiraría el servicio cada 2 min); el `apply` manual lo ignora. Tras un despliegue correcto levanta también `tailscale`.
  Reglas nuevas: migraciones compatibles con la versión anterior (R5) y contrato compatible con las apps
  instaladas (R11). Endurecimiento opcional, fuera de alcance: atestación de procedencia en CI y verificación en el host.
- **Constitution Check**: ninguno de I-IX afectado; «Secrets never enter the repository» se cumple (sin secretos en
  imágenes ni en el compose).

## R4. Empaquetado y humo en CI

- **Contexto**: FR-001 (artefacto reproducible sin secretos) y validación en local con Rancher Desktop.
- **Opciones**:
  1. **Dockerfile multietapa** (`eclipse-temurin:25-jdk` compila con `./mvnw -DskipTests package`; `25-jre` ejecuta
     el jar extraído por capas con `-Djarmode=tools`). Pros: el mismo fichero en el Mac y en CI; sin herramientas de
     terceros. Contras: compila otra vez en el job `publish` (los tests ya pasaron en `backend`).
  2. **`spring-boot:build-image`** (buildpacks de Paketo). Pros: sin Dockerfile. Contras: imágenes de builder de
     terceros pesadas y difíciles de fijar; lento en Rancher Desktop.
  3. **Reutilizar el jar del job `backend`** como artefacto. Pros: no recompila. Contras: en local no hay ese
     artefacto y el Dockerfile tendría dos caminos.
- **Decisión**: opción 1 para el backend; la web con `deploy/edge/Dockerfile` (`node:24` exporta la web, nginx
  estable la sirve). Usuario sin privilegios, sistema de ficheros de solo lectura con `tmpfs` en `/tmp`, etiqueta OCI
  `org.opencontainers.image.revision` y `ENV SERVICE_REVISION=<commit>` por `--build-arg` (obligatorio, `^[0-9a-f]{7,40}$`; el build falla sin él). El job `publish` arranca
  `deploy/compose.yaml` + `deploy/compose.smoke.yaml` (sin túnel, `edge` en `127.0.0.1:8088`) y comprueba
  `/api/health` y `/api/compatibility` a través de `edge`, y que `/actuator/health` da 404 desde fuera.
- **Consecuencias**: caché de buildx en GHA. Etiquetas exactas: `eclipse-temurin:25.0.5_8-jdk-noble` (a verificar; la
  `jre` está comprobada), `nginx:1.30.8-alpine`, `node:24` (a verificar), todas con digest. Presencia de `curl` en la
  imagen `jre` para el `healthcheck`, a verificar; si no está, `healthcheck` con `wget` de una etapa `busybox` copiada.
- **Constitution Check**: IX (Dockerfiles y YAML sin comentarios). Sin violaciones.

## R5. Migraciones antes de atender y vuelta atrás (FR-006, US3-3)

- **Contexto**: con un solo contenedor, `docker compose up -d` para la versión vieja antes de arrancar la nueva; si
  Flyway falla, no queda nadie atendiendo.
- **Opciones**:
  1. **Paso de migración separado con la imagen nueva** (perfil `migrate`: sin web, ejecuta Flyway y la validación de
     Hibernate y sale con su código) mientras la vieja sigue atendiendo; solo si sale con 0 se cambia el contenedor.
     Pros: una migración fallida no corta el servicio; PostgreSQL revierte el DDL transaccional. Contras: durante unos
     segundos la versión vieja corre sobre el esquema nuevo → regla de compatibilidad hacia atrás.
  2. **Azul/verde** con dos contenedores y cambio en `edge`. Pros: sin corte. Contras: más piezas para una parroquia.
  3. **Flyway CLI** en otra imagen. Pros: separa migrar de arrancar. Contras: tercera imagen y migraciones duplicadas
     fuera del jar.
- **Decisión**: opción 1. `MigrationOnlyRunner` en el paquete raíz (como `ClockConfig`), activo con `@Profile("migrate")`
  y `spring.main.web-application-type: none`, cierra el contexto con `SpringApplication.exit`. Corte esperado al cambiar
  de contenedor: segundos.
- **Consecuencias**: regla de equipo (a `AGENTS.md` en la implementación): toda migración es aditiva respecto a la
  versión anterior (expandir/contraer: renombrar o borrar columnas en dos despliegues). Sin `CREATE INDEX CONCURRENTLY`
  ni sentencias no transaccionales sin plan propio. Una migración aplicada no se deshace al volver atrás la imagen;
  por eso la regla. Si la migración falla, las últimas 40 líneas de su salida quedan en `state/migrate.log` (sin
  secretos; nunca van al ping) y los digest en `state/failed.env` (R3: no se reintenta mientras `production` no cambie).
- **Constitution Check**: IV (test con Testcontainers: con `migrate` el proceso sale con 0 y no abre puerto; una
  migración rota por `SPRING_FLYWAY_LOCATIONS` sale con ≠ 0 sin tocar el esquema). Sin violaciones.

## R6. Configuración de producción (FR-002, FR-003)

- **Contexto**: `application.yml` trae valores por defecto de la base de datos local (usuario y contraseña
  `neocatecumenal`), que en producción enmascararían una variable olvidada y son una contraseña en configuración.
- **Opciones**:
  1. **Base sin valores por defecto de conexión**; el perfil `local` los aporta; perfil `prod` con
     `${DATABASE_URL}`, `${ALLOWED_ORIGINS}`, `${PRIVACY_NOTICE_VERSION}` y `${MINIMUM_APP_VERSION}` sin valor por
     defecto, y una guarda que falla si `prod` va con `local` o si `identity.allowed-origins` queda vacío. Pros: falla al
     arrancar y Spring nombra la propiedad sin mostrar su valor. Contras: los tests y `local-run.sh` dependen del perfil
     `local` o de Testcontainers (ya es así).
  2. **Solo perfil `prod`** que sobrescribe la base. Pros: no toca local. Contras: la contraseña sigue en la base.
- **Decisión**: opción 1. Secretos por `spring.config.import: optional:configtree:/run/secrets/` (ficheros de secretos
  de compose, no variables de entorno visibles en `docker inspect`). `ProductionConfigurationGuard` en el paquete raíz,
  `@Profile("prod")`.
- **Consecuencias**: test de arranque con `prod` sin `ALLOWED_ORIGINS` → falla con el nombre de la propiedad y sin
  valores en el log. `application-bootstrap.yml` no cambia.
- **Constitution Check**: VIII, IX. Sin violaciones.

## R7. Primer administrador en producción (FR-007, US2)

- **Contexto**: hoy `BootstrapIdentityService` asegura todas las comunidades de `identity.bootstrap.communities` y la
  del administrador aunque ya exista uno, y solo se salta la creación del usuario. FR-007 exige abortar sin cambios y
  no crear comunidades extra.
- **Opciones**:
  1. **Quitar la lista `communities`** del comando y de las propiedades y comprobar primero si hay administrador:
     si lo hay, `AdministratorAlreadyExistsException` (aplicación) antes de tocar nada; si no, aprovisiona solo la
     comunidad del administrador y crea el usuario. Pros: una sola semántica en todos los entornos; menos código.
     Contras: en local ya no se cargan comunidades en lote (se crean desde la app, 003).
  2. **Mantener la lista y rechazarla con `prod`**. Pros: conserva la carga en lote local. Contras: dos comportamientos
     y una condición de perfil en un caso de uso.
- **Decisión**: opción 1, con un bloqueo de transacción (`UserRepository.lockForBootstrap`, `pg_advisory_xact_lock`)
  antes de la comprobación para que dos ejecuciones simultáneas no creen dos administradores. En producción se ejecuta
  con el servicio de compose `bootstrap` (perfil de compose `ops`, `SPRING_PROFILES_ACTIVE=prod,bootstrap`,
  `tty: true`, `stdin_open: true`, `logging.driver: none` y `--rm`), con nombre y número de comunidad por `-e`.
- **Consecuencias**: la contraseña sale solo por la consola (ya era así) y Docker no la guarda en su log por el driver
  `none`. El runner registra «ya existe un administrador» y sale con ≠ 0. Cambian `BootstrapCommand`,
  `BootstrapOutcome` (desaparece `createdCommunities`), `IdentityProperties.Bootstrap`, `BootstrapRunnerIT`,
  `IdentityPropertiesTest` y la documentación de 002.
- **Constitution Check**: I, II (excepción de aplicación, puerto nuevo en `application.port.out`), IV (test primero
  sin Spring: con administrador no se llama a `CommunityProvisioner` ni a `UserRepository.save`). Sin violaciones.

## R8. Copias en una partición aparte del servidor (FR-009, FR-010, FR-011, SC-007)

- **Contexto**: decisión del usuario: copia diaria, retención de 30 días, solo en una partición del propio servidor
  distinta de la de datos; sin copia fuera de casa (perder el equipo entero = perder los datos, riesgo aceptado en la
  spec). La clave de las copias se custodia también fuera del servidor (FR-011). Volumen: pocos MB.
- **Opciones**:
  1. **`pg_dump -Fc` por tubería a `restic`** (`backup --stdin --stdin-filename neocatecumenal.dump`) en un
     repositorio dentro de la partición de copias. Pros: cifrado propio con una clave independiente del disco (una
     copia sacada del equipo sigue cifrada); deduplicación (30 copias diarias ocupan poco más que una);
     `forget --keep-within-daily 30d` por calendario; `restic check`; imagen oficial fijada. Contras: una herramienta
     más que aprender para restaurar.
  2. **Ficheros `pg_dump` sueltos** con borrado por `find -mtime +30`. Pros: lo más simple; restaurar es `pg_restore`.
     Contras: sin cifrado propio (solo LUKS) y por tanto sin la «clave de copias» de FR-011; 30 copias completas;
     sin verificación de integridad.
  3. **`pg_basebackup` + WAL** (recuperación a un instante). Pros: pérdida de minutos, no de un día. Contras:
     complejidad y espacio desproporcionados para una parroquia.
- **Decisión**: opción 1. Partición dedicada (ideal: segundo disco M.2 si el equipo lo admite, para sobrevivir a la
  avería del disco de datos; si no, partición aparte del mismo disco) cifrada con LUKS2 + TPM2 como el resto,
  montada en `$NEOCAT_BACKUP_DIR` (por defecto `/mnt/neocatecumenal-backups`). `neocat-backup` diario: comprueba que
  `$NEOCAT_BACKUP_DIR` es un punto de montaje (`NEOCAT_BACKUP_REQUIRE_MOUNT=false` solo en el Mac), vuelca a `restic`
  excluyendo los datos de `identity_session` y `identity_login_*_throttle`, aplica
  `restic forget --keep-within-daily 30d --prune`, comprueba que la instantánea más antigua tiene < 31 días y que la
  partición está por debajo del 80 % de uso. `restic check` semanal. `RESTIC_REPOSITORY=$NEOCAT_BACKUP_DIR/restic` y
  `RESTIC_PASSWORD_FILE=$NEOCAT_CONFIG_DIR/restic.password` (`400`, root, montado de solo lectura en el contenedor de restic, sin variable de entorno); esa contraseña y la clave de recuperación
  LUKS también en el gestor de contraseñas del mantenedor (FR-011).
- **Consecuencias**: coste recurrente de copias: 0 € (SC-007). Todo queda en el servidor de casa, en España (FR-010).
  Restauración: desde la partición con `neocat-restore`, también tras reinstalar el sistema si solo falló el disco de
  datos. Riesgos aceptados: robo, incendio o avería total del equipo; un atacante con root en el servidor puede borrar
  las copias.
- **Constitution Check**: VIII (copias cifradas; minimización con exclusión de tablas efímeras). Sin violaciones.

## R9. Aviso de fallo y de partición llena el mismo día (FR-020)

- **Contexto**: un aviso solo por «fallo» no salta si el servidor está apagado o el temporizador no corre. FR-020
  incluye la partición de copias llena.
- **Opciones**:
  1. **Healthchecks.io** (interruptor de hombre muerto: avisa por correo si no llega el ping de éxito en el periodo).
     Pros: detecta fallo y ausencia; plan gratuito; solo recibe pings sin datos personales. Contras: servicio de
     terceros (sede en la UE, a verificar).
  2. **ntfy** con avisos de fallo. Pros: notificación al móvil. Contras: no detecta ausencia.
  3. **Correo por SMTP desde el servidor**. Pros: sin terceros nuevos. Contras: credenciales SMTP en casa; no detecta
     ausencia.
- **Decisión**: opción 1, con tres comprobaciones: `backup` (periodo 1 día, gracia 2 h), `erasure-sync` (R10) y
  `deploy` (periodo 1 h, gracia 1 h). `neocat-backup` hace `/fail` con un texto fijo si falla el volcado, `restic`,
  la retención, la antigüedad de la instantánea más antigua, si la partición no está montada o si su uso pasa del
  80 % (umbral `NEOCAT_BACKUP_MAX_USE`), antes de que se llene de verdad. `neocat-update` hace ping de éxito al terminar
  bien cada ejecución (también sin cambios), así la comprobación vuelve a verde tras un fallo y avisa si el
  temporizador deja de correr; ante un fallo, `/fail` con un texto fijo del paso. URLs de ping en
  `$NEOCAT_CONFIG_DIR/*.env`.
- **Consecuencias**: correo al mantenedor el mismo día. El cuerpo de los pings nunca lleva salida de comandos que
  pueda incluir datos.
- **Constitution Check**: VIII. Sin violaciones.

## R10. Supresiones posteriores a la copia (FR-009)

- **Contexto**: al restaurar una copia de fecha D hay que volver a suprimir lo que se suprimió después de D, sin
  guardar datos personales fuera de la base de datos. Supresiones del sistema: borrar usuario (`identity`), borrar
  hermano y retirar el consentimiento de un campo de contacto, que borra el valor (`community`, 003 R7). Borrar una
  comunidad no suprime datos personales.
- **Opciones**:
  1. **Registro de supresiones en la base de datos, por módulo**: `identity_erasure` (id de usuario, fecha) y
     `community_erasure` (id de hermano, campo opcional, fecha), escritos por el caso de uso en la misma transacción
     que la supresión, sin nombres ni valores, purgados a los 35 días. La purga está atada a la instantánea más antigua: `neocat-backup` garantiza (y avisa si
     no) que no hay instantánea de 31 días o más, así que ninguna copia es más antigua que el registro conservado; el
     módulo no lee el repositorio de copias y sigue siendo el único escritor de su tabla. Al restaurar,
     un script aplica sobre la base restaurada las entradas con fecha posterior a la copia, tomadas de la fuente más
     reciente disponible (producción si existe; si no, la copia más nueva). Pros: no sale nada de la base; distingue
     supresiones pedidas de borrados accidentales; cada tabla con su dueño. Contras: dos tablas, un puerto y una purga
     por módulo; un script SQL de operación.
  2. **Disparadores de PostgreSQL** que anotan los `DELETE`. Pros: sin tocar los casos de uso. Contras: anotarían
     también un borrado accidental o un error de código, y el script volvería a borrar lo que se quería recuperar;
     lógica invisible a los tests de dominio.
  3. **Comparar la copia con producción** y borrar lo que falte. Pros: sin tablas. Contras: idéntico problema con los
     borrados accidentales y no sirve si falló el disco de datos.
  4. **Lista fuera de la base** (fichero o gestor del mantenedor). Contras: contradice la restricción.
- **Decisión**: opción 1, más una sincronización horaria (`neocat-erasure-sync`) que lanza una copia extra en la
  partición si hay supresiones posteriores a la última instantánea. Así, si falla el disco de datos, la ventana en la
  que una supresión no ha llegado a ninguna copia baja de 24 h a 1 h.
- **Consecuencias**: el script de restauración borra usuarios y hermanos por id y pone a `null` los campos de contacto
  indicados; si un borrado choca con una FK (p. ej., el hermano era responsable en la copia), anonimiza el nombre con
  un texto fijo y vacía el contacto, y lo informa por id. El aviso de privacidad dice que lo suprimido desaparece de
  las copias al caducar (30 días). Puertos `ErasureRecords` en `identity` y `community`; la purga reutiliza el patrón de
  `AccessDataPurgeJob`.
- **Constitution Check**: II, III (una tabla por módulo, sin tabla compartida), IV (test de aplicación: borrar usuario,
  borrar hermano y retirar consentimiento anotan; test de integración del script con una copia sintética), VIII
  (pseudónimos con retención corta y justificada). Sin violaciones.

## R11. Versión mínima de app (FR-021, US5-4)

- **Contexto**: el API se despliega solo desde `master`; las apps de las tiendas tardan días en actualizarse. Si un
  cambio rompe el contrato, una app vieja no debe fallar en silencio.
- **Opciones**:
  1. **Endpoint público `GET /api/compatibility`** con `minimumAppVersion` y `serviceRevision`; la app lo consulta al
     arrancar y al volver a primer plano. Pros: contrato explícito y aditivo; también cubre US3-4. Contras: una petición
     más al abrir.
  2. **Cabecera `X-App-Version` en cada petición y `426 Upgrade Required`**. Pros: el servidor hace cumplir el mínimo.
     Contras: toca todas las operaciones del contrato y los tests de cada una.
  3. **Configuración remota en EAS Update**. Pros: sin backend. Contras: la versión mínima depende del API, no del
     cliente; otra fuente de verdad.
- **Decisión**: opción 1. Servidor: propiedad `service.minimum-app-version` (perfil `prod` desde
  `MINIMUM_APP_VERSION`; base `1.0.0`) y `SERVICE_REVISION`, servidos por `ServiceInfoController` en
  `identity.infrastructure.in.rest`, junto a `HealthController` (precedente de endpoints operativos). Cliente: versión
  propia desde `Constants.expoConfig.version` (con `runtimeVersion.policy: "appVersion"`, una actualización OTA nunca
  llega a un binario de otra versión, así que coincide con la de la tienda); comparación semántica pura en
  `lib/compatibility/`; si es menor, pantalla bloqueante `UpdateRequiredScreen` con botón a la tienda; si la consulta
  falla (sin red), no bloquea. En web no se comprueba (web y API salen del mismo commit).
- **Consecuencias**: regla de contrato: cambios aditivos; si uno no lo es, se sube `MINIMUM_APP_VERSION` en el mismo
  despliegue y antes se publica la app nueva. `serviceRevision` expone el commit (repo público; riesgo bajo).
- **Constitution Check**: V (contrato primero, aditivo, 0.3.0 → 0.4.0), VI (lógica en `lib/` y hook), VII (pantalla
  accesible), IX (textos en `i18n/es`). Sin violaciones.

## R12. Builds móviles, EAS Update y tiendas (FR-012, FR-013, FR-016)

- **Contexto**: no hay `eas.json`, `ios.bundleIdentifier`, `android.package` ni `expo-updates`.
- **Opciones de disparo**:
  1. **Workflows manuales** (`workflow_dispatch`) en GitHub con `EXPO_TOKEN`: `mobile-build.yml` (EAS Build +
     Submit) y `mobile-update.yml` (EAS Update a `production`). Pros: trazable y sin portátil. Contras: un secreto más
     en GitHub.
  2. **Desde el portátil del mantenedor** con `eas-cli`. Pros: nada en CI. Contras: depende de una máquina; menos
     trazable.
  3. **Automático en cada merge**. Contras: un OTA a producción por cada merge sin revisar en móvil.
- **Decisión**: opción 1, con `eas.json` de perfiles `development` (canal `development`), `preview` (interno, canal
  `preview`) y `production` (canal `production`, `autoIncrement`, `EXPO_PUBLIC_API_URL=https://<nodo>.<tailnet>.ts.net`
  y URLs de tienda). `app.json`: `runtimeVersion.policy: "appVersion"`, `updates.url` del proyecto EAS (lo crea
  `eas init`), `expo-updates` por `npx expo install` (58.0.22 comprobada como última 58.x). Identificador decidido por el usuario:
  `es.parroquia.neocatecumenal` como `ios.bundleIdentifier` y `android.package` (permanente una vez publicado).
- **Consecuencias**: `EXPO_PUBLIC_API_URL` se incrusta en el bundle JS: si cambiara el hostname, basta un OTA. Cuenta
  de revisor (FR-016) desde la app, en una comunidad ficticia. Prueba cerrada de Play Store: plazo del mantenedor.
- **Constitution Check**: VI, IX. Sin violaciones.

## R13. Web pública (US6, FR-014, FR-018)

- **Contexto**: `app.json` usa `web.output: "static"`; las rutas dinámicas generan ficheros `[id].html` que un servidor
  estático no resuelve sin reglas por ruta.
- **Opciones**:
  1. **`web.output: "single"`** y `try_files $uri /index.html` en nginx. Pros: cualquier ruta interna recargada
     funciona (US6-1). Contras: sin HTML prerenderizado por ruta (irrelevante en una app con sesión).
  2. **Mantener `static`** con reescrituras por cada ruta dinámica. Contras: hay que mantener nginx al día con las rutas.
- **Decisión**: opción 1. Política de privacidad pública en la ruta `/privacy` de la web, accesible sin sesión, como URL
  estable para las fichas (FR-018); el texto lo aporta la parroquia.
- **Consecuencias**: si el servidor de casa está caído, la URL de privacidad también; alternativa si las tiendas lo
  exigen: GitHub Pages del repo con el mismo texto.
- **Constitution Check**: VI, VII. Sin violaciones.

## R14. Servidor: aislamiento, cifrado y secretos (FR-011, FR-017, FR-019)

- **Contexto**: mini PC compartido con Immich y Home Assistant; disco cifrado; sin puertos abiertos; arranque tras un
  corte de luz sin nadie delante.
- **Opciones de cifrado de disco**:
  1. **LUKS2 con desbloqueo por TPM2** (`systemd-cryptenroll`, PCR 7) y clave de recuperación en el gestor de
     contraseñas. Pros: arranca solo tras un corte; protege el disco extraído. Contras: si se roban el equipo entero,
     arranca hasta la pantalla de acceso.
  2. **LUKS2 con frase al arrancar** (o desbloqueo remoto por SSH en initramfs). Pros: más fuerte ante robo del equipo.
     Contras: cada corte deja el servicio caído hasta que alguien teclea.
- **Decisión**: opción 1, decidida por el usuario: desbloqueo automático por TPM2 (TPM 2.0 del GEEKOM a verificar al
  comprarlo). Aislamiento en `deploy/compose.yaml`: proyecto
  `neocatecumenal`, redes propias `db-net`, `edge-net` (backend↔edge) y `tailscale-net` (edge↔tailscale), las dos primeras `internal: true` para que el backend no tenga salida a Internet, ningún `ports:` publicado, volúmenes con
  nombre propio, `read_only`, `cap_drop: [ALL]`, `no-new-privileges`, límites de memoria y CPU para no ahogar a
  Immich, `restart: unless-stopped`, ningún contenedor con el socket de Docker. Tailscale en modo `userspace` (sin
  `NET_ADMIN` ni `/dev/net/tun`), nodo con etiqueta `tag:neocatecumenal` y ACL sin acceso saliente a otros nodos del
  tailnet. Secretos en `$NEOCAT_HOME/secrets/` (`600`), montados como secretos de compose. Rutas base configurables en todos
  los scripts: `NEOCAT_HOME` (por defecto `/srv/neocatecumenal`), `NEOCAT_CONFIG_DIR` (por defecto
  `/etc/neocatecumenal`) y `NEOCAT_BACKUP_DIR` (por defecto `/mnt/neocatecumenal-backups`), para ejecutarlos en el Mac con un directorio temporal. Logs de contenedores con el driver
  `local` (`max-size: 10m`, `max-file: 3`).
- **Consecuencias**: runbook en `docs/operacion.md` (FR-015) con instalación, cifrado, Tailscale, puesta en marcha,
  administrador, despliegue, restauración, rotación de secretos y builds móviles. Escaneo de secretos (SC-005):
  secret scanning y push protection de GitHub (gratis en repos públicos) más los tests de logs existentes.
- **Constitution Check**: VIII. «Secrets never enter the repository»: solo `*.env.example` sin valores. Sin
  violaciones.
