import { apiRequest } from './http-client';

export interface ApiPage<T> { items: T[]; page: number; size: number; total: number }

export interface ApiUser {
  id: string;
  username: string;
  name: string;
  unitId?: string;
  roleId?: string;
  roleName?: string;
  phone?: string;
  email?: string;
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

export interface CreateUserResponse { user: ApiUser; temporaryPassword: string }
export interface ApiUnit { id: string; code: string; name: string; internal: boolean; enabled: boolean }

export const systemApi = {
  users: (params: URLSearchParams) => apiRequest<ApiPage<ApiUser>>(`/users?${params.toString()}`),
  createUser: (data: { username: string; roleId: string; name: string; phone?: string; email?: string; enabled?: boolean }) =>
    apiRequest<CreateUserResponse>('/users', { method: 'POST', body: JSON.stringify(data) }),
  updateUser: (id: string, data: { username: string; name: string; phone?: string; email?: string }) =>
    apiRequest<ApiUser>(`/users/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  setUserStatus: (id: string, enabled: boolean) =>
    apiRequest<ApiUser>(`/users/${id}/status`, { method: 'PUT', body: JSON.stringify({ enabled }) }),
  resetPassword: (id: string) =>
    apiRequest<{ temporaryPassword: string }>(`/users/${id}/password:reset`, { method: 'POST' }),
  roles: () => apiRequest<ApiRole[]>('/roles'),
  permissions: () => apiRequest<ApiPermission[]>('/permissions'),
  units: () => apiRequest<ApiUnit[]>('/units'),
  updateRole: (id: string, data: { pagePermissions: string[]; actionPermissions: string[]; enabled: boolean }) =>
    apiRequest<ApiRole>(`/roles/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
};
