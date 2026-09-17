import { create } from 'zustand';
import { authApi, type ApiCurrentUser } from '../api/auth-api';
import { ApiError } from '../api/http-client';
import { useAppStore } from './index';
import type { ActionPermissionKey, PagePermissionKey, RbacRole, User, UserRole } from '../types';

type SessionStatus = 'idle' | 'loading' | 'authenticated' | 'anonymous';

const roleNames: Record<string, UserRole> = {
  SYSTEM_ADMIN: '系统管理员',
  PROJECT_TECH_LEADER: '项目技术负责人',
  RESEARCH_ASSISTANT: '科研助理',
  INTERNAL_TOPIC_UNIT: '内部课题单位',
  EXTERNAL_TOPIC_UNIT: '外部课题单位',
};

function syncCompatibilityState(apiUser: ApiCurrentUser | null): void {
  if (!apiUser) {
    useAppStore.setState({ currentUser: null });
    return;
  }
  const roleName = roleNames[apiUser.roleCode];
  if (!roleName) throw new Error(`未识别的角色：${apiUser.roleCode}`);
  const roleId = `server-role-${apiUser.roleCode}`;
  const role: RbacRole = {
    id: roleId,
    code: apiUser.roleCode,
    name: roleName,
    description: '当前会话的服务端权限',
    pagePermissions: apiUser.pagePermissions as PagePermissionKey[],
    actionPermissions: apiUser.actionPermissions as ActionPermissionKey[],
    enabled: true,
    builtIn: false,
    createdAt: '',
    updatedAt: '',
  };
  const topicIds = [...new Set(apiUser.memberships.filter((item) => item.enabled).map((item) => item.topicId))];
  const user: User = {
    id: apiUser.id,
    username: apiUser.username,
    password: '',
    name: apiUser.username,
    unitId: apiUser.unitId,
    topicId: topicIds[0],
    topicIds,
    role: roleName,
    roleId,
    dataScope: apiUser.unitId ? 'TOPICS' : 'ALL',
    enabled: true,
    createdAt: '',
  };
  useAppStore.setState((state) => ({
    currentUser: user,
    users: [...state.users.filter((item) => item.id !== user.id), user],
    roles: [...state.roles.filter((item) => item.id !== role.id), role],
  }));
}

interface SessionState {
  user: ApiCurrentUser | null;
  status: SessionStatus;
  restore: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  expire: () => void;
}

export const useSessionStore = create<SessionState>((set, get) => ({
  user: null,
  status: 'idle',
  restore: async () => {
    if (get().status === 'loading' || get().status === 'authenticated') return;
    set({ status: 'loading' });
    try {
      const user = await authApi.me();
      syncCompatibilityState(user);
      set({ user, status: 'authenticated' });
    } catch {
      sessionStorage.removeItem('gzxm_access_token');
      syncCompatibilityState(null);
      set({ user: null, status: 'anonymous' });
    }
  },
  login: async (username, password) => {
    const result = await authApi.login(username, password);
    sessionStorage.setItem('gzxm_access_token', result.accessToken);
    syncCompatibilityState(result.user);
    set({ user: result.user, status: 'authenticated' });
  },
  logout: async () => {
    try { await authApi.logout(); }
    catch (error) { if (!(error instanceof ApiError && error.problem.status === 401)) throw error; }
    finally {
      sessionStorage.removeItem('gzxm_access_token');
      syncCompatibilityState(null);
      set({ user: null, status: 'anonymous' });
    }
  },
  expire: () => {
    sessionStorage.removeItem('gzxm_access_token');
    syncCompatibilityState(null);
    set({ user: null, status: 'anonymous' });
  },
}));
