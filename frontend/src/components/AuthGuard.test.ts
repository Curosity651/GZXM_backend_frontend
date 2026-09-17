// @vitest-environment jsdom
import React from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthGuard } from './AuthGuard';
import { useSessionStore } from '../store/session';
import type { ApiCurrentUser } from '../api/auth-api';

const user = (pagePermissions: string[]): ApiCurrentUser => ({
  id: '1', username: 'admin', roleCode: 'SYSTEM_ADMIN', pagePermissions,
  actionPermissions: ['system.manage'], memberships: [],
});

function renderProtectedPage() {
  return render(React.createElement(MemoryRouter, { initialEntries: ['/admin/users'] },
    React.createElement(Routes, null,
      React.createElement(Route, { path: '/login', element: React.createElement('div', null, '登录页') }),
      React.createElement(Route, {
        path: '/admin/users',
        element: React.createElement(AuthGuard, null, React.createElement('div', null, '用户管理内容')),
      }),
    ),
  ));
}

describe('AuthGuard server permissions', () => {
  afterEach(() => {
    cleanup();
    sessionStorage.clear();
    useSessionStore.setState({ user: null, status: 'anonymous' });
  });

  it('allows a page included in the current server session', () => {
    useSessionStore.setState({ user: user(['user-management']), status: 'authenticated' });
    renderProtectedPage();
    expect(screen.getByText('用户管理内容')).toBeTruthy();
  });

  it('rejects a page absent from the current server session', () => {
    useSessionStore.setState({ user: user(['home']), status: 'authenticated' });
    renderProtectedPage();
    expect(screen.getByText('无权访问')).toBeTruthy();
    expect(screen.queryByText('用户管理内容')).toBeNull();
  });
});
