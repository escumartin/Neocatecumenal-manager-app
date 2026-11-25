import { fireEvent, screen, waitFor } from '@testing-library/react-native';
import { CommunityEdit } from '../edit';
import { CommunityResponsible } from '../responsible';
import { detailOf, fakeCommunitiesApi, renderAs, twelve } from '../support/test-support';
import { ana, fakeMembersApi, luis, marta } from '@/screens/members/support/test-support';
import { fontSizeOf, minHeightOf } from '@/screens/bookings/support/layout-checks';
import { communitiesText } from '@/i18n/es/communities';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import type { CommunityDetail } from '@/lib/api/communities-api';
import type { Member } from '@/lib/api/members-api';

jest.mock('@/hooks/navigation/use-reload-on-focus', () => ({
  useReloadOnFocus: jest.fn(),
}));
jest.mock('@/lib/haptics', () => ({
  hapticSuccess: jest.fn(),
  hapticFailure: jest.fn(),
}));

afterEach(() => {
  jest.clearAllMocks();
});

const foreign: Member = {
  id: '6f1c1f1e-0000-4000-8000-000000000051',
  communityId: '6f1c1f1e-0000-4000-8000-000000000099',
  givenName: 'Carla',
  isResponsible: false,
};

function withResponsible(member: Member, responsibleIsModerator: boolean): CommunityDetail {
  return {
    ...detailOf(twelve),
    hasResponsible: true,
    responsible: {
      id: member.id,
      givenName: member.givenName,
      ...(member.surnames ? { surnames: member.surnames } : {}),
    },
    responsibleIsModerator,
  };
}

function responsible(communityApi = fakeCommunitiesApi(), membersApi = fakeMembersApi()) {
  const onSaved = jest.fn();
  return {
    communityApi,
    membersApi,
    onSaved,
    element: (
      <CommunityResponsible communityId={twelve.id} api={communityApi} membersApi={membersApi} onSaved={onSaved} />
    ),
  };
}

describe('community file', () => {
  it('states explicitly that the community has no responsible', async () => {
    await renderAs(
      'ADMINISTRATOR',
      <CommunityEdit
        communityId={twelve.id}
        api={fakeCommunitiesApi()}
        onSaved={jest.fn()}
        onChooseResponsible={jest.fn()}
      />,
    );

    expect(await screen.findByText(communitiesText.list.noResponsible)).toBeOnTheScreen();
    expect(screen.getByText(communitiesText.responsible.designateRow)).toBeOnTheScreen();
  });

  it('shows the name of the responsible and offers to change it', async () => {
    const api = fakeCommunitiesApi({
      get: jest.fn().mockResolvedValue({
        ok: true,
        community: withResponsible(luis, true),
      }),
    });
    await renderAs(
      'ADMINISTRATOR',
      <CommunityEdit communityId={twelve.id} api={api} onSaved={jest.fn()} onChooseResponsible={jest.fn()} />,
    );

    expect(await screen.findByText('Luis Álvarez')).toBeOnTheScreen();
    expect(screen.getByText(communitiesText.responsible.changeRow)).toBeOnTheScreen();
    expect(screen.queryByText(communitiesText.list.noResponsible)).not.toBeOnTheScreen();
    expect(screen.queryByText(communitiesText.responsible.notModerator)).not.toBeOnTheScreen();
  });

  it('warns without blocking when the responsible is not a moderator', async () => {
    const api = fakeCommunitiesApi({
      get: jest.fn().mockResolvedValue({
        ok: true,
        community: withResponsible(luis, false),
      }),
    });
    await renderAs('ADMINISTRATOR', <CommunityEdit communityId={twelve.id} api={api} onSaved={jest.fn()} />);

    expect(await screen.findByText(communitiesText.responsible.notModerator)).toBeOnTheScreen();
    expect(screen.getByLabelText(communitiesText.form.save)).toBeEnabled();
  });

  it('opens the responsible screen of the community', async () => {
    const onChooseResponsible = jest.fn();
    await renderAs(
      'ADMINISTRATOR',
      <CommunityEdit
        communityId={twelve.id}
        api={fakeCommunitiesApi()}
        onSaved={jest.fn()}
        onChooseResponsible={onChooseResponsible}
      />,
    );

    await fireEvent.press(await screen.findByLabelText(communitiesText.responsible.designateRow));

    expect(onChooseResponsible).toHaveBeenCalledWith(twelve.id);
  });
});

