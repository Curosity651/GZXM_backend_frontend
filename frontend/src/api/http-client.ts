import { API_BASE_URL } from './api-mode';

export interface ApiProblem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code?: string;
  fieldErrors?: Array<{ field: string; message: string }>;
}

export class ApiError extends Error {
  readonly problem: ApiProblem;

  constructor(problem: ApiProblem) {
    super(problem.detail ?? problem.title ?? `请求失败（${problem.status}）`);
    this.problem = problem;
  }
}

type AccessTokenProvider = () => string | null;
type AccessTokenUpdater = (token: string | null) => void;

let getAccessToken: AccessTokenProvider = () => sessionStorage.getItem('gzxm_access_token');
let setAccessToken: AccessTokenUpdater = (token) => {
  if (token) sessionStorage.setItem('gzxm_access_token', token);
  else sessionStorage.removeItem('gzxm_access_token');
};
let refreshPromise: Promise<string | null> | null = null;

export function configureSession(provider: AccessTokenProvider, updater: AccessTokenUpdater): void {
  getAccessToken = provider;
  setAccessToken = updater;
}

async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
    }).then(async (response) => {
      if (!response.ok) return null;
      const data = await response.json() as { accessToken: string };
      setAccessToken(data.accessToken);
      return data.accessToken;
    }).finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}

async function parseProblem(response: Response): Promise<ApiProblem> {
  try {
    return await response.json() as ApiProblem;
  } catch {
    return { status: response.status, title: response.statusText, code: 'HTTP_ERROR' };
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has('Content-Type') && init.body) headers.set('Content-Type', 'application/json');
  const token = getAccessToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers, credentials: 'include' });
  if (response.status === 401 && retry && path !== '/auth/refresh') {
    const refreshed = await refreshAccessToken();
    if (refreshed) return apiRequest<T>(path, init, false);
    setAccessToken(null);
    window.dispatchEvent(new CustomEvent('gzxm:session-expired'));
  }
  if (!response.ok) throw new ApiError(await parseProblem(response));
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
