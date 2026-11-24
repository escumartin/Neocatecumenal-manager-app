export const identity = {
  login: {
    title: 'Iniciar sesión',
    accessIdLabel: 'Identificador de acceso',
    passwordLabel: 'Contraseña',
    showPassword: 'Mostrar contraseña',
    hidePassword: 'Ocultar contraseña',
    submit: 'Entrar',
    submitting: 'Entrando…',
    required: 'Escribe tu identificador de acceso y tu contraseña.',
  },
  changePassword: {
    title: 'Cambia tu contraseña',
    intro: 'Elige una contraseña nueva, de 8 a 64 caracteres. Puedes usar una frase fácil de recordar.',
    currentLabel: 'Contraseña actual',
    newLabel: 'Contraseña nueva',
    show: 'Mostrar contraseñas',
    hide: 'Ocultar contraseñas',
    submit: 'Guardar contraseña',
    submitting: 'Guardando…',
    cancel: 'Volver sin cambiar',
    fieldErrors: {
      REQUIRED: 'Escribe este dato.',
      PASSWORD_TOO_SHORT: 'La contraseña es demasiado corta: necesita al menos 8 caracteres.',
      PASSWORD_TOO_LONG: 'La contraseña es demasiado larga: el máximo son 64 caracteres.',
      PASSWORD_TOO_COMMON: 'Esa contraseña es demasiado habitual. Elige otra más difícil de adivinar.',
      PASSWORD_CONTAINS_ACCESS_ID: 'La contraseña no puede contener tu identificador de acceso.',
      CURRENT_PASSWORD_INCORRECT: 'La contraseña actual no es correcta.',
    } as Record<string, string>,
  },
  logout: 'Cerrar sesión',
  loading: 'Cargando…',
  sessionUnavailable: {
    message: 'No hemos podido comprobar tu sesión. Revisa tu conexión.',
    retry: 'Reintentar',
  },
  errors: {
    INVALID_CREDENTIALS: 'El identificador o la contraseña no son correctos.',
    FORBIDDEN: 'No se ha podido iniciar sesión desde este origen.',
    VALIDATION_FAILED: 'Revisa los datos introducidos.',
    UNKNOWN_ACTION: 'No hemos podido completar la operación. Inténtalo de nuevo en unos minutos.',
    UNKNOWN: 'No hemos podido iniciar sesión. Inténtalo de nuevo en unos minutos.',
  } as Record<string, string>,
  blocked(retryAfterSeconds: number) {
    const minutes = Math.max(1, Math.ceil(retryAfterSeconds / 60));
    const unit = minutes === 1 ? 'minuto' : 'minutos';
    return `Demasiados intentos. Vuelve a intentarlo dentro de ${minutes} ${unit}.`;
  },
};

export function messageForLoginFailure(code: string, retryAfterSeconds?: number): string {
  if (code === 'LOGIN_TEMPORARILY_BLOCKED') {
    return identity.blocked(retryAfterSeconds ?? 60);
  }
  return identity.errors[code] ?? identity.errors.UNKNOWN;
}
