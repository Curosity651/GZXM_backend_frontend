import { apiRequest } from './http-client';
import type { ApiPage } from './system-api';

export interface TopicMember {
  id: string; topicId: string; unitId: string; unitName: string;
  membershipType: 'LEAD' | 'PARTICIPANT'; enabled: boolean; userIds: string[];
}
export interface ApiTopic {
  id: string; code: string; name: string; summary?: string; leadUnitId: string;
  status: 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'CLOSED'; enabled: boolean;
  startDate?: string; endDate?: string; recordVersion: number; members: TopicMember[];
}
export interface TopicWrite {
  code: string; name: string; summary?: string; leadUnitId: string;
  participantUnitIds: string[]; memberUserIds: Record<string, string[]>; startDate?: string; endDate?: string; recordVersion?: number;
}

/** Business pages must not expose configuration drafts as selectable topics. */
export const isBusinessTopic = (topic: ApiTopic) => topic.enabled && topic.status !== 'DRAFT';

export const topicApi = {
  list: (params = new URLSearchParams({ page: '1', size: '200' })) => apiRequest<ApiPage<ApiTopic>>(`/topics?${params}`),
  get: (id: string) => apiRequest<ApiTopic>(`/topics/${id}`),
  create: (data: TopicWrite) => apiRequest<ApiTopic>('/topics', { method: 'POST', body: JSON.stringify(data) }),
  update: (id: string, data: TopicWrite) => apiRequest<ApiTopic>(`/topics/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  setStatus: (id: string, enabled: boolean, status?: ApiTopic['status']) => apiRequest<ApiTopic>(`/topics/${id}/status`, { method: 'PUT', body: JSON.stringify({ enabled, status }) }),
  members: (id: string) => apiRequest<TopicMember[]>(`/topics/${id}/members`),
  addMember: (id: string, unitId: string, userIds: string[] = []) => apiRequest<TopicMember>(`/topics/${id}/members`, { method: 'POST', body: JSON.stringify({ unitId, userIds }) }),
  setMemberStatus: (topicId: string, membershipId: string, enabled: boolean) => apiRequest<TopicMember>(`/topics/${topicId}/members/${membershipId}/status`, { method: 'PUT', body: JSON.stringify({ enabled }) }),
};
