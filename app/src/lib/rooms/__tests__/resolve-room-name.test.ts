import { resolveRoomName } from '../resolve-room-name';

const labels = { loading: 'loading', unavailable: 'unavailable', deactivated: 'deactivated' };
const names = new Map([['r1', 'Salón grande']]);

describe('resolveRoomName', () => {
  it.each(['loading', 'ready', 'error'] as const)('returns the name when it is known with status %s', (status) => {
    expect(resolveRoomName(status, names, 'r1', labels)).toBe('Salón grande');
  });

  it('says loading while the rooms are loading', () => {
    expect(resolveRoomName('loading', names, 'r2', labels)).toBe('loading');
  });

  it('says unavailable when the rooms could not be loaded', () => {
    expect(resolveRoomName('error', names, 'r2', labels)).toBe('unavailable');
  });

  it('says deactivated only when the rooms loaded without the room', () => {
    expect(resolveRoomName('ready', names, 'r2', labels)).toBe('deactivated');
  });
});
