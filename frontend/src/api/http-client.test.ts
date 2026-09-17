// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiRequest } from './http-client';

describe('http client authentication headers', () => {
  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('does not attach a stale access token to the login request', async () => {
    sessionStorage.setItem('gzxm_access_token', 'stale-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ accessToken: 'new-token' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));

    await apiRequest('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: 'admin', password: 'password' }),
    }, false);

    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers);
    expect(headers.has('Authorization')).toBe(false);
  });
});
