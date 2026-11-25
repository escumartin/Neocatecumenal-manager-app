export type BookingStatusName =
  'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

function formatDate(date: string): string {
  const [year, month, day] = date.split('-');
  return `${day}/${month}/${year}`;
}

type OccupiedSlotLike = {
  startTime: string;
  endTime: string;
  status: BookingStatusName;
  ownCommunity: boolean;
};

type ConflictLike = { date: string; startTime: string; endTime: string };

function slotRange(slot: { startTime: string; endTime: string }): string {
  return `De ${slot.startTime} a ${slot.endTime}`;
}

export const bookingsText = {
  forbidden:
    'Solo los moderadores y los administradores pueden solicitar salones.',
  reasonWarning: 'No incluyas datos personales en el motivo.',
  status: {
    PENDING: 'Pendiente',
    APPROVED: 'Aprobada',
    REJECTED: 'Rechazada',
    CANCELLED: 'Cancelada',
  } as Record<BookingStatusName, string>,
  occupancy: {
    title: 'Ocupación de salones',
    date: 'Día',
    choose: 'Elige un salón y un día para ver sus franjas ocupadas.',
    chooseCommunity: 'Elige una comunidad para ver la ocupación.',
    loading: 'Cargando ocupación…',
    loadError: 'No hemos podido cargar la ocupación.',
    retry: 'Reintentar',
    refresh: 'Actualizar ocupación',
    empty: 'No hay franjas ocupadas ese día.',
    listLabel: 'Franjas ocupadas',
    ownCommunity: 'Tu comunidad',
    otherCommunity: 'Otra comunidad',
    slotText: slotRange,
    slotLabel(slot: OccupiedSlotLike) {
      const community = slot.ownCommunity ? 'tu comunidad' : 'otra comunidad';
      return `${slotRange(slot)}, ${bookingsText.status[slot.status]}, ${community}`;
    },
  },
  occupied: {
    withDates(conflicts: ConflictLike[]) {
      const list = conflicts
        .map(
          (conflict) =>
            `${formatDate(conflict.date)}, de ${conflict.startTime} a ${conflict.endTime}`,
        )
        .join('; ');
      return `El salón ya está ocupado en: ${list}. Elige otra franja.`;
    },
    withoutDates:
      'Otra solicitud ha ocupado esa franja hace un momento. Actualiza la ocupación y elige otra franja.',
  },
  request: {
    title: 'Solicitar un salón',
    room: 'Salón',
    loadingRooms: 'Cargando salones…',
    roomsError: 'No hemos podido cargar los salones.',
    retry: 'Reintentar',
    noRooms: 'No hay salones disponibles.',
    when: 'Cuándo',
    date: 'Fecha',
    startTime: 'Hora de inicio',
    endTime: 'Hora de fin',
    reason: 'Motivo',
    submit: 'Enviar solicitud',
    submitting: 'Enviando…',
    sent: 'Solicitud enviada. Queda pendiente de aprobación.',
  },
  recurrence: {
    toggle: 'Repetir cada semana',
    until: 'Repetir hasta',
    hint: 'El mismo día de la semana y la misma franja, como máximo hasta un año después de la primera fecha. Si alguna fecha está ocupada, no se registra ninguna.',
    fieldErrors: {
      REQUIRED: 'Indica hasta qué fecha se repite la solicitud.',
      INVALID_FORMAT: 'Revisa la fecha final: usa AAAA-MM-DD.',
      RECURRENCE_TOO_LONG:
        'La repetición no puede pasar de un año desde la primera fecha.',
      RECURRENCE_UNTIL_BEFORE_DATE:
        'La fecha final no puede ser anterior a la primera fecha.',
    } as Record<string, string>,
  },
  list: {
    title: 'Mis solicitudes',
    loading: 'Cargando solicitudes…',
    loadError: 'No hemos podido cargar las solicitudes.',
    retry: 'Reintentar',
    empty: 'Todavía no hay solicitudes.',
    newRequest: 'Solicitar un salón',
    unknownRoom: 'Salón dado de baja',
    roomLoading: 'Cargando salón…',
    roomUnavailable: 'Nombre no disponible',
    when(request: { date: string; startTime: string; endTime: string }) {
      return `${formatDate(request.date)}, de ${request.startTime} a ${request.endTime}`;
    },
  },
  rectify: {
    actionFor(roomName: string) {
      return `Corregir el motivo de la solicitud de ${roomName}`;
    },
    action: 'Corregir motivo',
    reason: 'Nuevo motivo',
    save: 'Guardar motivo',
    saving: 'Guardando…',
    cancel: 'Cancelar',
  },
  resolve: {
    title: 'Solicitudes pendientes',
    loading: 'Cargando solicitudes pendientes…',
    loadError: 'No hemos podido cargar las solicitudes pendientes.',
    retry: 'Reintentar',
    empty: 'No hay solicitudes pendientes.',
    forbidden:
      'Solo los administradores pueden aprobar o rechazar solicitudes.',
    community(number: number) {
      return `Comunidad ${number}`;
    },
    approve: 'Aprobar',
    approveFor(roomName: string) {
      return `Aprobar la solicitud de ${roomName}`;
    },
    reject: 'Rechazar',
    rejectFor(roomName: string) {
      return `Rechazar la solicitud de ${roomName}`;
    },
    rejectionReason: 'Motivo del rechazo (opcional)',
    confirmReject: 'Confirmar rechazo',
    cancelReject: 'No rechazar',
    working: 'Guardando…',
    alreadyResolved:
      'Otro administrador ya ha resuelto esta solicitud. Hemos actualizado la lista.',
    alreadyStarted:
      'La franja ya ha empezado: ya no se puede aprobar, solo rechazar.',
  },
  cancel: {
    actionFor(roomName: string) {
      return `Cancelar la solicitud de ${roomName}`;
    },
    action: 'Cancelar',
    title(roomName: string) {
      return `¿Cancelar la solicitud de ${roomName}?`;
    },
    warning:
      'La cancelación es definitiva y la franja quedará libre para otras solicitudes.',
    wholeSeries:
      'Toda la serie (las ocurrencias que ya han empezado se conservan)',
    oneOccurrence(occurrence: {
      date: string;
      startTime: string;
      endTime: string;
    }) {
      return `Solo el ${formatDate(occurrence.date)}, de ${occurrence.startTime} a ${occurrence.endTime}`;
    },
    choice: 'Qué quieres cancelar',
    confirm: 'Confirmar cancelación',
    keep: 'No cancelar',
    cancelling: 'Cancelando…',
  },
  rejectionReasonLine(reason: string) {
    return `Motivo del rechazo: ${reason}`;
  },
  reasonInvalidFormat: 'El motivo contiene caracteres no válidos.',
  fieldErrors: {
    START_NOT_IN_FUTURE:
      'La franja ya ha empezado. Elige una fecha y una hora de inicio futuras.',
    END_NOT_AFTER_START: 'La hora de fin debe ser posterior a la de inicio.',
    COMMUNITY_REQUIRED: 'Elige la comunidad para la que solicitas el salón.',
    COMMUNITY_NOT_FOUND: 'La comunidad elegida no existe.',
    ROOM_REQUIRED: 'Elige un salón.',
    REQUIRED: 'Escribe el motivo de la solicitud.',
    TOO_LONG: 'El motivo es demasiado largo (máximo 500 caracteres).',
    INVALID_FORMAT: 'Revisa la fecha y las horas: usa AAAA-MM-DD y HH:MM.',
  } as Record<string, string>,
  errors: {
    ROOM_INACTIVE: 'Este salón ya está dado de baja.',
    BOOKING_REQUEST_ALREADY_RESOLVED:
      'La solicitud ya está resuelta y su motivo no se puede cambiar.',
    BOOKING_ALREADY_STARTED: 'La franja ya ha empezado y no se puede cancelar.',
    BOOKING_REQUEST_NOT_ACTIVE:
      'La solicitud ya no está activa: está rechazada o cancelada.',
    FORBIDDEN: 'No tienes permiso para hacer esto.',
    NOT_FOUND: 'El salón o la solicitud ya no existen.',
    VALIDATION_FAILED: 'Revisa los datos de la solicitud.',
    UNKNOWN:
      'No hemos podido completar la operación. Inténtalo de nuevo en unos minutos.',
  } as Record<string, string>,
};

