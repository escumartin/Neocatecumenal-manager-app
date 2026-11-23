---
name: conventions-security
description: Secure-code patterns for the Java 21 Spring Boot 3 backend and the Expo client, including GDPR (data minimisation, consent, erasure, no real data or secrets in the repo). Use when writing or editing code, scripts or configuration, especially endpoints, persistence, validation, logging and secrets.
---

# Patrones de código seguro

Severidades: CRITICAL bloquea, HIGH se corrige antes de la PR, MEDIUM se anota.

## Patrones críticos

| Patrón | Severidad | Corrección |
|---|---|---|
| Secreto hardcodeado o versionado (token, contraseña, clave, `.pem`, `.env`), también de local o de un contenedor desechable | CRITICAL | Variable de entorno o gestor de secretos; si ya entró en el repo, se rota |
| Datos personales reales en el repo (fixtures, seeds, capturas, volcados, tests) | CRITICAL | Datos sintéticos; nada de personas reales |
| Fichero que no debe versionarse (volcados, capturas, temporales, `.env`, carpetas de herramientas) | HIGH | Sacarlo del árbol o ignorarlo; revisar `git status`, no solo el diff |
| SQL con concatenación de strings (JDBC, `@Query` nativa, `EntityManager`) | CRITICAL | Parámetros bind con nombre |
| Falta `@Valid` o `@Validated` en un body, y validación solo en el cliente | CRITICAL | Validar en el borde del backend; el contrato OpenAPI define los límites |
| Endpoint sin autenticación o sin comprobar el rol | CRITICAL | Spring Security con autorización por rol y por recurso |
| Autorización solo en la UI | CRITICAL | El backend decide siempre quién ve o cambia qué |
| Cliente HTTP (`RestClient`) con URL controlada por el usuario | HIGH | Allowlist o URL solo desde configuración |
| Dato personal en logs (nombre, teléfono, email, dirección) | CRITICAL | Solo identificadores; ver `conventions-observability` |
| Respuesta de error con stack trace o mensaje interno | HIGH | Mensaje genérico al cliente; el detalle va al log |
| Subida de ficheros sin validar tipo, tamaño y contenido | HIGH | Validación en el borde |
| Rate limiting ausente en login y endpoints públicos | HIGH | Límite en la aplicación o en el proxy |
| Propiedad de seguridad con default permisivo | CRITICAL | Default seguro; ver sección siguiente |
| Secreto o token del cliente Expo en el bundle o en `EXPO_PUBLIC_*` | CRITICAL | Solo lo público va al bundle; los secretos viven en el backend |
| Token de sesión en `AsyncStorage` sin cifrar | HIGH | `expo-secure-store` en móvil |

## RGPD

- **Minimización.** Se recoge y se expone solo lo necesario para el caso de uso; las respuestas de la API no devuelven campos que el rol no necesita.
- **Consentimiento.** Todo tratamiento de datos personales cuelga de un consentimiento registrado (quién, cuándo, para qué). Los datos de menores requieren el consentimiento de su tutor.
- **Borrado.** Cada dato personal tiene un camino de supresión y de exportación; los borrados en cascada y los backups se contemplan al diseñar el modelo.
- **Sin datos reales ni secretos en el repo.** Tests, seeds, fixtures y documentación usan datos sintéticos.
- **Datos sensibles.** La pertenencia a un movimiento religioso es dato de categoría especial: acceso mínimo y trazado, nunca en logs ni en métricas.

## Defaults de configuración con impacto de seguridad (CRITICAL)

Una propiedad que decide si el sistema está protegido no puede quedar abierta por omisión.

1. El default de `application.yml` es el restrictivo; el modo laxo se activa explícitamente en el perfil local.
2. Si el default debe ser permisivo, un fail-fast de arranque impide que un entorno desplegado lo herede.
3. Cada entorno desplegado fija el valor de forma explícita, aunque coincida con el default.

En revisión, busca `matchIfMissing = true`, `havingValue = "false"` y los `${VAR:default}` que relajen una protección.

## Java 21 y Spring Boot 3

```java
@Value("${calendar.api.token}")
private String apiToken;
```

```java
@Query("SELECT r FROM RoomJpaEntity r WHERE r.name = :name")
Optional<RoomJpaEntity> findByName(@Param("name") String name);
```

```java
@PostMapping
public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request)
```

```java
log.error("Error interno al crear la reserva", ex);
return ResponseEntity.status(500).body(new ErrorResponse("INTERNAL", "Error interno"));
```

## Hexagonal

- La validación de formato vive en el adaptador REST; las invariantes de negocio, en el dominio.
- El dominio no conoce Spring Security: la identidad llega al caso de uso como dato del puerto de entrada.
- Los adaptadores de persistencia no exponen entidades JPA fuera de su módulo.

## Infraestructura

- Contenedores sin root e imágenes base de origen confiable.
- CI con credenciales federadas (OIDC) y sin secretos de larga duración.
