import { fireEvent, screen, waitFor } from '@testing-library/react-native';
import { PrivacyNotice } from '../privacy-notice';
import { renderWithSession } from '../support/test-support';
import { identity } from '@/i18n/es/identity';
import { privacyNotice } from '@/i18n/es/privacy-notice';

const pending = { pendingSteps: ['PRIVACY_NOTICE' as const] };

describe('privacy notice', () => {
  it('shows the notice text with a heading', async () => {
    await renderWithSession(<PrivacyNotice />, pending);

    expect(await screen.findByRole('heading', { name: privacyNotice.title })).toBeOnTheScreen();
    expect(screen.getByText(privacyNotice.paragraphs[0])).toBeOnTheScreen();
  });

  it('states that deleted data disappears from the backups after 30 days', async () => {
    await renderWithSession(<PrivacyNotice />, pending);

    const retention = privacyNotice.paragraphs.find((paragraph) => /30 días/.test(paragraph) && /copias/.test(paragraph));
    expect(retention).toBeDefined();
    expect(await screen.findByText(retention as string)).toBeOnTheScreen();
  });

  it('records the acceptance of the current version', async () => {
    const { api } = await renderWithSession(<PrivacyNotice />, pending);

    await fireEvent.press(await screen.findByRole('button', { name: privacyNotice.accept }));

    await waitFor(() => expect(api.acceptPrivacyNotice).toHaveBeenCalledWith('2'));
  });

  it('closes the session when the notice is rejected', async () => {
    const { api, store } = await renderWithSession(<PrivacyNotice />, pending);

    await fireEvent.press(await screen.findByRole('button', { name: privacyNotice.reject }));

    await waitFor(() => expect(api.rejectPrivacyNotice).toHaveBeenCalledTimes(1));
    expect(store.clear).toHaveBeenCalled();
    expect(api.acceptPrivacyNotice).not.toHaveBeenCalled();
  });

  it('explains what happens when the notice is rejected', async () => {
    await renderWithSession(<PrivacyNotice />, pending);

    expect(await screen.findByText(privacyNotice.rejectConsequence)).toBeOnTheScreen();
  });

  it('shows an alert when the acceptance fails', async () => {
    await renderWithSession(<PrivacyNotice />, pending, {
      acceptPrivacyNotice: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'PRIVACY_NOTICE_OUTDATED' }),
    });

    await fireEvent.press(await screen.findByRole('button', { name: privacyNotice.accept }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${privacyNotice.errors.PRIVACY_NOTICE_OUTDATED}`);
  });

  it('shows a generic alert when the acceptance fails for an unknown reason', async () => {
    await renderWithSession(<PrivacyNotice />, pending, {
      acceptPrivacyNotice: jest.fn().mockResolvedValue({ ok: false, status: 500 }),
    });

    await fireEvent.press(await screen.findByRole('button', { name: privacyNotice.accept }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${identity.errors.UNKNOWN_ACTION}`);
  });
});
