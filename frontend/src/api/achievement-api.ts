import { apiRequest } from './http-client';
import type { ApiFile } from './file-api';

export interface AchievementApproval {
  id: string; stage: string; level: string; decision: 'APPROVED' | 'RETURNED'; opinion?: string;
  operatorId: string; operatedAt: string; submittedVersion: number;
}

export interface ApiAchievement {
  id: string; topicId: string; unitId: string; nodeId: string; indicatorDefinitionId: string;
  achievementType: 'PAPER' | 'PATENT' | 'COPYRIGHT' | 'STANDARD' | 'TALENT';
  title: string; responsiblePerson: string; status: string; countsToIndicator: boolean;
  recordVersion: number; submittedVersion: number; detail: Record<string, unknown>;
  materials: ApiFile[]; materialLinks: Array<{ id: string; fileId: string; materialType: string; version: number; active: boolean; status: string }>;
  createdAt: string; updatedAt: string; approvals: AchievementApproval[];
}

export interface AchievementWrite {
  topicId: string; nodeId: string; indicatorDefinitionId: string; title: string; responsiblePerson: string;
  detail: Record<string, unknown>; recordVersion?: number;
  materialAttachments?: Array<{ fileId: string; materialType: string }>;
}

export interface AchievementProgress {
  nodeId: string; countingBasis: string;
  baseTotals: Record<string, number>;
  baseStages: { initiated: number; submitted: number; preApproved: number; external: number; formal: number; supplement: number; effective: number };
  specialIndicators: AchievementProgressRow[];
  rows: AchievementProgressRow[];
}

export interface AchievementProgressRow {
  scope: 'TOPIC' | 'UNIT'; topicId: string; unitId?: string; nodeId: string; indicatorDefinitionId: string;
  achievementType: string; targetQuantity?: number; targetVersion?: number; targetPublished: boolean; hasTarget: boolean;
  completionRate?: number; historical: boolean;
  stages: { initiated: number; submitted: number; preApproved: number; external: number; formal: number; supplement: number; effective: number };
}

interface Page<T> { items: T[]; page: number; size: number; total: number }
const key = () => `web-${crypto.randomUUID()}`;
const query = (params: Record<string, string | boolean | undefined>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([name, value]) => { if (value !== undefined && value !== '') search.set(name, String(value)); });
  return search.toString();
};

export const achievementApi = {
  list: (filters: Record<string, string | boolean | undefined> = {}) => apiRequest<Page<ApiAchievement>>(`/achievements?${query({ page: '1', size: '200', ...filters })}`),
  get: (id: string) => apiRequest<ApiAchievement>(`/achievements/${id}`),
  create: (body: AchievementWrite) => apiRequest<ApiAchievement>('/achievements', { method: 'POST', body: JSON.stringify(body) }),
  update: (id: string, body: AchievementWrite) => apiRequest<ApiAchievement>(`/achievements/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  action: (id: string, body: { action: string; recordVersion: number; externalSubmissionDate?: string; externalSubmissionNumber?: string }) => apiRequest<ApiAchievement>(`/achievements/${id}/actions`, { method: 'POST', headers: { 'Idempotency-Key': key() }, body: JSON.stringify(body) }),
  review: (id: string, body: { decision: 'APPROVE' | 'RETURN'; opinion?: string; submittedVersion: number; recordVersion: number }) => apiRequest<AchievementApproval>(`/achievements/${id}/reviews`, { method: 'POST', headers: { 'Idempotency-Key': key() }, body: JSON.stringify(body) }),
  progress: (nodeId: string, topicId?: string, unitId?: string) => apiRequest<AchievementProgress>(`/achievement-progress?${query({ nodeId, topicId, unitId })}`),
};
