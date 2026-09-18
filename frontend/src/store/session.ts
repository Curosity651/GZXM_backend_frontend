import { create } from 'zustand';
import { authApi, type ApiCurrentUser } from '../api/auth-api';
import { ApiError } from '../api/http-client';

type SessionStatus = 'idle' | 'loading' | 'authenticated' | 'anonymous';

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
      set({ user, status: 'authenticated' });
    } catch {
      sessionStorage.removeItem('gzxm_access_token');
      set({ user: null, status: 'anonymous' });
    }
  },
  login: async (username, password) => {
    const result = await authApi.login(username, password);
    sessionStorage.setItem('gzxm_access_token', result.accessToken);
    set({ user: result.user, status: 'authenticated' });
  },
  logout: async () => {
    try { await authApi.logout(); }
    catch (error) { if (!(error instanceof ApiError && error.problem.status === 401)) throw error; }
    finally {
      sessionStorage.removeItem('gzxm_access_token');
      set({ user: null, status: 'anonymous' });
    }
  },
  expire: () => {
    sessionStorage.removeItem('gzxm_access_token');
    set({ user: null, status: 'anonymous' });
  },
}));
