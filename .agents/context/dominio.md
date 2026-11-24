Verificado: 2026-11-23 (fuente: TFG, capítulos 1 y 4)

# Dominio

## Contexto

Parroquia San Bartolomé (Murcia): unas 25 comunidades de hasta 80 hermanos, unos 1200 en total. Usuarios de 14 a 90 años;
uso principal en móvil. Hoy el ostiario principal reparte los salones parroquiales por WhatsApp; el objetivo es evitar que
dos grupos coincidan en el mismo salón y franja.

## Glosario ubicuo

| Término | Significado |
|---|---|
| Comunidad | grupo de hermanos identificado por un número; tiene un responsable obligatorio |
| Hermano / Usuario | persona de una comunidad: nombre, apellidos, dirección, teléfono fijo, móvil, email, sexo, comunidad |
| Lugar | salón parroquial, hotel o restaurante |
| Reserva | ocupación de un lugar en una franja |
| Evento | actividad con fecha y lugar; se especializa en Eucaristía, Palabra, Convivencia y Paso |
| Eucaristía | evento de varias comunidades, en sábado |
| Palabra | evento de una comunidad en un salón, entre semana; título «palabra» o «penitencial», con lecturas |
| Convivencia | evento mensual en domingo o de fin de semana; título, cuerpo, precio |
| Paso | fin de semana en hotel de una comunidad |
| Peregrinación | viaje con título, cuerpo, precio y fecha de salida; no guarda asistentes |
| Noticia | aviso de ámbito general y público |
| Ostiario | encargado de los salones; el principal reparte los salones de la parroquia |
| Responsable, salmistas, acólita | roles dentro de una comunidad |

## Roles de la app

Herencia: no identificado < registrado < moderador < administrador.

| Rol | Equivale a | Puede |
|---|---|---|
| No identificado | visitante | ver noticias e iniciar sesión |
| Registrado | hermano | ver su comunidad (directorio de contactos, Palabras, Eucaristías, Convivencias, Pasos, Peregrinaciones), generar grupos, editar su teléfono fijo, móvil y email |
| Moderador | responsable de comunidad | CRUD de noticias, eventos y usuarios solo de su comunidad |
| Administrador | ostiario jefe | todo, en cualquier comunidad, más CRUD de comunidades y lugares |

No hay autorregistro: el alta la hace un administrador o un moderador. Login con identificador numérico y contraseña.
El identificador de acceso es de 6 dígitos (100000–999999), lo genera el sistema y es inmutable. En el primer acceso la sesión queda restringida: primero se acepta el aviso de datos y después se cambia la contraseña inicial. No hay recuperación por email: la contraseña se reasigna por el moderador (registrados de su comunidad) o por un administrador.

## Bounded contexts propuestos

1. Identidad y acceso
2. Comunidades y hermanos
3. Espacios y reservas
4. Agenda de eventos
5. Peregrinaciones
6. Noticias
7. Grupos aleatorios

## Reglas de negocio

- Una reserva de lugar no puede solaparse con otra en el mismo lugar: cualquier solape (inicio dentro, fin dentro, envolvente).
- Generar grupos aleatorios de una comunidad: entre 2 y 20 grupos, reparto lo más uniforme posible; si hay más grupos que miembros, quedan grupos vacíos. Son efímeros: no se persisten.
- El moderador solo opera sobre su comunidad.
- El administrador elige comunidad antes de operar.
- Borrar es definitivo y pide confirmación.
- Cada comunidad tiene un responsable obligatorio.
- Una comunidad no puede quedarse sin ningún moderador (por borrado, cambio de rol o cambio de comunidad).
- El último administrador no puede borrarse ni perder el rol.

## Casos de uso (resumen)

- Acceso: iniciar y cerrar sesión; el administrador o el moderador dan de alta, editan y borran usuarios.
- Comunidades y lugares: el administrador gestiona comunidades y lugares; el registrado consulta el directorio y edita sus datos de contacto.
- Agenda: el moderador y el administrador gestionan eventos (Eucaristías, Palabras, Convivencias, Pasos) con reserva de lugar; el registrado los consulta.
- Peregrinaciones: consulta por el registrado; gestión por el administrador.
- Noticias: consulta pública; gestión por el moderador y el administrador.
- Grupos: el registrado genera grupos aleatorios de su comunidad.

## Fuera de alcance y futuro

- Rol vocal: los sacerdotes publican su disponibilidad semanal.
- Notificaciones por email y push al crear, cambiar o borrar eventos.
- Datos personales: RGPD (minimizar, consentimiento, borrado).
