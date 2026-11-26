import { backupRetentionNotice } from './backup-retention';

export const privacyNotice = {
  title: 'Aviso sobre tus datos',
  paragraphs: [
    'Texto legal del aviso de privacidad pendiente de redacción.',
    backupRetentionNotice,
  ],
  accept: 'Acepto',
  reject: 'No acepto',
  accepting: 'Guardando…',
  rejectConsequence: 'Si no aceptas, se cerrará tu sesión y volveremos a mostrarte este aviso la próxima vez que entres.',
  errors: {
    PRIVACY_NOTICE_OUTDATED: 'El aviso ha cambiado. Cierra la sesión y vuelve a entrar para leer la versión vigente.',
  } as Record<string, string>,
};
