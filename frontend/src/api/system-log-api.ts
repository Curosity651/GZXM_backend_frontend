import { apiRequest } from './http-client';
import type { ApiPage } from './system-api';

export interface ApiSystemErrorLog {
  id: number; traceId: string; severity: 'WARN' | 'ERROR'; userId?: number; username?: string;
  httpMethod: string; requestPath: string; statusCode: number; errorCode?: string;
  errorMessage?: string; exceptionClass?: string; stackSummary?: string; durationMs: number;
  clientIp?: string; userAgent?: string; createdAt: string;
}

export const systemLogApi = {
  list: (params: URLSearchParams) => apiRequest<ApiPage<ApiSystemErrorLog>>(`/system-logs?${params.toString()}`),
};
