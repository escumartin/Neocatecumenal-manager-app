import type { ContactFieldName } from '@/lib/members/contact-validation';

const originText = {
  title: 'Quién anotó tu consentimiento',
  deletedUser: 'usuario eliminado',
  channel: { VERBAL: 'De palabra', WRITTEN: 'Por escrito' } as Record<string, string>,
  role: { MODERATOR: 'moderador', ADMINISTRATOR: 'administrador' } as Record<string, string>,
  line: (channel: string, role: string, name: string | undefined) =>
    `${originText.channel[channel] ?? channel}, anotado por ${name ?? originText.deletedUser} (${
      originText.role[role] ?? role
    })`,
};

export const membersText = {
  retry: 'Reintentar',
  list: {
    loading: 'Cargando hermanos…',
    loadError: 'No hemos podido cargar los hermanos.',
    empty: 'Todavía no hay hermanos en esta comunidad.',
    create: 'Añadir hermano',
    chooseCommunity: 'Elige una comunidad para ver sus hermanos.',
    responsible: 'Responsable',
    deactivated: 'Esta comunidad está desactivada.',
  },
  directory: {
    loading: 'Cargando la comunidad…',
    loadError: 'No hemos podido cargar la comunidad.',
    empty: 'Todavía no hay hermanos en tu comunidad.',
    deactivated: 'Tu comunidad está desactivada. Habla con tu moderador.',
    chooseCommunity: 'Elige una comunidad para ver a sus hermanos.',
    searchLabel: 'Buscar hermano',
    searchHint: 'Escribe parte del nombre o de los apellidos.',
    noResults: 'Ningún hermano coincide con la búsqueda.',
    clearSearch: 'Borrar búsqueda',
    cardLoading: 'Cargando al hermano…',
    cardLoadError: 'No hemos podido cargar al hermano.',
    noContact: 'Este hermano no comparte datos de contacto.',
    contactTitle: 'Contacto',
    mobile: 'Móvil',
    landline: 'Fijo',
    email: 'Correo',
    actionCall: 'Llamar',
    actionText: 'Enviar mensaje',
    actionWrite: 'Escribir un correo',
    callMobile: (name: string) => `Llamar al móvil de ${name}`,
    textMobile: (name: string) => `Enviar un mensaje al móvil de ${name}`,
    callLandline: (name: string) => `Llamar al fijo de ${name}`,
    writeEmail: (name: string) => `Escribir un correo a ${name}`,
  },
  form: {
    createTitle: 'Nuevo hermano',
    editTitle: 'Hermano',
    loading: 'Cargando el hermano…',
    loadError: 'No hemos podido cargar el hermano.',
    givenName: 'Nombre',
    givenNameHint: 'Obligatorio.',
    surnames: 'Apellidos (opcional)',
    surnamesHint: 'Si no los escribes, se ordena por el nombre.',
    create: 'Dar de alta',
    save: 'Guardar cambios',
    saving: 'Guardando…',
    contactRow: 'Datos de contacto',
  },
  contact: {
    title: 'Datos de contacto',
    notice:
      'Aviso de datos: el móvil, el fijo y el correo solo se guardan si el hermano da su consentimiento para cada uno. Los verán los hermanos de su comunidad. Puede retirarlo cuando quiera y el dato se borra.',
    linkedNotice:
      'Este hermano tiene usuario y decide él mismo qué comparte. Aquí solo puedes corregir los datos que ya comparte.',
    save: 'Guardar contacto',
    fields: {
      mobile: { label: 'Móvil', hint: '9 cifras, o + y prefijo. Por ejemplo, 600 000 001.' },
      landline: { label: 'Fijo', hint: '9 cifras, o + y prefijo. Por ejemplo, 912 345 678.' },
      email: { label: 'Correo', hint: 'Con @ y un punto después. Por ejemplo, ana@correo.es.' },
    },
    share: {
      mobile: 'Compartir el móvil',
      landline: 'Compartir el fijo',
      email: 'Compartir el correo',
    },
  },
  own: {
    row: 'Mis datos de contacto',
    loading: 'Cargando tus datos…',
    loadError: 'No hemos podido cargar tus datos de contacto.',
    notLinked:
      'Tu usuario todavía no está vinculado a un hermano de tu comunidad. Pide a tu moderador que te vincule y podrás elegir aquí qué datos compartes.',
    hideNote: 'Si dejas de compartir un dato, se borra y tus hermanos dejan de verlo.',
    saved: 'Datos guardados.',
    nameLabel: 'Nombre',
    communityLabel: 'Comunidad',
    origin: originText,
  },
  link: {
    row: 'Vincular con un usuario',
    title: 'Vincular con un usuario',
    usersTitle: 'Usuarios registrados',
    hint: 'Solo aparecen los usuarios registrados de la comunidad. El nombre del hermano y el del usuario son independientes: vincular no copia nada.',
    loading: 'Cargando usuarios…',
    loadError: 'No hemos podido cargar los usuarios.',
    empty: 'No hay usuarios registrados en esta comunidad.',
    chooseUser: 'Elige el usuario con el que vincular al hermano.',
    confirm: 'Vincular',
    saving: 'Vinculando…',
    unlink: {
      row: 'Desvincular del usuario',
      title: (name: string) => `¿Desvincular a ${name}?`,
      warning:
        'El hermano conserva su nombre y sus datos. El usuario dejará de poder ver y cambiar en «Mis datos de contacto» lo que comparte.',
      confirm: 'Desvincular ahora',
      cancel: 'Cancelar',
      working: 'Desvinculando…',
    },
  },
  transfer: {
    row: 'Trasladar a otra comunidad',
    title: 'Trasladar hermano',
    administratorsOnly: 'Solo un administrador puede trasladar hermanos a otra comunidad.',
    communitiesTitle: 'Comunidad de destino',
    hint: 'El hermano y su usuario, si lo tiene, cambian de comunidad a la vez.',
    empty: 'No hay otra comunidad activa a la que trasladarlo.',
    chooseCommunity: 'Elige la comunidad de destino.',
    confirm: 'Trasladar',
    saving: 'Trasladando…',
    dialog: {
      title: (name: string, number: number) => `¿Trasladar a ${name} a la comunidad ${number}?`,
      warning: 'El hermano y su usuario, si lo tiene, cambiarán de comunidad a la vez.',
      confirm: 'Trasladar ahora',
      cancel: 'Cancelar',
      working: 'Trasladando…',
    },
  },
  delete: {
    row: 'Borrar hermano',
    title: (name: string) => `¿Borrar a ${name}?`,
    warning: (linkedUser: boolean) =>
      linkedUser
        ? 'Se borran para siempre el hermano, sus datos de contacto y el registro de sus consentimientos. Su usuario también se borra y se cierran sus sesiones. No se puede deshacer.'
        : 'Se borran para siempre el hermano, sus datos de contacto y el registro de sus consentimientos. No se puede deshacer.',
    confirm: 'Borrar ahora',
    cancel: 'Cancelar',
    working: 'Borrando…',
  },
  dataExport: {
    row: 'Copia de los datos',
    title: 'Copia de los datos del hermano',
    loading: 'Preparando la copia…',
    loadError: 'No hemos podido preparar la copia de los datos.',
    share: 'Compartir la copia',
    shareError: 'No hemos podido compartir la copia en este dispositivo.',
    heading: (name: string) => `Copia de los datos de ${name}`,
    community: (number: number) => `Comunidad: ${number}`,
    date: (date: string) => `Fecha de la copia: ${date}`,
    notShared: 'no compartido',
    consentsTitle: 'Registro de consentimientos:',
    noConsents: 'Sin registro de consentimientos.',
    field: { MOBILE: 'Móvil', LANDLINE: 'Fijo', EMAIL: 'Correo' } as Record<string, string>,
    decision: { GRANTED: 'concedido', WITHDRAWN: 'retirado' } as Record<string, string>,
    channel: { VERBAL: 'de palabra', WRITTEN: 'por escrito', APP: 'desde la aplicación' } as Record<string, string>,
    role: { MODERATOR: 'moderador', ADMINISTRATOR: 'administrador' } as Record<string, string>,
    byMember: 'anotado por el propio hermano',
    deletedUser: 'usuario eliminado',
  },
  consent: {
    title: 'Consentimiento del hermano',
    channel: { VERBAL: 'De palabra', WRITTEN: 'Por escrito' },
    noticeGiven: 'He informado al hermano',
  },
  fieldErrors: {
    givenName: {
      REQUIRED: 'Escribe el nombre del hermano.',
      TOO_LONG: 'El nombre es demasiado largo (máximo 100 caracteres).',
    },
    surnames: {
      TOO_LONG: 'Los apellidos son demasiado largos (máximo 100 caracteres).',
    },
    mobile: {
      REQUIRED: 'Escribe el móvil o desactiva «Compartir el móvil».',
      PHONE_INVALID:
        'El móvil no es válido. Escribe 9 cifras que empiecen por 6, 7, 8 o 9, por ejemplo 600 000 001, o un número con + y prefijo, por ejemplo +351 912 345 678.',
    },
    landline: {
      REQUIRED: 'Escribe el fijo o desactiva «Compartir el fijo».',
      PHONE_INVALID:
        'El fijo no es válido. Escribe 9 cifras que empiecen por 6, 7, 8 o 9, por ejemplo 912 345 678, o un número con + y prefijo, por ejemplo +351 912 345 678.',
    },
    email: {
      REQUIRED: 'Escribe el correo o desactiva «Compartir el correo».',
      EMAIL_INVALID: 'El correo no es válido. Escríbelo con @ y un punto después, por ejemplo ana@correo.es.',
    },
  } as Record<string, Record<string, string>>,
  errors: {
    CONSENT_REQUIRED: 'Indica si el hermano ha dado su consentimiento de palabra o por escrito.',
    NOTICE_NOT_GIVEN: 'Marca «He informado al hermano» antes de guardar.',
    COMMUNITY_REQUIRED: 'Elige la comunidad del hermano.',
    COMMUNITY_NOT_FOUND: 'La comunidad ya no existe.',
    COMMUNITY_DEACTIVATED: 'La comunidad está desactivada: no admite altas nuevas.',
    CONSENT_MANAGED_BY_MEMBER: 'Este hermano decide él mismo qué comparte desde su cuenta.',
    MEMBER_ALREADY_LINKED: 'Este hermano ya tiene un usuario vinculado.',
    USER_ALREADY_LINKED: 'Ese usuario ya está vinculado a otro hermano.',
    MEMBER_NOT_LINKED: 'Este hermano no tiene usuario vinculado.',
    USER_NOT_IN_COMMUNITY: 'Ese usuario no es de la comunidad del hermano.',
    USER_NOT_FOUND: 'Ese usuario ya no existe.',
    RESPONSIBLE_REQUIRED: 'Es el responsable de su comunidad: designa antes a otro responsable.',
    SELF_ACTION_FORBIDDEN: 'No puedes borrar tu propio usuario ni al hermano vinculado a él.',
    LAST_MODERATOR: 'No se puede: su usuario es el último moderador de la comunidad.',
    LAST_ADMINISTRATOR: 'No se puede: su usuario es el último administrador.',
    USER_LINKED_TO_MEMBER: 'El usuario está vinculado a un hermano de otra comunidad.',
    CONCURRENT_MODIFICATION: 'Los datos han cambiado mientras los editabas. Vuelve a abrir al hermano.',
    FORBIDDEN: 'No tienes permiso para hacer esto.',
    NOT_FOUND: 'El hermano ya no existe.',
    VALIDATION_FAILED: 'Revisa los datos introducidos.',
    UNKNOWN: 'No hemos podido completar la operación. Inténtalo de nuevo en unos minutos.',
  } as Record<string, string>,
};

export type MemberProblemField = 'givenName' | 'surnames' | ContactFieldName;

const FIELD_NAMES: MemberProblemField[] = ['givenName', 'surnames', 'mobile', 'landline', 'email'];

export type MemberProblem = { field: MemberProblemField | null; message: string };

export function problemForMembersFailure(failure: {
  code?: string;
  fieldErrors?: { field: string; code: string }[];
}): MemberProblem {
  const first = failure.fieldErrors?.[0];
  if (first) {
    const field = FIELD_NAMES.find((name) => name === first.field);
    const fieldMessage = field ? membersText.fieldErrors[field]?.[first.code] : undefined;
    if (field && fieldMessage) {
      return { field, message: fieldMessage };
    }
    const message = membersText.errors[first.code];
    if (message) {
      return { field: null, message };
    }
  }
  return { field: null, message: membersText.errors[failure.code ?? 'UNKNOWN'] ?? membersText.errors.UNKNOWN };
}
