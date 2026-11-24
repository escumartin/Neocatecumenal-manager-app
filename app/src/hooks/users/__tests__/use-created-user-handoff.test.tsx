import { renderHook } from '@testing-library/react-native';
import { createdUserHandoff } from '@/lib/users/created-user-handoff';
import { useCreatedUserHandoff } from '../use-created-user-handoff';

const created = {
  id: '6f1c1f1e-0000-4000-8000-000000000021',
  accessId: 482913,
  name: 'Test User',
  role: 'REGISTERED' as const,
  communityId: '6f1c1f1e-0000-4000-8000-000000000011',
  passwordChangePending: true,
  initialPassword: 'abcd234567',
};

describe('use created user handoff', () => {
  afterEach(() => createdUserHandoff.clear());

  it('keeps the credential across re-renders of the same screen', async () => {
    createdUserHandoff.set(created);
    const { result, rerender } = await renderHook(() => useCreatedUserHandoff());

    await rerender({});

    expect(result.current).toEqual({ created, kind: 'created' });
  });

  it('shows the credential only once, to the first screen that takes it', async () => {
    createdUserHandoff.set(created);
    await renderHook(() => useCreatedUserHandoff());

    const second = await renderHook(() => useCreatedUserHandoff());

    expect(second.result.current).toBeNull();
  });
});
