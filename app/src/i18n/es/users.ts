export const usersText = {
  title: 'Usuarios',
  loading: 'Cargando usuarios…',
  loadError: 'No hemos podido cargar los usuarios.',
  loadOneError: 'No hemos podido cargar el usuario.',
  retry: 'Reintentar',
  empty: 'Todavía no hay usuarios en esta comunidad.',
  create: 'Añadir usuario',
  accessId(accessId: number) {
    return `Identificador ${accessId}`;
  },
  editFor(name: string) {
    return `Editar a ${name}`;
  },
  deleteFor(name: string) {
    return `Borrar a ${name}`;
  },
  edit: 'Editar',
  remove: 'Borrar',
  roles: {
    REGISTERED: 'Registrado',
    MODERATOR: 'Moderador',
    ADMINISTRATOR: 'Administrador',
  },
  delete: {
    title(name: string) {
      return `¿Borrar a ${name}?`;
    },
    warning: 'El borrado es definitivo y no se puede deshacer. Esta persona dejará de poder entrar.',
    confirm: 'Borrar definitivamente',
    cancel: 'Cancelar',
    deleting: 'Borrando…',
  },
  form: {
    createTitle: 'Nuevo usuario',
    editTitle: 'Editar usuario',
    name: 'Nombre',
    role: 'Rol',
    create: 'Crear usuario',
    save: 'Guardar cambios',
    saving: 'Guardando…',
  },
  created: {
    title(name: string) {
      return `${name} ya tiene acceso`;
    },
    accessId: 'Identificador de acceso',
    password: 'Contraseña inicial',
    onlyOnce:
      'Apunta ahora la contraseña: solo se muestra esta vez. La persona deberá cambiarla en su primer acceso.',
    done: 'Hecho',
    copy: 'Copiar identificador y contraseña',
    copied: 'Copiado',
    copyFailed: 'No se ha podido copiar. Apúntalos a mano.',
  },
  reset: {
    action: 'Reasignar contraseña',
    confirmTitle(name: string) {
      return `¿Reasignar la contraseña de ${name}?`;
    },
    warning:
      'Se generará una contraseña inicial nueva y se cerrarán todas sus sesiones. La anterior dejará de valer y deberá cambiar la nueva al entrar.',
    confirm: 'Reasignar contraseña ahora',
    cancel: 'Cancelar',
    resetting: 'Reasignando…',
    title(name: string) {
      return `Nueva contraseña para ${name}`;
    },
    onlyOnce:
      'Apunta ahora la contraseña: solo se muestra esta vez. La persona deberá cambiarla al entrar.',
  },
  fieldErrors: {
    REQUIRED: 'Escribe un nombre.',
    TOO_LONG: 'El nombre es demasiado largo (máximo 100 caracteres).',
    INVALID_FORMAT: 'Revisa el formato de los datos.',
    COMMUNITY_REQUIRED: 'Elige una comunidad.',
    COMMUNITY_NOT_FOUND: 'La comunidad elegida no existe.',
  } as Record<string, string>,
  errors: {
    FORBIDDEN: 'No tienes permiso para hacer esto.',
    SELF_ACTION_FORBIDDEN: 'No puedes hacer esto con tu propia cuenta.',
    LAST_MODERATOR: 'No se puede: es el último moderador de su comunidad.',
    LAST_ADMINISTRATOR: 'No se puede: es el último administrador.',
    NOT_FOUND: 'El usuario ya no existe.',
    VALIDATION_FAILED: 'Revisa los datos introducidos.',
    UNKNOWN: 'No hemos podido completar la operación. Inténtalo de nuevo en unos minutos.',
  } as Record<string, string>,
};

export function messageForUsersFailure(failure: {
  code?: string;
  fieldErrors?: { code: string }[];
}): string {
  const fieldCode = failure.fieldErrors?.[0]?.code;
  if (fieldCode && usersText.fieldErrors[fieldCode]) {
    return usersText.fieldErrors[fieldCode];
  }
  return usersText.errors[failure.code ?? 'UNKNOWN'] ?? usersText.errors.UNKNOWN;
}
