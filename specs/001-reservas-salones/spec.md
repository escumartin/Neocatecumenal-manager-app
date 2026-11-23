# Feature Specification: Reservas de salones

**Feature Branch**: `001-reservas-salones`

**Created**: 2026-11-23

**Status**: Draft

**Input**: User description: "Reservas de salones: los responsables de comunidad solicitan el uso de un salón parroquial para una fecha y franja; el sistema evita solapamientos y quien gestiona los salones aprueba o rechaza"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Solicitar un salón para una fecha y franja (Priority: P1)

El responsable de una comunidad (moderador) elige un salón parroquial, una fecha y una franja horaria, indica el motivo y envía la solicitud. La solicitud queda pendiente hasta que quien gestiona los salones (administrador, ostiario jefe) la resuelva. Sustituye al reparto actual de salones por WhatsApp.

**Why this priority**: es el flujo mínimo que sustituye al reparto manual; sin solicitud no hay nada que aprobar ni solapamientos que evitar.

**Independent Test**: un moderador crea una solicitud para un salón libre y la ve en su lista como pendiente, con salón, fecha, franja y motivo.

**Acceptance Scenarios**:

1. **Given** un moderador identificado y un salón sin reservas en la franja elegida, **When** envía la solicitud con salón, fecha, hora de inicio, hora de fin y motivo, **Then** la solicitud se registra como pendiente asociada a su comunidad.
2. **Given** un moderador, **When** envía una solicitud con la hora de fin igual o anterior a la de inicio, o con una fecha pasada, **Then** el sistema la rechaza con un mensaje claro y no registra nada.
3. **Given** un moderador de la comunidad A, **When** consulta sus solicitudes, **Then** solo ve las de la comunidad A.

---

### User Story 2 - Evitar solapamientos de salón (Priority: P1)

El sistema impide que dos reservas ocupen el mismo salón en franjas que se solapan: inicio dentro de otra, fin dentro de otra o una que envuelve a otra. Al solicitar, el moderador ve de antemano qué franjas del salón están ocupadas.

**Why this priority**: es el motivo del proyecto (evitar que dos grupos coincidan en un salón y franja) y la regla de negocio central del dominio.

**Independent Test**: con una reserva activa (pendiente o aprobada) de 18:00 a 20:00, cualquier solicitud del mismo salón y día que comparta algún minuto con esa franja es rechazada; una que empiece a las 20:00 se acepta.

**Acceptance Scenarios**:

1. **Given** una reserva activa de un salón de 18:00 a 20:00, **When** se solicita el mismo salón de 19:00 a 21:00, de 17:00 a 18:30 o de 17:00 a 21:00, **Then** el sistema rechaza la solicitud indicando que el salón está ocupado.
2. **Given** la misma reserva activa, **When** se solicita el mismo salón de 20:00 a 21:00 o de 16:00 a 18:00, **Then** la solicitud se acepta.
3. **Given** la misma reserva activa, **When** se solicita otro salón en la misma franja, **Then** la solicitud se acepta.
4. **Given** dos moderadores que solicitan la misma franja y salón, **When** ambos envían, **Then** solo se registra la solicitud que llegó antes y la otra se rechaza por ocupado.
5. **Given** una solicitud pendiente que ocupa una franja, **When** se rechaza o se cancela, **Then** la franja queda libre para nuevas solicitudes.

---

### User Story 3 - Aprobar o rechazar solicitudes (Priority: P2)

El administrador ve las solicitudes pendientes de todas las comunidades, y aprueba o rechaza cada una; al rechazar puede indicar un motivo. El solicitante ve el resultado en su lista.

**Why this priority**: cierra el ciclo y da validez a la reserva, pero depende de que existan solicitudes.

**Independent Test**: el administrador aprueba una solicitud pendiente y rechaza otra con motivo; ambos moderadores ven el nuevo estado.

**Acceptance Scenarios**:

1. **Given** una solicitud pendiente, **When** el administrador la aprueba, **Then** pasa a aprobada y sigue ocupando el salón en esa franja.
2. **Given** una solicitud pendiente, **When** el administrador la rechaza con un motivo, **Then** pasa a rechazada, el motivo es visible para el solicitante y el salón queda libre.
3. **Given** un usuario que no es administrador, **When** intenta aprobar o rechazar, **Then** el sistema lo impide.

---

### User Story 4 - Consultar disponibilidad y cancelar (Priority: P3)

El moderador consulta la ocupación de los salones por día y cancela las solicitudes o reservas de su comunidad. El administrador puede cancelar cualquier reserva. Cancelar es definitivo y pide confirmación.

