import { fireEvent, render, screen } from '@testing-library/react-native';
import * as ReactNative from 'react-native';
import { Linking } from 'react-native';
import { UpdateRequiredScreen } from '../update-required';
import { compatibility } from '@/i18n/es/compatibility';

const storeUrl = 'https://apps.example.test/neocatecumenal';

function classOf(element: { props: { className?: string } }) {
  return element.props.className ?? '';
}

describe('update required screen', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('announces the required update as an alert with the message from the catalogue', async () => {
    await render(<UpdateRequiredScreen storeUrl={storeUrl} />);

    expect(screen.getByRole('alert')).toHaveTextContent(compatibility.message);
    expect(screen.getByRole('heading', { name: compatibility.title })).toBeOnTheScreen();
  });

  it('shows the message at 18 px or larger', async () => {
    await render(<UpdateRequiredScreen storeUrl={storeUrl} />);

    expect(classOf(screen.getByRole('alert'))).toContain('text-body');
  });

  it('offers a store button of at least 48 px labelled from the catalogue', async () => {
    await render(<UpdateRequiredScreen storeUrl={storeUrl} />);

    const button = screen.getByRole('button', { name: compatibility.openStore });
    expect(classOf(button)).toContain('min-h-12');
  });

  it('opens the store when the button is pressed', async () => {
    const openURL = jest.spyOn(Linking, 'openURL').mockResolvedValue();
    await render(<UpdateRequiredScreen storeUrl={storeUrl} />);

    await fireEvent.press(screen.getByRole('button', { name: compatibility.openStore }));

    expect(openURL).toHaveBeenCalledWith(storeUrl);
  });

  it('does not offer the store button without a store address', async () => {
    await render(<UpdateRequiredScreen />);

    expect(screen.queryByRole('button')).not.toBeOnTheScreen();
    expect(screen.getByRole('alert')).toHaveTextContent(compatibility.message);
  });

  it('tells where to find the app when there is no store address', async () => {
    await render(<UpdateRequiredScreen />);

    expect(screen.getByText(compatibility.searchInStore)).toBeOnTheScreen();
  });

  it('does not show the store search hint when there is a store address', async () => {
    await render(<UpdateRequiredScreen storeUrl={storeUrl} />);

    expect(screen.queryByText(compatibility.searchInStore)).not.toBeOnTheScreen();
  });

  it('shows a notice when the store cannot be opened', async () => {
    jest.spyOn(Linking, 'openURL').mockRejectedValue(new Error('cannot open'));
    await render(<UpdateRequiredScreen storeUrl={storeUrl} />);

    await fireEvent.press(screen.getByRole('button', { name: compatibility.openStore }));

    expect(await screen.findByText(compatibility.openStoreFailed)).toBeOnTheScreen();
  });

  it('does not show the failure notice when the store opens', async () => {
    jest.spyOn(Linking, 'openURL').mockResolvedValue();
    await render(<UpdateRequiredScreen storeUrl={storeUrl} />);

    await fireEvent.press(screen.getByRole('button', { name: compatibility.openStore }));

    expect(screen.queryByText(compatibility.openStoreFailed)).not.toBeOnTheScreen();
  });

  it('does not cap the font scaling of the message', async () => {
    await render(<UpdateRequiredScreen storeUrl={storeUrl} />);

    expect(screen.getByRole('alert').props.allowFontScaling).not.toBe(false);
    expect(screen.getByRole('alert').props.maxFontSizeMultiplier).toBeUndefined();
  });

  it.each(['light', 'dark'] as const)('renders in the %s theme', async (scheme) => {
    jest.spyOn(ReactNative, 'useColorScheme').mockReturnValue(scheme);

    await render(<UpdateRequiredScreen storeUrl={storeUrl} />);

    expect(screen.getByRole('alert')).toBeOnTheScreen();
  });
});
