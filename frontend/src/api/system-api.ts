import { apiRequest } from './http-client';

export interface ApiPage<T> { items: T[]; page: number; size: number; total: number }

export interface ApiUser {
  id: string;
  username: string;
  principalName: string;
  principalPhone?: string;
  principalEmail?: string;
  contactName: string;
  unitId?: string;
  unitName?: string;
  roleId?: string;
  roleName?: string;
  contactPhone?: string;
  contactEmail?: string;
  enabled: boolean;
  createdAt: string;
}

export interface ApiRole {
  id: string;
  code: string;
  name: string;
  description?: string;
  pagePermissions: string[];
  actionPermissions: string[];
  enabled: boolean;
  builtIn: boolean;
}

export interface ApiPermission {
  code: string;
  name: string;
  type: 'PAGE' | 'ACTION';
  group: string;
  lockedForExternal: boolean;
}

export interface CreateUserResponse { user: ApiUser }
export interface ApiUnit { id: string; code: string; name: string; internal: boolean; enabled: boolean; topicUnitEligible: boolean }
export interface ApiTopicUser { id: string; username: string; principalName: string; contactName: string; contactPhone?: string; contactEmail?: string; unitId: string; unitName?: string; enabled: boolean }

export const systemApi = {
  users: (params: URLSearchParams) => apiRequest<ApiPage<ApiUser>>(`/users?${params.toString()}`),
  createUser: (data: { username: string; roleId: string; principalName: string; principalPhone?: string; principalEmail?: string; contactName: string; contactPhone?: string; contactEmail?: string; enabled?: boolean; unitId?: string; unitName?: string; password: string }) =>
    apiRequest<CreateUserResponse>('/users', { method: 'POST', body: JSON.stringify(data) }),
  updateUser: (id: string, data: { username: string; roleId?: string; principalName?: string; principalPhone?: string; principalEmail?: string; contactName?: string; contactPhone?: string; contactEmail?: string; unitId?: string; unitName?: string }) =>
    apiRequest<ApiUser>(`/users/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  setUserStatus: (id: string, enabled: boolean) =>
    apiRequest<ApiUser>(`/users/${id}/status`, { method: 'PUT', body: JSON.stringify({ enabled }) }),
  changePassword: (id: string, password: string) =>
    apiRequest<void>(`/users/${id}/password`, { method: 'PUT', body: JSON.stringify({ password }) }),
  roles: () => apiRequest<ApiRole[]>('/roles'),
  permissions: () => apiRequest<ApiPermission[]>('/permissions'),
  units: () => apiRequest<ApiUnit[]>('/units'),
  topicUsers: (unitId?: string) => apiRequest<ApiTopicUser[]>(`/topic-unit-users${unitId ? `?unitId=${unitId}` : ''}`),
  updateRole: (id: string, data: { pagePermissions: string[]; actionPermissions: string[]; enabled: boolean }) =>
    apiRequest<ApiRole>(`/roles/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
};