describe('responsible screen', () => {
  it('lists only the members of that community and asks the directory of that community', async () => {
    const membersApi = fakeMembersApi({
      list: jest.fn().mockResolvedValue({ ok: true, members: [luis, ana, marta, foreign] }),
    });
    const { element } = responsible(fakeCommunitiesApi(), membersApi);
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText('Ana Pérez')).toBeOnTheScreen();
    expect(screen.getByText('Luis Álvarez')).toBeOnTheScreen();
    expect(screen.getByText('Marta')).toBeOnTheScreen();
    expect(screen.queryByText('Carla')).not.toBeOnTheScreen();
    expect(membersApi.list).toHaveBeenCalledWith(twelve.id);
    expect(screen.getAllByRole('radio')).toHaveLength(3);
  });

  it('marks the current responsible as selected', async () => {
    const api = fakeCommunitiesApi({
      get: jest.fn().mockResolvedValue({
        ok: true,
        community: withResponsible(luis, true),
      }),
    });
    const { element } = responsible(api);
    await renderAs('ADMINISTRATOR', element);

    const current = await screen.findByRole('radio', { name: /Luis Álvarez/ });

    expect(current).toBeChecked();
    expect(screen.getByRole('radio', { name: /Ana Pérez/ })).not.toBeChecked();
  });

  it('designates the chosen member and closes', async () => {
    const { element, communityApi, onSaved } = responsible();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: /Ana Pérez/ }));
    await fireEvent.press(screen.getByLabelText(communitiesText.responsible.save));

    await waitFor(() => expect(communityApi.designateResponsible).toHaveBeenCalledWith(twelve.id, ana.id));
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(hapticSuccess).toHaveBeenCalledTimes(1);
  });

  it('does not allow saving without a choice or with the current responsible', async () => {
    const api = fakeCommunitiesApi({
      get: jest.fn().mockResolvedValue({
        ok: true,
        community: withResponsible(luis, true),
      }),
    });
    const { element } = responsible(api);
    await renderAs('ADMINISTRATOR', element);
    await screen.findByRole('radio', { name: /Luis Álvarez/ });

    expect(screen.getByLabelText(communitiesText.responsible.save)).toBeDisabled();

    await fireEvent.press(screen.getByRole('radio', { name: /Ana Pérez/ }));

    expect(screen.getByLabelText(communitiesText.responsible.save)).toBeEnabled();
  });

  it('warns without blocking when the current responsible is not a moderator', async () => {
    const api = fakeCommunitiesApi({
      get: jest.fn().mockResolvedValue({
        ok: true,
        community: withResponsible(luis, false),
      }),
    });
    const { element, communityApi, onSaved } = responsible(api);
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(communitiesText.responsible.notModerator)).toBeOnTheScreen();

    await fireEvent.press(screen.getByRole('radio', { name: /Ana Pérez/ }));
    await fireEvent.press(screen.getByLabelText(communitiesText.responsible.save));

    await waitFor(() => expect(communityApi.designateResponsible).toHaveBeenCalledWith(twelve.id, ana.id));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
  });

  it('shows no warning when the community has no responsible yet', async () => {
    const { element } = responsible();
    await renderAs('ADMINISTRATOR', element);

    await screen.findByText('Ana Pérez');

    expect(screen.queryByText(communitiesText.responsible.notModerator)).not.toBeOnTheScreen();
  });

  it('shows the localized message when the member is not in the community', async () => {
    const api = fakeCommunitiesApi({
      designateResponsible: jest.fn().mockResolvedValue({
        ok: false,
        status: 422,
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'memberId', code: 'MEMBER_NOT_IN_COMMUNITY' }],
      }),
    });
    const { element, onSaved } = responsible(api);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: /Ana Pérez/ }));
    await fireEvent.press(screen.getByLabelText(communitiesText.responsible.save));

    expect(await screen.findByText(`⚠ ${communitiesText.fieldErrors.MEMBER_NOT_IN_COMMUNITY}`)).toBeOnTheScreen();
    expect(onSaved).not.toHaveBeenCalled();
    expect(hapticFailure).toHaveBeenCalledTimes(1);
  });

  it('shows the localized message when the responsible is required', async () => {
    const api = fakeCommunitiesApi({
      designateResponsible: jest.fn().mockResolvedValue({
        ok: false,
        status: 409,
        code: 'RESPONSIBLE_REQUIRED',
      }),
    });
    const { element } = responsible(api);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: /Ana Pérez/ }));
    await fireEvent.press(screen.getByLabelText(communitiesText.responsible.save));

    expect(await screen.findByText(`⚠ ${communitiesText.errors.RESPONSIBLE_REQUIRED}`)).toBeOnTheScreen();
  });

  it('shows a generic message when the request fails unexpectedly', async () => {
    const api = fakeCommunitiesApi({
      designateResponsible: jest.fn().mockRejectedValue(new Error('offline')),
    });
    const { element } = responsible(api);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: /Ana Pérez/ }));
    await fireEvent.press(screen.getByLabelText(communitiesText.responsible.save));

    expect(await screen.findByText(`⚠ ${communitiesText.errors.UNKNOWN}`)).toBeOnTheScreen();
  });

  it('explains that a community without members cannot have a responsible yet', async () => {
    const membersApi = fakeMembersApi({
      list: jest.fn().mockResolvedValue({ ok: true, members: [] }),
    });
    const { element } = responsible(fakeCommunitiesApi(), membersApi);
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(communitiesText.responsible.noMembers)).toBeOnTheScreen();
    expect(screen.queryByLabelText(communitiesText.responsible.save)).not.toBeOnTheScreen();
  });

  it('offers a retry when the members cannot be loaded', async () => {
    const list = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValue({ ok: true, members: [ana] });
    const { element } = responsible(fakeCommunitiesApi(), fakeMembersApi({ list }));
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(`⚠ ${communitiesText.responsible.loadError}`)).toBeOnTheScreen();

    await fireEvent.press(screen.getByLabelText(communitiesText.retry));

    expect(await screen.findByText('Ana Pérez')).toBeOnTheScreen();
  });

  it('offers a retry when the community cannot be loaded', async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValue({ ok: true, community: detailOf(twelve) });
    const { element } = responsible(fakeCommunitiesApi({ get }));
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(`⚠ ${communitiesText.responsible.loadError}`)).toBeOnTheScreen();

    await fireEvent.press(screen.getByLabelText(communitiesText.retry));

    expect(await screen.findByText('Ana Pérez')).toBeOnTheScreen();
  });

  it('labels every control, keeps 48 px targets and readable type', async () => {
    const { element } = responsible();
    await renderAs('ADMINISTRATOR', element);

    await screen.findByText('Ana Pérez');
    await fireEvent.press(screen.getByRole('radio', { name: /Ana Pérez/ }));

    const controls = [
      ...screen.getAllByRole('radio'),
      screen.getByRole('button', { name: communitiesText.responsible.save }),
    ];
    for (const control of controls) {
      expect(control.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(control)).toBeGreaterThanOrEqual(48);
    }
    for (const text of screen.getAllByText(/./)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });
});