const RECURRENCE_FIELD = 'recurrence.until';
const REASON_FIELD = 'reason';

export function messageForBookingFailure(failure: {
  code?: string;
  fieldErrors?: { field?: string; code: string }[];
  conflicts?: ConflictLike[];
}): string {
  if (failure.code === 'ROOM_OCCUPIED') {
    return failure.conflicts && failure.conflicts.length > 0
      ? bookingsText.occupied.withDates(failure.conflicts)
      : bookingsText.occupied.withoutDates;
  }
  const first = failure.fieldErrors?.[0];
  if (
    first?.field === RECURRENCE_FIELD &&
    bookingsText.recurrence.fieldErrors[first.code]
  ) {
    return bookingsText.recurrence.fieldErrors[first.code];
  }
  if (first?.field === REASON_FIELD && first.code === 'INVALID_FORMAT') {
    return bookingsText.reasonInvalidFormat;
  }
  const fieldCode = first?.code;
  if (fieldCode && bookingsText.fieldErrors[fieldCode]) {
    return bookingsText.fieldErrors[fieldCode];
  }
  return (
    bookingsText.errors[failure.code ?? 'UNKNOWN'] ??
    bookingsText.errors.UNKNOWN
  );
}

export function messageForResolutionFailure(failure: {
  code?: string;
  fieldErrors?: { field?: string; code: string }[];
  conflicts?: ConflictLike[];
}): string {
  if (failure.fieldErrors?.[0]?.code === 'START_NOT_IN_FUTURE') {
    return bookingsText.resolve.alreadyStarted;
  }
  if (failure.code === 'BOOKING_REQUEST_ALREADY_RESOLVED') {
    return bookingsText.resolve.alreadyResolved;
  }
  return messageForBookingFailure(failure);
}
