import { render, screen } from '@testing-library/react-native';
import { Platform, Text } from 'react-native';
import { FormScroll } from '../form-scroll';

describe('form scroll', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('lifts its content above the keyboard and keeps taps on controls working', async () => {
    await render(
      <FormScroll>
        <Text>content</Text>
      </FormScroll>,
    );

    expect(screen.root?.props.automaticallyAdjustKeyboardInsets).toBe(true);
    expect(screen.root?.props.keyboardShouldPersistTaps).toBe('handled');
    expect(screen.getByText('content')).toBeOnTheScreen();
  });

  it('does not wrap the form in a keyboard avoiding view on iOS', async () => {
    await render(
      <FormScroll>
        <Text>content</Text>
      </FormScroll>,
    );

    expect(screen.root?.props.automaticallyAdjustKeyboardInsets).toBe(true);
  });

  it('wraps the form in a keyboard avoiding view on Android', async () => {
    jest.replaceProperty(Platform, 'OS', 'android');
    await render(
      <FormScroll>
        <Text>content</Text>
      </FormScroll>,
    );

    expect(screen.root?.props.automaticallyAdjustKeyboardInsets).toBeUndefined();
    expect(screen.root?.props.onLayout).toEqual(expect.any(Function));
    expect(screen.getByText('content')).toBeOnTheScreen();
  });
});
