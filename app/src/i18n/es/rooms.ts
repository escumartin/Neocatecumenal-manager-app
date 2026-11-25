export const roomsText = {
  title: 'Salones',
  loading: 'Cargando salones…',
  loadError: 'No hemos podido cargar los salones.',
  retry: 'Reintentar',
  empty: 'Todavía no hay salones. Añade el primero.',
  forbidden: 'Solo los administradores pueden gestionar los salones.',
  add: {
    title: 'Nuevo salón',
    name: 'Nombre del salón',
    submit: 'Añadir salón',
    submitting: 'Añadiendo…',
  },
  rename: {
    action: 'Cambiar nombre',
    actionFor(name: string) {
      return `Cambiar el nombre de ${name}`;
    },
    name: 'Nuevo nombre',
    save: 'Guardar nombre',
    saving: 'Guardando…',
    cancel: 'Cancelar',
  },
  deactivate: {
    action: 'Dar de baja',
    actionFor(name: string) {
      return `Dar de baja ${name}`;
    },
    title(name: string) {
      return `¿Dar de baja ${name}?`;
    },
    warning:
      'La baja es definitiva y no se puede deshacer. El salón dejará de poder solicitarse y su historial se conserva.',
    confirm: 'Dar de baja definitivamente',
    cancel: 'Cancelar',
    deactivating: 'Dando de baja…',
  },
  activeBookings(count: number) {
    return count === 1
      ? 'No se puede dar de baja: el salón tiene 1 reserva pendiente o aprobada por venir.'
      : `No se puede dar de baja: el salón tiene ${count} reservas pendientes o aprobadas por venir.`;
  },
  fieldErrors: {
    REQUIRED: 'Escribe un nombre para el salón.',
    TOO_LONG: 'El nombre es demasiado largo (máximo 80 caracteres).',
  } as Record<string, string>,
  errors: {
    ROOM_NAME_TAKEN: 'Ya existe un salón activo con ese nombre.',
    ROOM_INACTIVE: 'Este salón ya está dado de baja.',
    CONCURRENT_MODIFICATION:
      'El salón ha cambiado mientras lo editabas; recarga y vuelve a intentarlo.',
    FORBIDDEN: 'No tienes permiso para hacer esto.',
    NOT_FOUND: 'El salón ya no existe.',
    VALIDATION_FAILED: 'Revisa el nombre del salón.',
    UNKNOWN:
      'No hemos podido completar la operación. Inténtalo de nuevo en unos minutos.',
  } as Record<string, string>,
};

export function messageForRoomsFailure(failure: {
  code?: string;
  fieldErrors?: { code: string }[];
  activeBookings?: number;
}): string {
  if (
    failure.code === 'ROOM_HAS_ACTIVE_BOOKINGS' &&
    failure.activeBookings !== undefined
  ) {
    return roomsText.activeBookings(failure.activeBookings);
  }
  const fieldCode = failure.fieldErrors?.[0]?.code;
  if (fieldCode && roomsText.fieldErrors[fieldCode]) {
    return roomsText.fieldErrors[fieldCode];
  }
  return (
    roomsText.errors[failure.code ?? 'UNKNOWN'] ?? roomsText.errors.UNKNOWN
  );
}
