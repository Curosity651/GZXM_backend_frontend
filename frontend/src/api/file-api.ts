import { API_BASE_URL } from './api-mode';
import { apiRequest } from './http-client';

export interface ApiFile { id: string; originalName: string; size: number; contentType: string; sha256?: string; status: string; uploaderId: string; createdAt: string }
interface UploadTicket { fileId: string; uploadUrl: string; method: string; headers: Record<string, string>; expiresAt: string }
interface SignedUrl { url: string; expiresAt: string }
const INLINE_TYPES = new Set(['application/pdf', 'image/png', 'image/jpeg', 'image/gif', 'image/webp']);

export function canPreviewFile(contentType: string): boolean {
  return INLINE_TYPES.has(contentType.split(';', 1)[0].trim().toLowerCase());
}

function authorizedHeaders(extra?: HeadersInit): Headers {
  const headers = new Headers(extra);
  const token = sessionStorage.getItem('gzxm_access_token');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return headers;
}

async function errorMessage(response: Response): Promise<string> {
  try { return ((await response.json()) as { detail?: string }).detail ?? `请求失败：${response.status}`; }
  catch { return `请求失败：${response.status}`; }
}

export const fileApi = {
  async upload(file: File, businessType = 'ARCHIVE'): Promise<ApiFile> {
    const ticket = await apiRequest<UploadTicket>('/files/upload-tickets', { method: 'POST', body: JSON.stringify({
      fileName: file.name, size: file.size, contentType: file.type || 'application/octet-stream', businessType,
    }) });
    const response = await fetch(ticket.uploadUrl, { method: ticket.method, headers: authorizedHeaders(ticket.headers), body: file });
    if (!response.ok) throw new Error(await errorMessage(response));
    return apiRequest<ApiFile>(`/files/${ticket.fileId}:complete`, { method: 'POST' });
  },
  async download(file: ApiFile, preview = false): Promise<void> {
    const safePreview = preview && canPreviewFile(file.contentType);
    const signed = await apiRequest<SignedUrl>(`/files/${file.id}/${safePreview ? 'preview-url' : 'download-url'}`);
    const url = signed.url.startsWith('/') ? signed.url : `${API_BASE_URL}${signed.url}`;
    const response = await fetch(url, { headers: authorizedHeaders() });
    if (!response.ok) throw new Error(await errorMessage(response));
    const blobUrl = URL.createObjectURL(await response.blob());
    if (safePreview) window.open(blobUrl, '_blank', 'noopener,noreferrer');
    else {
      const link = document.createElement('a'); link.href = blobUrl; link.download = file.originalName; link.click();
    }
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000);
  },
};
