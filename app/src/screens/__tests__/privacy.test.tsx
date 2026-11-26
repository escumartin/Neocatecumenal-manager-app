import { render, screen } from '@testing-library/react-native';
import * as ReactNative from 'react-native';
import PrivacyRoute from '@/app/privacy';
import { privacy } from '@/i18n/es/privacy';

function classOf(element: { props: { className?: string } }) {
  return element.props.className ?? '';
}

describe('public privacy route', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('renders without a session provider', async () => {
    await render(<PrivacyRoute />);

    expect(screen.getByRole('heading', { name: privacy.title })).toBeOnTheScreen();
  });

  it('shows every paragraph of the provisional text', async () => {
    await render(<PrivacyRoute />);

    privacy.paragraphs.forEach((paragraph) => {
      expect(screen.getByText(paragraph)).toBeOnTheScreen();
    });
  });

  it('states that deleted data disappears from the backups after 30 days', async () => {
    expect(privacy.paragraphs.some((paragraph) => /30 días/.test(paragraph) && /copias/.test(paragraph))).toBe(true);
  });

  it('shows the paragraphs at 18 px or larger', async () => {
    await render(<PrivacyRoute />);

    expect(classOf(screen.getByText(privacy.paragraphs[0]))).toContain('text-body');
  });

  it.each(['light', 'dark'] as const)('renders in the %s theme', async (scheme) => {
    jest.spyOn(ReactNative, 'useColorScheme').mockReturnValue(scheme);

    await render(<PrivacyRoute />);

    expect(screen.getByRole('heading', { name: privacy.title })).toBeOnTheScreen();
  });
});
