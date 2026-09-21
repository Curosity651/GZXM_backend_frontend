import { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { type PageKey } from '../domain/permissions';
import { useSessionStore } from '../store/session';

const routePermissions: Record<string, PageKey> = {
  '/': 'home',
  '/indicator': 'topic-indicator',
  '/indicator/topic': 'topic-indicator',
  '/achievement-entry': 'achievement-entry',
  '/reports': 'report-management',
  '/archive/topics': 'topic-archive', '/archive/self-funded': 'self-funded-archive',
  '/archive/monitoring': 'archive-monitoring', '/admin/users': 'user-management', '/admin/roles': 'role-permission',
  '/admin/config': 'system-config',
  '/admin/logs': 'system-log',
};

const pageRoutes: Array<[PageKey, string]> = [
  ['topic-indicator', '/indicator'], ['achievement-entry', '/achievement-entry'],
  ['report-management', '/reports'], ['topic-archive', '/archive/topics'],
  ['self-funded-archive', '/archive/self-funded'], ['archive-monitoring', '/archive/monitoring'],
  ['user-management', '/admin/users'], ['role-permission', '/admin/roles'], ['system-log', '/admin/logs'],
];

function firstAccessibleRoute(pagePermissions: string[]) {
  return pageRoutes.find(([page]) => page === 'user-management' || pagePermissions.includes(page))?.[1] ?? '/admin/users';
}

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const user = useSessionStore((state) => state.user);
  const status = useSessionStore((state) => state.status);
  const restore = useSessionStore((state) => state.restore);
  const expire = useSessionStore((state) => state.expire);
  const location = useLocation();

  useEffect(() => { void restore(); }, [restore]);
  useEffect(() => {
    window.addEventListener('gzxm:session-expired', expire);
    return () => window.removeEventListener('gzxm:session-expired', expire);
  }, [expire]);

  if (status === 'idle' || status === 'loading') {
    return <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}><Spin size="large" /></div>;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const page = routePermissions[location.pathname] ?? Object.entries(routePermissions).find(([path]) => path !== '/' && location.pathname.startsWith(`${path}/`))?.[1];
  const permitted = page === 'user-management' || !page || user.pagePermissions.includes(page);
  if (!permitted || (location.pathname === '/' && !user.pagePermissions.includes('home'))) {
    return <Navigate to={firstAccessibleRoute(user.pagePermissions)} replace />;
  }

  return <>{children}</>;
}
