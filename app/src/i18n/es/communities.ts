export const communitiesText = {
  pickerTitle: 'Comunidad',
  loading: 'Cargando comunidades…',
  loadError: 'No hemos podido cargar las comunidades.',
  retry: 'Reintentar',
  option(number: number) {
    return `Comunidad ${number}`;
  },
  list: {
    loading: 'Cargando comunidades…',
    loadError: 'No hemos podido cargar las comunidades.',
    empty: 'Todavía no hay comunidades.',
    create: 'Añadir comunidad',
    noResponsible: 'Sin responsable',
    deactivated: 'Desactivada',
  },
  file: {
    loading: 'Cargando la comunidad…',
    loadError: 'No hemos podido cargar la comunidad.',
    statusDeactivated: 'Esta comunidad está desactivada: no admite altas nuevas.',
  },
  form: {
    createTitle: 'Nueva comunidad',
    editTitle: 'Comunidad',
    number: 'Número',
    numberHint: 'Obligatorio. Solo cifras, por ejemplo 12.',
    name: 'Nombre (opcional)',
    nameHint: 'Referencia de la parroquia, hasta 100 caracteres.',
    create: 'Crear comunidad',
    save: 'Guardar cambios',
    saving: 'Guardando…',
  },
  actions: {
    deactivate: {
      row: 'Desactivar comunidad',
      title(number: number) {
        return `¿Desactivar la comunidad ${number}?`;
      },
      warning:
        'No admitirá altas nuevas de hermanos ni de usuarios. Si tiene moderadores o administradores, seguirán pudiendo entrar. Sus datos se conservan y puedes reactivarla cuando quieras.',
      confirm: 'Desactivar ahora',
      cancel: 'Cancelar',
      working: 'Desactivando…',
    },
    reactivate: {
      row: 'Reactivar comunidad',
    },
    delete: {
      row: 'Borrar comunidad',
      title(number: number) {
        return `¿Borrar la comunidad ${number}?`;
      },
      warning:
        'El borrado es definitivo y no se puede deshacer. Solo se puede borrar una comunidad sin responsable, hermanos, usuarios, reservas ni eventos.',
      confirm: 'Borrar definitivamente',
      cancel: 'Cancelar',
      working: 'Borrando…',
    },
  },
  responsible: {
    designateRow: 'Designar responsable',
    changeRow: 'Cambiar responsable',
    title: 'Responsable de la comunidad',
    choose: 'Hermano responsable',
    hint: 'Elige entre los hermanos de esta comunidad.',
    save: 'Designar responsable',
    saving: 'Guardando…',
    loading: 'Cargando los hermanos…',
    loadError: 'No hemos podido cargar los hermanos de la comunidad.',
    noMembers: 'Esta comunidad aún no tiene hermanos. Da de alta un hermano para poder designarlo responsable.',
    notModerator:
      'Aviso: el responsable no tiene usuario de moderador. Puedes mantenerlo, pero conviene que tenga ese rol.',
  },
  nameInvalid: 'No puede contener caracteres de control ni saltos de línea.',
  fieldErrors: {
    MEMBER_NOT_IN_COMMUNITY: 'Ese hermano no pertenece a esta comunidad.',
    REQUIRED: 'Escribe el número de la comunidad.',
    INVALID_FORMAT: 'El número debe ser una cifra mayor que 0.',
    TOO_LONG: 'El nombre es demasiado largo (máximo 100 caracteres).',
    COMMUNITY_NUMBER_TAKEN: 'Ya existe una comunidad con ese número.',
  } as Record<string, string>,
  errors: {
    COMMUNITY_HAS_RESPONSIBLE: 'No se puede borrar: la comunidad tiene responsable. Si ya no se usa, desactívala.',
    COMMUNITY_NOT_EMPTY: 'No se puede borrar: la comunidad tiene hermanos o usuarios. Si ya no se usa, desactívala.',
    COMMUNITY_HAS_HISTORY: 'No se puede borrar: la comunidad tiene reservas o eventos. Si ya no se usa, desactívala.',
    COMMUNITY_HAS_PRIVILEGED_USERS: 'La comunidad tiene moderadores o administradores. Confirma para desactivarla.',
    COMMUNITY_DEACTIVATED: 'La comunidad está desactivada.',
    RESPONSIBLE_REQUIRED: 'La comunidad no puede quedarse sin responsable: designa antes a otro hermano.',
    CONCURRENT_MODIFICATION: 'Los datos han cambiado mientras los editabas. Vuelve a abrir la comunidad.',
    FORBIDDEN: 'No tienes permiso para hacer esto.',
    NOT_FOUND: 'La comunidad ya no existe.',
    VALIDATION_FAILED: 'Revisa los datos introducidos.',
    UNKNOWN: 'No hemos podido completar la operación. Inténtalo de nuevo en unos minutos.',
  } as Record<string, string>,
};

export function messageForCommunitiesFailure(failure: {
  code?: string;
  fieldErrors?: { field?: string; code: string }[];
}): string {
  const first = failure.fieldErrors?.[0];
  if (first?.field === 'name' && first.code === 'INVALID_FORMAT') {
    return communitiesText.nameInvalid;
  }
  if (first && communitiesText.fieldErrors[first.code]) {
    return communitiesText.fieldErrors[first.code];
  }
  return communitiesText.errors[failure.code ?? 'UNKNOWN'] ?? communitiesText.errors.UNKNOWN;
}
