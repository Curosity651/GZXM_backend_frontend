import { API_BASE_URL } from './api-config';

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

async function executeRequest(path: string, init: RequestInit = {}, retry = true): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!headers.has('Content-Type') && init.body) headers.set('Content-Type', 'application/json');
  const token = getAccessToken();
  // 登录接口必须保持匿名。浏览器里可能残留上一次运行签发的旧令牌，
  // 若继续携带，JWT 过滤器会在校验账号密码前直接拒绝请求。
  if (token && path !== '/auth/login') headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers, credentials: 'include' });
  if (response.status === 401 && retry && path !== '/auth/refresh') {
    const refreshed = await refreshAccessToken();
    if (refreshed) return executeRequest(path, init, false);
    setAccessToken(null);
    window.dispatchEvent(new CustomEvent('gzxm:session-expired'));
  }
  if (!response.ok) throw new ApiError(await parseProblem(response));
  return response;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const response = await executeRequest(path, init, retry);
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function apiRequestDetailed<T>(path: string, init: RequestInit = {}): Promise<{ data: T; response: Response }> {
  const response = await executeRequest(path, init);
  const data = response.status === 204 ? undefined as T : await response.json() as T;
  return { data, response };
}
