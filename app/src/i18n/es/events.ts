import { bookingsText, type BookingStatusName } from './bookings';

type ConflictLike = { date: string; startTime: string; endTime: string };

function formatDate(date: string): string {
  const [year, month, day] = date.split('-');
  return `${day}/${month}/${year}`;
}

export const eventsText = {
  forbidden: 'Solo los moderadores y los administradores pueden dar de alta eventos.',
  types: {
    WORD: 'Palabra',
    CONVIVENCE: 'Convivencia',
  } as Record<string, string>,
  create: {
    title: 'Dar de alta un evento',
    type: 'Tipo de evento',
    eventTitle: 'Título',
    titleWarning: 'No incluyas datos personales en el título.',
    room: 'Salón',
    when: 'Cuándo',
    date: 'Fecha',
    startTime: 'Hora de inicio',
    endTime: 'Hora de fin',
    loadingRooms: 'Cargando salones…',
    roomsError: 'No hemos podido cargar los salones.',
    retry: 'Reintentar',
    noRooms: 'No hay salones disponibles.',
    submit: 'Dar de alta el evento',
    submitting: 'Guardando…',
    sent(status: BookingStatusName) {
      return `Evento registrado. La solicitud de su salón está: ${bookingsText.status[status]}.`;
    },
  },
  detail: {
    title: 'Evento',
    loading: 'Cargando evento…',
    loadError: 'No hemos podido cargar el evento.',
    retry: 'Reintentar',
    notFound: 'El evento ya no existe.',
    forbidden: 'No tienes permiso para ver este evento.',
    type: 'Tipo',
    room: 'Salón',
    unknownRoom: 'Salón dado de baja',
    roomLoading: 'Cargando salón…',
    roomUnavailable: 'Nombre no disponible',
    whenLabel: 'Cuándo',
    statusLabel: 'Estado de la solicitud del salón',
    when(event: { date: string; startTime: string; endTime: string }) {
      return `${formatDate(event.date)}, de ${event.startTime} a ${event.endTime}`;
    },
  },
  fieldErrors: {
    TITLE_REQUIRED: 'Escribe el título del evento.',
    TITLE_TOO_LONG: 'El título es demasiado largo (máximo 120 caracteres).',
    TITLE_INVALID: 'El título contiene caracteres no válidos.',
    TYPE_REQUIRED: 'Elige el tipo de evento.',
    TYPE_INVALID: 'El tipo de evento no es válido.',
    ROOM_REQUIRED: 'Elige un salón.',
    COMMUNITY_REQUIRED: 'Elige la comunidad para la que das de alta el evento.',
    COMMUNITY_NOT_FOUND: 'La comunidad elegida no existe.',
    START_NOT_IN_FUTURE: 'La franja ya ha empezado. Elige una fecha y una hora de inicio futuras.',
    END_NOT_AFTER_START: 'La hora de fin debe ser posterior a la de inicio.',
    INVALID_FORMAT: 'Revisa la fecha y las horas: usa AAAA-MM-DD y HH:MM.',
  } as Record<string, string>,
  errors: {
    ROOM_INACTIVE: 'Este salón ya está dado de baja.',
    FORBIDDEN: 'No tienes permiso para hacer esto.',
    NOT_FOUND: 'El salón o el evento ya no existen.',
    VALIDATION_FAILED: 'Revisa los datos del evento.',
    UNKNOWN: 'No hemos podido completar la operación. Inténtalo de nuevo en unos minutos.',
  } as Record<string, string>,
};

const FIELD_MESSAGES: Record<string, Record<string, string>> = {
  title: { REQUIRED: 'TITLE_REQUIRED', TOO_LONG: 'TITLE_TOO_LONG', INVALID_FORMAT: 'TITLE_INVALID' },
  type: { INVALID_FORMAT: 'TYPE_INVALID', REQUIRED: 'TYPE_REQUIRED' },
};

export function messageForEventFailure(failure: {
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
  if (first) {
    const key = (first.field && FIELD_MESSAGES[first.field]?.[first.code]) || first.code;
    if (eventsText.fieldErrors[key]) {
      return eventsText.fieldErrors[key];
    }
  }
  return eventsText.errors[failure.code ?? 'UNKNOWN'] ?? eventsText.errors.UNKNOWN;
}
