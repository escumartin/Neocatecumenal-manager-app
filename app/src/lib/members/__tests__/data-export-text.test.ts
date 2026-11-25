import { dataExportText } from '../data-export-text';
import { ana, exportOf } from '@/screens/members/support/test-support';

describe('data export text', () => {
  it('names the member, the community and the date of the copy', () => {
    const text = dataExportText(exportOf(ana));

    expect(text).toContain('Copia de los datos de Ana Pérez');
    expect(text).toContain('Comunidad: 12');
    expect(text).toContain('Fecha de la copia: 25/11/2026');
  });

  it('lists every stored contact value and marks the ones not shared', () => {
    const text = dataExportText(exportOf({ ...ana, email: 'ana@example.org' }));

    expect(text).toContain('Móvil: 600000001');
    expect(text).toContain('Fijo: no compartido');
    expect(text).toContain('Correo: ana@example.org');
  });

  it('writes each consent record with decision, channel, date and author', () => {
    const text = dataExportText(exportOf(ana));

    expect(text).toContain('Móvil: concedido, de palabra, 20/11/2026, anotado por Marcos (moderador)');
  });

  it('says deleted user when the author no longer exists', () => {
    const text = dataExportText(exportOf(ana));

    expect(text).toContain('Correo: concedido, por escrito, 21/11/2026, anotado por usuario eliminado (administrador)');
  });

  it('says the member did it from the app and never prints a name for them', () => {
    const text = dataExportText(exportOf(ana));

    expect(text).toContain('Correo: retirado, desde la aplicación, 22/11/2026, anotado por el propio hermano');
  });

  it('says there are no consent records when there are none', () => {
    const text = dataExportText({ ...exportOf(ana), consentRecords: [] });

    expect(text).toContain('Sin registro de consentimientos.');
  });
});
