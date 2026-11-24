---
name: conventions-observability
description: Observability conventions for the Java 25 Spring Boot 4 backend (structured logging in Spanish, levels, no personal data in logs, low-cardinality metrics, tracing, health) and error reporting in the Expo client. Use when writing production code or editing logs, metrics or health checks.
---

# Convenciones de observabilidad

Severidades: CRITICAL bloquea, HIGH se corrige antes de la PR.

## Logging estructurado (CRITICAL)

- Cada dato del evento va como par clave-valor (`log.atInfo().addKeyValue("bookingId", id).log("Reserva creada")`), nunca concatenado en el mensaje.
- Dominio y aplicación solo usan la fachada SLF4J; el formato JSON lo pone la configuración del logging.
- Los mensajes de log están en castellano; los nombres de las claves, en inglés y en `camelCase`.

## Niveles (HIGH)

- Fallo de negocio esperado en un punto de entrada (endpoint, listener de evento, tarea): WARN. Inesperado: ERROR con la excepción.
- Flujo normal relevante: INFO. Detalle de diagnóstico: DEBUG, apagado por defecto.
- Default en producción: INFO.

## Datos personales y secretos (CRITICAL)

- Solo identificadores. Nunca nombre, teléfono, email, dirección ni un command, entity o payload completo (su `toString()` arrastra datos personales).
- Nunca tokens, credenciales, valores de cookie, connection strings ni contenido de ficheros o mensajes.
- Los campos secretos de configuración se loguean por nombre, jamás por valor.
- Los datos sensibles (pertenencia a una comunidad, datos de menores) no salen en logs, métricas ni trazas.
- El input de terceros se sanea en la frontera donde entra.

## Métricas (CRITICAL)

Micrometer. Cada combinación de etiquetas es una serie activa.

- Etiquetas con valores acotados y conocidos (resultado, tipo de evento, módulo).
- Nunca como etiqueta: identificadores de reserva, usuario o comunidad, rutas con parámetros, mensajes de error ni nada derivado de una persona.
- Añadir una dimensión a una métrica existente se decide y se anota en la PR.

## Tracing

Instrumentación automática de Spring Boot (Micrometer Tracing, OpenTelemetry). Sin propagación manual. Los eventos entre módulos Spring Modulith se observan con la instrumentación del propio Modulith.

## Health (CRITICAL)

- Probes de liveness y readiness con Spring Boot Actuator.
- Solo se expone lo necesario (`health`); el detalle de los componentes no sale sin autenticar.

## Cliente Expo

- Errores capturados en un único punto (error boundary y manejador global), sin datos personales en el mensaje.
- Nada de `console.log` con datos de usuario, tokens o respuestas completas de la API.
- Cualquier servicio de reporte de errores recibe identificadores, no datos personales.
