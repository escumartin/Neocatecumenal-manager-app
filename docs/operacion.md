# Operación del servidor

Runbook del mantenedor (FR-015). Nombres de servicios, variables y órdenes: `specs/004-despliegue-produccion/contracts/operations.md`. Sin secretos ni datos reales en este documento.

## 1. Dónde vive cada cosa

| Ruta | Contenido |
|---|---|
| `$NEOCAT_HOME/deploy/` | copia de `deploy/` del repo: `compose.yaml`, `bin/`, `sql/`, `systemd/`, `env/` (por defecto `NEOCAT_HOME=/srv/neocatecumenal`) |
| `$NEOCAT_HOME/secrets/` | `750`, `root:neocat-secrets`; ficheros `440`, `root:neocat-secrets`: `spring.datasource.username`, `spring.datasource.password`, `tailscale.authkey` |
| `$NEOCAT_HOME/.env` | `600`; `ALLOWED_ORIGINS`, `PRIVACY_NOTICE_VERSION`, `MINIMUM_APP_VERSION` (plantilla `deploy/env/neocatecumenal.env.example`) |
| `$NEOCAT_HOME/state/` | `700`; `current.env`, `previous.env` y `failed.env` del actualizador y `migrate.log` (últimas líneas de la última migración fallida, sin secretos) |
| `$NEOCAT_CONFIG_DIR` | `/etc/neocatecumenal`; `backup.env` y `deploy.env` (`600`) y `restic.password` (`400`), root; `docker/` con el `config.json` de `docker login` si GHCR es privado (las unidades fijan `DOCKER_CONFIG` ahí) |
| `$NEOCAT_BACKUP_DIR` | `/mnt/neocatecumenal-backups`; partición de copias; repositorio restic en `$NEOCAT_BACKUP_DIR/restic` |
| `/usr/local/bin/neocat-*` | enlaces a `$NEOCAT_HOME/deploy/bin/*` |

## 2. Cifrado: LUKS2 con TPM2

1. Disco de datos y partición de copias distintos (ideal: otro disco), ambos en LUKS2.
2. Enrolar el TPM2: `systemd-cryptenroll --tpm2-device=auto --tpm2-pcrs=7 <dispositivo>`; añadir la entrada `tpm2-device=auto` en `/etc/crypttab`.
3. Guardar la clave de recuperación LUKS y el contenido de `restic.password` en el gestor de contraseñas del mantenedor (FR-011); sin ellas no se abre nada si falla el TPM o se cambia la placa.
4. Comprobar: `lsblk -f` muestra `crypto_LUKS` en ambos; `findmnt $NEOCAT_BACKUP_DIR` la muestra montada.

Riesgo aceptado: con TPM2 el arranque es desatendido; si roban el equipo entero, arranca hasta la pantalla de acceso.

## 3. Puesta en marcha desde cero

1. Instalar Docker con `docker compose` y Tailscale; nodo con etiqueta `tag:neocatecumenal` y una ACL sin acceso saliente a otros nodos.
2. Copiar `deploy/` a `$NEOCAT_HOME/deploy/`; crear el grupo `neocat-secrets` (`groupadd --system neocat-secrets`; su gid, en `NEOCAT_SECRETS_GID` del `.env`), `secrets/` (`750`) con los tres ficheros (`440`), todo `root:neocat-secrets`, y `.env` (`600`). Los contenedores leen los secretos como miembros de ese grupo (`group_add`); sin él, PostgreSQL (uid 999) y la app (uid 10001) no pueden leer ficheros `600` de root en Linux.
3. Crear `backup.env` y `deploy.env` en `$NEOCAT_CONFIG_DIR` desde sus `.example` (`600`, root) y `restic.password` (`400`, root, solo la contraseña; las órdenes lo montan de solo lectura en el contenedor de restic). Crear `$NEOCAT_HOME/state` (`700`): las unidades solo pueden escribir ahí.
4. Paquetes de GHCR en público (o `docker login ghcr.io` como root con un token `read:packages`).
5. Enlazar `deploy/bin/*` en `/usr/local/bin` y copiar `deploy/systemd/*` a `/etc/systemd/system/`.
6. Primer despliegue a mano: `neocat-update`. Levanta `postgres` y `backend` (con `edge` es lo que espera a `healthy`) y, con el servicio sano, `tailscale`; si `tailscale` no arranca avisa por la salida sin dar la versión por mala.
7. `systemctl enable --now neocat-update.timer neocat-backup.timer neocat-erasure-sync.timer`.
8. Comprobar: `curl -fsS https://<nodo>.<tailnet>.ts.net/api/compatibility` y `ss -tlnp` sin puertos del proyecto en el host.

## 4. Primer administrador

```bash
cd $NEOCAT_HOME
docker compose -f deploy/compose.yaml --env-file .env --profile ops run --rm \
  -e IDENTITY_BOOTSTRAP_ADMINISTRATORNAME=<nombre> -e IDENTITY_BOOTSTRAP_ADMINISTRATORCOMMUNITY=<número> bootstrap
```

Imprime identificador y contraseña solo en la terminal (sin log). Si ya hay un administrador aborta con exit distinto de 0. El administrador cambia la contraseña en el primer inicio de sesión. Después crea comunidades y usuarios desde la app.