**Why this priority**: mejora la experiencia y evita reservas fantasma, pero el flujo funciona sin ella.

**Independent Test**: un moderador cancela una reserva aprobada de su comunidad tras confirmar y la franja vuelve a quedar libre.

**Acceptance Scenarios**:

1. **Given** una reserva de la comunidad del moderador, **When** la cancela y confirma, **Then** deja de estar activa y la franja queda libre.
2. **Given** una reserva de otra comunidad, **When** un moderador intenta cancelarla, **Then** el sistema lo impide.
3. **Given** un día con reservas, **When** un moderador consulta la disponibilidad de un salón, **Then** ve las franjas ocupadas, sin motivos ni datos de personas.

---

### User Story 5 - Reservas periódicas y ligadas a eventos (Priority: P2)

El moderador puede pedir una reserva que se repite (por ejemplo, una Palabra semanal hasta una fecha fin). Cada ocurrencia se comprueba contra solapes; si alguna choca, se rechaza la solicitud entera y se informa de las fechas en conflicto. Al dar de alta un evento (Palabra, Convivencia...) con un salón como lugar, se crea su solicitud de reserva. Se puede cancelar una ocurrencia o toda la serie.

**Why this priority**: las Palabras son semanales; sin repetición el moderador tendría que repetir la solicitud cada semana.

**Independent Test**: una solicitud semanal de 8 semanas con una ocurrencia chocando se rechaza y lista esa fecha; sin choque se registran 8 ocurrencias y se cancela una sin afectar a las demás.

**Acceptance Scenarios**:

1. **Given** una solicitud semanal con fecha fin sin choques, **When** se envía, **Then** se registran todas las ocurrencias como una serie pendiente.
2. **Given** una solicitud periódica en la que alguna ocurrencia solapa con una reserva activa, **When** se envía, **Then** se rechaza entera y se muestran las fechas en conflicto.
3. **Given** una serie aprobada, **When** el moderador cancela una ocurrencia, **Then** solo esa franja queda libre y las demás siguen activas.
4. **Given** una serie aprobada, **When** el moderador cancela la serie y confirma, **Then** todas las ocurrencias futuras se cancelan.
5. **Given** un moderador que da de alta un evento con un salón como lugar, **When** guarda el evento, **Then** se crea la solicitud de reserva de ese salón para la fecha y franja del evento, y si el salón está ocupado el evento no se guarda.

---

### Edge Cases

- Franjas contiguas (una termina cuando otra empieza) no se consideran solapadas.
- Franjas que cruzan la medianoche: no se admiten; una franja pertenece a un único día.
- Un salón con reservas futuras que el administrador quiere eliminar: debe resolver antes esas reservas.
- Dos administradores resuelven la misma solicitud a la vez: solo cuenta la primera resolución; la segunda recibe un aviso.
- Una serie sin fecha fin o con demasiadas ocurrencias: se exige fecha fin dentro de un límite razonable (un año).
- Un usuario que deja de ser moderador conserva el historial de sus solicitudes, pero no puede crear ni cancelar nuevas.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Los moderadores MUST poder solicitar un salón indicando salón, fecha, hora de inicio, hora de fin y motivo; la solicitud queda asociada a su comunidad.
- **FR-002**: El sistema MUST validar que la hora de fin es posterior a la de inicio, que ambas caen en el mismo día y que la fecha no es pasada.
- **FR-003**: El sistema MUST rechazar cualquier solicitud que solape en el mismo salón con una reserva activa, pendiente o aprobada (inicio dentro, fin dentro o envolvente); las franjas contiguas no se solapan.
- **FR-004**: Una solicitud MUST tener un estado entre pendiente, aprobada, rechazada y cancelada, con las transiciones pendiente a aprobada, pendiente a rechazada y pendiente o aprobada a cancelada.
- **FR-005**: Los administradores MUST poder ver las solicitudes pendientes de todas las comunidades y aprobarlas o rechazarlas, con motivo opcional al rechazar.
- **FR-006**: Los moderadores MUST ver solo las solicitudes y reservas de su comunidad; los administradores, las de cualquier comunidad.
- **FR-007**: Los moderadores MUST poder cancelar las solicitudes y reservas de su comunidad, y los administradores cualquiera, siempre con confirmación previa.
- **FR-008**: Los moderadores MUST poder consultar las franjas ocupadas de un salón por día antes de solicitar, sin ver motivos ni datos de personas de otras comunidades.
- **FR-009**: Solo los administradores MUST poder aprobar o rechazar; el resto de roles, no.
- **FR-010**: El sistema MUST conservar el historial de estados de cada solicitud (quién y cuándo la creó, resolvió o canceló).
- **FR-011**: Las solicitudes MUST guardar solo los datos necesarios: salón, comunidad, solicitante, fecha, franja, motivo, estado y motivo de rechazo; nunca datos personales adicionales ni en registros técnicos.
- **FR-012**: Las pantallas MUST cumplir los requisitos de accesibilidad y uso móvil del proyecto (texto legible, objetivos táctiles amplios, contraste AA, etiquetas en todos los controles).
- **FR-013**: Una solicitud pendiente MUST bloquear la franja para otras solicitudes; gana quien solicita antes. Al rechazarse o cancelarse, la franja MUST quedar libre.
- **FR-014**: Al dar de alta un evento con un salón como lugar, el sistema MUST crear la solicitud de reserva de ese salón para la fecha y franja del evento; si el salón está ocupado, el evento no se guarda.
- **FR-015**: Las solicitudes MUST admitir una regla de repetición (por ejemplo, semanal con fecha fin); cada ocurrencia MUST comprobarse contra solapes y, si alguna choca, se rechaza la solicitud entera informando de las fechas en conflicto.
- **FR-016**: Los usuarios con permiso MUST poder cancelar una sola ocurrencia o toda la serie, con confirmación previa.

