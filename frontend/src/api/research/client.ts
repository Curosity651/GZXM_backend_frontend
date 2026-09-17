import { apiRequest } from '../http-client';
import type * as C from './contracts';

export type Query = Record<string, string | number | boolean | undefined>;
export function query(values: Query = {}): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) if (value !== undefined && value !== '') params.set(key, String(value));
  return params.size ? `?${params}` : '';
}
const segment = (id: string) => encodeURIComponent(id);
const json = (method: string, body: unknown, key?: string): RequestInit => ({
  method, body: JSON.stringify(body), ...(key ? { headers: { 'Idempotency-Key': key } } : {}),
});

/** A can supply the shared header-preserving request implementation after its foundation review. */
export type MetadataRequest = <T>(path: string, init?: RequestInit) => Promise<{ data: T; headers: Headers }>;
let metadataRequest: MetadataRequest | undefined;
export function configureResearchMetadataRequest(request?: MetadataRequest): void { metadataRequest = request; }
export function hasResearchMetadataRequest(): boolean { return Boolean(metadataRequest); }
function version(headers: Headers, name: string): number {
  const raw = headers.get(name);
  if (raw === null || !/^\d+$/.test(raw) || !Number.isSafeInteger(Number(raw)) || Number(raw) > 2147483647) throw new Error(`未收到有效 ${name}，请检查公共 HTTP 能力及响应头配置`);
  return Number(raw);
}
export interface Draft<T> { items: T[]; draftVersion: number; topicIndicatorVersion?: number }
async function draft<T>(path: string, init?: RequestInit, allocation = false): Promise<Draft<T>> {
  if (!metadataRequest) throw new Error('指标草稿编辑等待公共 HTTP 响应头能力接入；不能从指标行推断草稿版本');
  const response = await metadataRequest<T[]>(path, init);
  return { items: response.data, draftVersion: version(response.headers, 'X-Draft-Version'),
    ...(allocation ? { topicIndicatorVersion: version(response.headers, 'X-Topic-Indicator-Version') } : {}) };
}

export const researchApi = {
  topics: (filters: Query = {}) => apiRequest<C.TopicPage>(`/topics${query(filters)}`),
  topic: (id: string) => apiRequest<C.Topic>(`/topics/${segment(id)}`),
  createTopic: (body: C.TopicWriteRequest) => apiRequest<C.Topic>('/topics', json('POST', body)),
  updateTopic: (id: string, body: C.TopicWriteRequest) => apiRequest<C.Topic>(`/topics/${segment(id)}`, json('PUT', body)),
  topicStatus: (id: string, body: { enabled: boolean; status?: C.TopicStatus }) => apiRequest<C.Topic>(`/topics/${segment(id)}/status`, json('PUT', body)),
  members: (id: string) => apiRequest<C.TopicMembership[]>(`/topics/${segment(id)}/members`),
  addMember: (id: string, unitId: string) => apiRequest<C.TopicMembership>(`/topics/${segment(id)}/members`, json('POST', { unitId })),
  memberStatus: (id: string, memberId: string, enabled: boolean) => apiRequest<C.TopicMembership>(`/topics/${segment(id)}/members/${segment(memberId)}/status`, json('PUT', { enabled })),
  units: () => apiRequest<C.Unit[]>('/units'),
  nodes: () => apiRequest<C.TimeNode[]>('/time-nodes'),
  definitions: () => apiRequest<C.IndicatorDefinition[]>('/indicator-definitions'),
  targets: (id: string, nodeId: string) => apiRequest<C.TopicIndicator[]>(`/topics/${segment(id)}/indicator-targets${query({ nodeId })}`),
  targetDraft: (id: string, nodeId: string) => draft<C.TopicIndicator>(`/topics/${segment(id)}/indicator-targets${query({ nodeId, view: 'draft' })}`),
  saveTargets: (id: string, body: C.IndicatorTargetBatch) => draft<C.TopicIndicator>(`/topics/${segment(id)}/indicator-targets`, json('PUT', body)),
  publishTargets: (id: string, body: { nodeId: string; draftVersion: number }, key: string) => apiRequest<void>(`/topics/${segment(id)}/indicator-targets:publish`, json('POST', body, key)),
  allocations: (id: string, nodeId: string) => apiRequest<C.UnitIndicatorAllocation[]>(`/topics/${segment(id)}/unit-allocations${query({ nodeId })}`),
  allocationDraft: (id: string, nodeId: string) => draft<C.UnitIndicatorAllocation>(`/topics/${segment(id)}/unit-allocations${query({ nodeId, view: 'draft' })}`, undefined, true),
  saveAllocations: (id: string, body: C.UnitAllocationBatch) => draft<C.UnitIndicatorAllocation>(`/topics/${segment(id)}/unit-allocations`, json('PUT', body), true),
  publishAllocations: (id: string, body: { nodeId: string; draftVersion: number }, key: string) => apiRequest<void>(`/topics/${segment(id)}/unit-allocations:publish`, json('POST', body, key)),
  achievements: (filters: Query = {}) => apiRequest<C.AchievementPage>(`/achievements${query(filters)}`),
  achievement: (id: string) => apiRequest<C.Achievement>(`/achievements/${segment(id)}`),
  createAchievement: (body: C.AchievementWriteRequest) => apiRequest<C.Achievement>('/achievements', json('POST', body)),
  updateAchievement: (id: string, body: C.AchievementWriteRequest) => apiRequest<C.Achievement>(`/achievements/${segment(id)}`, json('PUT', body)),
  action: (id: string, body: C.AchievementActionRequest, key: string) => apiRequest<C.Achievement>(`/achievements/${segment(id)}/actions`, json('POST', body, key)),
  review: (id: string, body: C.AchievementReviewRequest, key: string) => apiRequest<C.ApprovalRecord>(`/achievements/${segment(id)}/reviews`, json('POST', body, key)),
  snapshots: (id: string) => apiRequest<C.SubmissionSnapshot[]>(`/achievements/${segment(id)}/snapshots`),
  progress: (nodeId: string, topicId?: string, unitId?: string) => apiRequest<C.AchievementProgress>(`/achievement-progress${query({ nodeId, topicId, unitId })}`),
};

/** Reuse a key for an identical retry, including after an uncertain network failure. Never persist credentials. */
export class ResearchOperationKeys {
  private keys = new Map<string, string>();
  forRequest(operation: string, body: unknown): string {
    const signature = JSON.stringify([operation, body]);
    let key = this.keys.get(signature);
    if (!key) { key = crypto.randomUUID(); this.keys.set(signature, key); }
    return key;
  }
}

export async function allTopics(): Promise<C.Topic[]> {
  const items: C.Topic[] = [];
  for (let page = 1; ; page++) {
    const result = await researchApi.topics({ page, size: 100 });
    items.push(...result.items);
    if (items.length >= result.total || result.items.length === 0) return items;
  }
}
