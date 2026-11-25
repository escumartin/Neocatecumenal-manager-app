import type { Member, MemberManagementApi, MembersApi } from '@/lib/api/members-api';
import { twelve } from '@/screens/communities/support/test-support';

export const ana: Member = {
  id: '6f1c1f1e-0000-4000-8000-000000000041',
  communityId: twelve.id,
  givenName: 'Ana',
  surnames: 'Pérez',
  mobile: '600000001',
  isResponsible: false,
};

export const luis: Member = {
  id: '6f1c1f1e-0000-4000-8000-000000000042',
  communityId: twelve.id,
  givenName: 'Luis',
  surnames: 'Álvarez',
  isResponsible: true,
};

export const marta: Member = {
  id: '6f1c1f1e-0000-4000-8000-000000000043',
  communityId: twelve.id,
  givenName: 'Marta',
  email: 'marta@example.org',
  isResponsible: false,
};

export const linked: Member = {
  ...ana,
  id: '6f1c1f1e-0000-4000-8000-000000000044',
  linkedUserId: '6f1c1f1e-0000-4000-8000-000000000099',
};

export function fakeMembersApi(
  overrides: Partial<MembersApi & MemberManagementApi> = {},
): MembersApi & MemberManagementApi {
  return {
    list: jest.fn().mockResolvedValue({ ok: true, members: [luis, ana, marta] }),
    get: jest.fn().mockResolvedValue({ ok: true, member: ana }),
    create: jest.fn().mockResolvedValue({ ok: true, member: ana }),
    rename: jest.fn().mockResolvedValue({ ok: true, member: ana }),
    changeContact: jest.fn().mockResolvedValue({ ok: true, member: ana }),
    link: jest.fn().mockResolvedValue({ ok: true, member: linked }),
    unlink: jest.fn().mockResolvedValue({ ok: true }),
    transfer: jest.fn().mockResolvedValue({ ok: true, member: ana }),
    ...overrides,
  };
}
