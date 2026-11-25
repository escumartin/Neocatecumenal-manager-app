import { Platform } from 'react-native';
import { sessionStore } from '@/lib/auth/session-store';
import { resolveApiUrl } from './api-url';
import { createApiClient } from './client';
import { sessionEvents } from './session-events';
import { createBookingRequestsApi } from './booking-requests-api';
import { createCommunitiesApi } from './communities-api';
import { createEventsApi } from './events-api';
import { createMembersApi, createMyMemberApi } from './members-api';
import { createRoomsApi } from './rooms-api';
import { createSessionApi } from './session-api';
import { createUsersApi } from './users-api';

export const apiClient = createApiClient({
  baseUrl: resolveApiUrl({
    url: process.env.EXPO_PUBLIC_API_URL,
    isDevelopment: __DEV__,
    isWeb: Platform.OS === 'web',
  }),
  readToken: sessionStore.read,
  onUnauthorized: sessionEvents.unauthorized,
  onPendingStep: sessionEvents.pendingStep,
});

export const sessionApi = createSessionApi(
  apiClient,
  Platform.OS === 'web' ? 'COOKIE' : 'BODY',
);

export const communitiesApi = createCommunitiesApi(apiClient);

export const usersApi = createUsersApi(apiClient);

export const roomsApi = createRoomsApi(apiClient);

export const bookingRequestsApi = createBookingRequestsApi(apiClient);

export const eventsApi = createEventsApi(apiClient);

export const membersApi = createMembersApi(apiClient);

export const myMemberApi = createMyMemberApi(apiClient);
