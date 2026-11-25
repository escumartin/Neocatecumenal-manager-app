import * as ExpoHaptics from 'expo-haptics';
import { Platform } from 'react-native';
import { hapticFailure, hapticSuccess } from '../haptics';

jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn().mockResolvedValue(undefined),
  notificationAsync: jest.fn().mockResolvedValue(undefined),
  ImpactFeedbackStyle: { Light: 'light' },
  NotificationFeedbackType: { Error: 'error' },
}));

describe('haptics', () => {
  afterEach(() => {
    jest.clearAllMocks();
    jest.restoreAllMocks();
  });

  it('gives a light impact on success', () => {
    hapticSuccess();

    expect(ExpoHaptics.impactAsync).toHaveBeenCalledWith('light');
  });

  it('gives an error notification on failure', () => {
    hapticFailure();

    expect(ExpoHaptics.notificationAsync).toHaveBeenCalledWith('error');
  });

  it('does nothing on web', () => {
    jest.replaceProperty(Platform, 'OS', 'web');

    hapticSuccess();
    hapticFailure();

    expect(ExpoHaptics.impactAsync).not.toHaveBeenCalled();
    expect(ExpoHaptics.notificationAsync).not.toHaveBeenCalled();
  });

  it('swallows a haptics engine that rejects', async () => {
    jest.mocked(ExpoHaptics.impactAsync).mockRejectedValueOnce(new Error('unavailable'));

    expect(() => hapticSuccess()).not.toThrow();
    await Promise.resolve();
  });

  it('swallows a haptics engine that throws', () => {
    jest.mocked(ExpoHaptics.notificationAsync).mockImplementationOnce(() => {
      throw new Error('missing native module');
    });

    expect(() => hapticFailure()).not.toThrow();
  });
});