### Key Entities *(include if feature involves data)*

- **Reserva (solicitud)**: ocupación de un salón en una fecha y franja por una comunidad; atributos: salón, comunidad, solicitante, fecha, hora de inicio, hora de fin, motivo, estado, motivo de rechazo, historial de estados, evento asociado (opcional), serie (opcional).
- **Serie de reservas**: conjunto de ocurrencias generadas por una regla de repetición (frecuencia y fecha fin); se aprueba o rechaza como un todo y admite cancelar ocurrencias sueltas.
- **Salón (Lugar)**: salón parroquial reservable; lo gestiona el administrador. Hotel y restaurante, también «Lugar» en el dominio, quedan fuera.
- **Comunidad**: propietaria de la solicitud; su responsable (moderador) es quien solicita.
- **Usuario**: moderador que solicita o administrador que resuelve.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un moderador completa una solicitud de salón en menos de 2 minutos desde el móvil.
- **SC-002**: 0 casos de dos reservas aprobadas solapadas en el mismo salón, en el 100 % de las pruebas de solape (inicio dentro, fin dentro, envolvente, contiguas).
- **SC-003**: El administrador resuelve una solicitud pendiente en menos de 30 segundos.
- **SC-004**: El 90 % de los moderadores completa su primera solicitud sin ayuda.
- **SC-005**: Tras la puesta en marcha, el reparto de salones por WhatsApp deja de ser necesario para las comunidades que usan la app.

## Assumptions

- «Responsable de comunidad» equivale al rol moderador y «quien gestiona los salones» al administrador (ostiario jefe), según `dominio.md`.
- El dominio no describe un flujo de solicitud y aprobación (modela la reserva como ocupación de un lugar y el reparto lo hace el ostiario jefe); esta feature lo añade conforme a la descripción, y la regla de solape del dominio se mantiene sin cambios.
- La franja se define con hora de inicio y fin libres dentro de un día, no con tramos fijos, según la regla de solape del dominio (inicio dentro, fin dentro, envolvente).
- Solo se reservan salones parroquiales; hoteles y restaurantes (Pasos y Peregrinaciones) quedan fuera.
- Los salones ya existen, dados de alta por el administrador; esta feature no los gestiona.
- Los usuarios ya se autentican con el sistema de identidad existente; no hay autorregistro.
- Las notificaciones por email o push están fuera de alcance (futuro, según el dominio); el solicitante ve el resultado dentro de la app.
- Zona horaria única de la parroquia (Murcia).
- Un salón puede tener cualquier número de reservas por día mientras no se solapen.
- Dependencia: el alta de eventos pertenece al contexto de Agenda de eventos (`dominio.md`, casos de uso de Agenda: eventos con reserva de lugar); esta feature expone la reserva y la Agenda la solicita al crear un evento.
- Conflicto en una serie: se rechaza la solicitud entera y se informan las fechas en conflicto; no hay aprobación parcial.
- La regla de repetición de v1 es semanal con fecha fin; otras frecuencias quedan para después.
- Aprobar o rechazar una serie se hace como un todo; cancelar admite una ocurrencia o toda la serie.
