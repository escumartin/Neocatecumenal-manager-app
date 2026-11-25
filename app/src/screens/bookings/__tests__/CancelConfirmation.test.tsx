import { PortalHost } from '@rn-primitives/portal';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { CancelConfirmation } from '../cancel-confirmation';
import { fontSizeOf, minHeightOf } from '../support/layout-checks';
import { bookingsText } from '@/i18n/es/bookings';

const series = [
  { id: 'b1', date: '2030-05-12', startTime: '18:00', endTime: '20:00' },
  { id: 'b2', date: '2030-05-19', startTime: '18:00', endTime: '20:00' },
];

async function open(
  props: Partial<React.ComponentProps<typeof CancelConfirmation>> = {},
) {
  const onConfirm = jest.fn();
  const onDismiss = jest.fn();
  await render(
    <>
      <CancelConfirmation
        roomName="Salón grande"
        busy={false}
        onConfirm={onConfirm}
        onDismiss={onDismiss}
        {...props}
      />
      <PortalHost />
    </>,
  );
  return { onConfirm, onDismiss };
}

describe('cancel confirmation', () => {
  it('is an alert dialog named after the room with the permanent warning', async () => {
    await open();

    const dialog = await screen.findByLabelText(
      bookingsText.cancel.title('Salón grande'),
    );
    expect(dialog.props.role).toBe('alertdialog');
    expect(
      screen.getByText(bookingsText.cancel.title('Salón grande')),
    ).toBeOnTheScreen();
    expect(screen.getByText(bookingsText.cancel.warning)).toBeOnTheScreen();
    expect(
      screen.getByRole('button', { name: bookingsText.cancel.confirm }),
    ).toBeOnTheScreen();
    expect(
      screen.getByRole('button', { name: bookingsText.cancel.keep }),
    ).toBeOnTheScreen();
  });

  it('does not cancel until the confirmation is pressed', async () => {
    const { onConfirm } = await open();
    await screen.findByLabelText(bookingsText.cancel.title('Salón grande'));

    expect(onConfirm).not.toHaveBeenCalled();
    await fireEvent.press(
      screen.getByRole('button', { name: bookingsText.cancel.confirm }),
    );

    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onConfirm).toHaveBeenCalledWith(undefined);
  });

  it('keeps the booking when the user does not cancel', async () => {
    const { onConfirm, onDismiss } = await open();
    await screen.findByLabelText(bookingsText.cancel.title('Salón grande'));

    await fireEvent.press(
      screen.getByRole('button', { name: bookingsText.cancel.keep }),
    );

    expect(onDismiss).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('disables both actions while the cancellation runs', async () => {
    await open({ busy: true });
    await screen.findByLabelText(bookingsText.cancel.title('Salón grande'));

    expect(
      screen.getByRole('button', { name: bookingsText.cancel.confirm }),
    ).toBeDisabled();
    expect(
      screen.getByRole('button', { name: bookingsText.cancel.keep }),
    ).toBeDisabled();
  });

  it('offers no choice for a single request', async () => {
    await open();
    await screen.findByLabelText(bookingsText.cancel.title('Salón grande'));

    expect(screen.queryAllByRole('radio')).toHaveLength(0);
  });

  it('offers the whole series, selected by default, and each occurrence', async () => {
    await open({ occurrences: series });
    await screen.findByLabelText(bookingsText.cancel.title('Salón grande'));

    expect(
      screen.getByRole('radio', { name: bookingsText.cancel.wholeSeries }),
    ).toBeChecked();
    expect(
      screen.getByRole('radio', {
        name: bookingsText.cancel.oneOccurrence(series[0]),
      }),
    ).not.toBeChecked();
    expect(
      screen.getByRole('radio', {
        name: bookingsText.cancel.oneOccurrence(series[1]),
      }),
    ).not.toBeChecked();
  });

  it('cancels the whole series by default', async () => {
    const { onConfirm } = await open({ occurrences: series });
    await screen.findByLabelText(bookingsText.cancel.title('Salón grande'));

    await fireEvent.press(
      screen.getByRole('button', { name: bookingsText.cancel.confirm }),
    );

    expect(onConfirm).toHaveBeenCalledWith(undefined);
  });

  it('cancels only the chosen occurrence', async () => {
    const { onConfirm } = await open({ occurrences: series });
    await screen.findByLabelText(bookingsText.cancel.title('Salón grande'));

    await fireEvent.press(
      screen.getByRole('radio', {
        name: bookingsText.cancel.oneOccurrence(series[1]),
      }),
    );
    expect(
      screen.getByRole('radio', {
        name: bookingsText.cancel.oneOccurrence(series[1]),
      }),
    ).toBeChecked();
    expect(
      screen.getByRole('radio', { name: bookingsText.cancel.wholeSeries }),
    ).not.toBeChecked();
    await fireEvent.press(
      screen.getByRole('button', { name: bookingsText.cancel.confirm }),
    );

    expect(onConfirm).toHaveBeenCalledWith('b2');
  });

  it('shows the failure of the cancellation as an alert', async () => {
    await open({ problem: bookingsText.errors.BOOKING_ALREADY_STARTED });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      `⚠ ${bookingsText.errors.BOOKING_ALREADY_STARTED}`,
    );
  });

  it('gives every button a label, at least 18 px text and at least 48 px height', async () => {
    await open({ occurrences: series });
    await screen.findByLabelText(bookingsText.cancel.title('Salón grande'));

    const buttons = [
      ...screen.getAllByRole('button'),
      ...screen.getAllByRole('radio'),
    ];
    expect(buttons.length).toBeGreaterThanOrEqual(5);
    for (const button of buttons) {
      expect(
        button.props['aria-label'] ?? button.props.accessibilityLabel,
      ).toBeTruthy();
      expect(minHeightOf(button)).toBeGreaterThanOrEqual(48);
    }
    expect(
      fontSizeOf(screen.getByText(bookingsText.cancel.warning)),
    ).toBeGreaterThanOrEqual(18);
  });

  it('marks the chosen option with a check mark and its checked state, not by colour alone', async () => {
    await open({ occurrences: series });
    await screen.findByLabelText(bookingsText.cancel.title('Salón grande'));
    const label = bookingsText.cancel.oneOccurrence(series[0]);

    expect(
      screen.getByText(`✓ ${bookingsText.cancel.wholeSeries}`),
    ).toBeOnTheScreen();
    expect(screen.queryByText(`✓ ${label}`)).not.toBeOnTheScreen();

    await fireEvent.press(screen.getByRole('radio', { name: label }));

    expect(screen.getByText(`✓ ${label}`)).toBeOnTheScreen();
    expect(
      screen.queryByText(`✓ ${bookingsText.cancel.wholeSeries}`),
    ).not.toBeOnTheScreen();
    expect(
      screen.getByRole('radio', { name: label }).props.accessibilityState,
    ).toEqual(expect.objectContaining({ checked: true }));
    expect(
      screen.getByRole('radio', { name: bookingsText.cancel.wholeSeries }).props
        .accessibilityState,
    ).toEqual(expect.objectContaining({ checked: false }));
  });
});
