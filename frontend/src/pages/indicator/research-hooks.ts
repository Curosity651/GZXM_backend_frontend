import { useCallback, useEffect, useRef, useState } from 'react';

export function errorText(error: unknown): string { return error instanceof Error ? error.message : '请求失败，请重试'; }
export const topicStatuses: Record<string, string> = { ACTIVE: '实施中', DRAFT: '草稿', PAUSED: '已暂停', CLOSED: '已结题' };

export function useResearchLoad<T>(loader: () => Promise<T>, refreshKey = 0) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const refresh = useCallback(() => setRevision(value => value + 1), []);
  useEffect(() => {
    let current = true;
    setLoading(true); setData(undefined); setError('');
    loader().then(value => { if (current) setData(value); }).catch(reason => {
      if (current) setError(errorText(reason));
    }).finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [loader, revision, refreshKey]);
  return { data, loading, error, refresh };
}

export function useResearchMutation() {
  const locked = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const run = async (action: () => Promise<void>) => {
    if (locked.current) return;
    locked.current = true; setBusy(true); setError('');
    try { await action(); } catch (reason) { setError(errorText(reason)); }
    finally { locked.current = false; setBusy(false); }
  };
  return { busy, error, run };
}
