export type ApiMode = 'mock' | 'real';

export const API_MODE: ApiMode = import.meta.env.VITE_API_MODE === 'real' ? 'real' : 'mock';
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

export function isRealApi(): boolean {
  return API_MODE === 'real';
}