## 5. Despliegue y vuelta atrás

Qué hace cada orden, sus disparos y salidas: [operations.md § 5](../specs/004-despliegue-produccion/contracts/operations.md#5-órdenes-del-host-deploybin).

- Si `production` apunta a una versión que no migra o no llega a `healthy`, el servicio sigue o vuelve a la anterior, hay un único `/fail` a `deploy` y `neocat-update` anota la versión en `state/failed.env`: mientras `production` no cambie, no la reintenta ni vuelve a avisar. El diagnóstico está en `state/migrate.log` (migración) o en `docker compose logs backend`.
- Arreglo: publicar un commit nuevo que cambie `production`; la comprobación `deploy` vuelve a verde con la primera ejecución correcta.
- Vuelta atrás a mano: `neocat-update apply neocatecumenal-backend:previous neocatecumenal-web:previous` (`apply` ignora `failed.env`).
- Las migraciones deben ser compatibles con la versión anterior (R5); si no lo son, la vuelta atrás exige restaurar la copia.

## 6. Restauración

Contenido y calendario de las copias: [operations.md § 5](../specs/004-despliegue-produccion/contracts/operations.md#5-órdenes-del-host-deploybin).

- Restaurar en una base aparte: `neocat-restore latest neocatecumenal_restore` (o un id de instantánea). Rechaza una base con datos; reaplica las supresiones posteriores a la copia (informe por id, sin nombres) y deja `identity_session` vacía.
- Disco de datos perdido: crear el volumen, `docker compose up -d postgres`, montar la partición y `neocat-restore latest neocatecumenal`; después `neocat-update`.
- Objetivo: restauración en menos de 30 min (SC-003).

Riesgo aceptado: las copias están solo en la partición local del servidor, sin copia fuera de casa. Un incendio, robo o fallo del equipo pierde datos y copias; decisión del usuario.

## 7. Avisos (Healthchecks.io)

Comprobaciones, periodos y quién hace ping: [operations.md § 7](../specs/004-despliegue-produccion/contracts/operations.md#7-comprobaciones-de-healthchecksio). Las URL van en `backup.env` y `deploy.env`; el fallo envía correo.

## 8. Rotación de secretos

- Contraseña de base de datos: cambiar el rol en PostgreSQL (`ALTER ROLE`), actualizar `secrets/spring.datasource.password` y `docker compose up -d --force-recreate backend`.
- Clave de Tailscale: generar otra, sustituir `secrets/tailscale.authkey` y recrear `tailscale`.
- Contraseña de restic: `restic key add` con la nueva, `restic key remove` con la antigua, reescribir `restic.password` (`400`) y actualizar el gestor de contraseñas.
- `EXPO_TOKEN`: regenerar en Expo y actualizar el secreto del repositorio en GitHub.
- Secreto filtrado: rotarlo de inmediato con el paso anterior que corresponda, revocar el original en su origen, revisar accesos y logs del servicio afectado y, si estuvo en un commit, no reescribir el historial: la rotación es la defensa. Secret scanning y push protection deben estar activos en el repo.

## 9. Builds móviles y EAS Update

- Build: workflow `Mobile build` (`workflow_dispatch`, plataforma y envío a tiendas opcional); usa el secreto `EXPO_TOKEN`. `EXPO_PUBLIC_API_URL` no viene de variables del repo: la toma del bloque `env` del perfil en `app/eas.json`, que hoy lleva un marcador (T068); sustituirlo por la URL real de Funnel antes de la primera build de producción.
- Cambio solo de JavaScript: workflow `Mobile update`, canal `production`, con mensaje; llega al móvil en menos de 1 h. Solo funciona con `updates.url` real en `app/app.json`, que escribe `eas init`; con el marcador actual falla.
- Cambio de contrato incompatible: subir `MINIMUM_APP_VERSION` en `.env` y desplegar; las apps por debajo muestran la pantalla de actualización. Si el contrato solo crece de forma aditiva, no hace falta (R11).

## 10. Cuenta de revisor de las tiendas

El administrador crea desde la app una comunidad propia y una cuenta de revisor con datos ficticios, entrega sus credenciales por el formulario de la tienda y la desactiva al terminar la revisión. Nunca datos reales. Si una tienda rechaza la app por uso restringido, publicar como no listada.

## 11. Revisión del servidor

- `lsblk -f` y `findmnt $NEOCAT_BACKUP_DIR` (sección 2).
- `ss -tlnp` sin puertos del proyecto; el router sin reenvíos (FR-017).
- `docker network inspect`: `db-net` (solo `postgres`, `backend`, `migrate` y `bootstrap`) y `edge-net` (solo `backend` y `edge`) con `internal: true`; `tailscale-net` solo con `edge` y `tailscale`. El `backend` no tiene salida a Internet.
- La ACL de Tailscale impide alcanzar otros nodos.
- `docker pull` de las imágenes sin credenciales (o con el token `read:packages`).
- `secrets/` en `750` y sus ficheros en `440`, `root:neocat-secrets`; `$NEOCAT_CONFIG_DIR/*.env` en `600`.
- Secret scanning y push protection activos.
