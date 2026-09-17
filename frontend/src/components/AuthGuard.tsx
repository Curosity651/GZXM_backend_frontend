import { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Result, Spin } from 'antd';
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
};

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
  if (page && !user.pagePermissions.includes(page)) {
    return <Result status="403" title="无权访问" subTitle="当前角色没有该页面权限，请从左侧菜单进入可用功能。" />;
  }

  return <>{children}</>;
}
