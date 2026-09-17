import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Alert, Button, Spin } from 'antd';
import { authApi, type ApiCurrentUser } from '../../api/auth-api';

/** B consumes A's current-user endpoint; never promotes Mock Store identity to a real session. */
export function ResearchSession({ page, children }: { page: string; children: (user: ApiCurrentUser) => ReactNode }) {
  const [user, setUser] = useState<ApiCurrentUser>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const retry = useCallback(() => setRevision(value => value + 1), []);
  useEffect(() => {
    let current = true;
    setLoading(true); setUser(undefined); setError('');
    authApi.me().then(value => { if (current) setUser(value); }).catch(reason => {
      if (current) setError(reason instanceof Error ? reason.message : '无法读取真实登录身份');
    }).finally(() => { if (current) setLoading(false); });
    const expired = () => { setUser(undefined); setError('真实会话已失效，请重新登录'); };
    window.addEventListener('gzxm:session-expired', expired);
    return () => { current = false; window.removeEventListener('gzxm:session-expired', expired); };
  }, [revision]);
  if (loading) return <Spin description="读取登录身份"><div style={{ height: 100 }} /></Spin>;
  if (error || !user) return <Alert type="error" showIcon title="真实业务暂不可用" description={<>{error}。公共登录与会话接入尚待 A 提供；本页面不会使用演示身份读取或写入业务。<Button onClick={retry}>重试</Button></>} />;
  if (!user.pagePermissions.includes(page)) return <Alert type="error" title="无权访问此页面" description="当前真实账号没有该页面权限。" />;
  return <>{children(user)}</>;
}
