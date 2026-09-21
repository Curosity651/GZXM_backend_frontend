import { apiRequest } from './http-client';

export interface ApiCurrentUser {
  id: string;
  username: string;
  principalName?: string;
  contactName?: string;
  unitId?: string;
  unitName?: string;
  roleCode: string;
  pagePermissions: string[];
  actionPermissions: string[];
  memberships: Array<{ id: string; topicId: string; unitId: string; membershipType: 'LEAD' | 'PARTICIPANT'; enabled: boolean }>;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: ApiCurrentUser;
}

export const authApi = {
  login: (username: string, password: string) => apiRequest<TokenResponse>('/auth/login', {
    method: 'POST', body: JSON.stringify({ username, password }),
  }, false),
  me: () => apiRequest<ApiCurrentUser>('/auth/me'),
  logout: () => apiRequest<void>('/auth/logout', { method: 'POST' }),
};
