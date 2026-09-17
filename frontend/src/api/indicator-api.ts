import { apiRequest, apiRequestDetailed } from './http-client';

export interface TimeNode { id: string; name: string; deadline: string; sortOrder: number; enabled: boolean }
export interface IndicatorDefinition { id: string; code: string; name: string; achievementType: string; unit: string; category: 'BASE' | 'SPECIAL'; enabled: boolean }
export interface IndicatorTarget { id: string; topicId: string; nodeId: string; indicatorDefinitionId: string; targetQuantity: number; status: string; version: number }
export interface UnitAllocation { id: string; topicId: string; unitId: string; nodeId: string; indicatorDefinitionId: string; targetQuantity: number; status: string; version: number }
export interface VersionedRows<T> { rows: T[]; draftVersion: number; topicIndicatorVersion?: number }

const version = (response: Response, name: string) => Number(response.headers.get(name) ?? '0');
const key = () => `web-${crypto.randomUUID()}`;

export const indicatorApi = {
  nodes: () => apiRequest<TimeNode[]>('/time-nodes'),
  definitions: () => apiRequest<IndicatorDefinition[]>('/indicator-definitions'),
  targets: async (topicId: string, nodeId: string, view: 'effective' | 'draft' = 'effective'): Promise<VersionedRows<IndicatorTarget>> => {
    const { data, response } = await apiRequestDetailed<IndicatorTarget[]>(`/topics/${topicId}/indicator-targets?nodeId=${nodeId}&view=${view}`);
    return { rows: data, draftVersion: version(response, 'X-Draft-Version') };
  },
  saveTargets: async (topicId: string, nodeId: string, draftVersion: number, targets: Array<{ indicatorDefinitionId: string; targetQuantity: number }>): Promise<VersionedRows<IndicatorTarget>> => {
    const { data, response } = await apiRequestDetailed<IndicatorTarget[]>(`/topics/${topicId}/indicator-targets`, { method: 'PUT', body: JSON.stringify({ nodeId, draftVersion, targets }) });
    return { rows: data, draftVersion: version(response, 'X-Draft-Version') };
  },
  publishTargets: (topicId: string, nodeId: string, draftVersion: number) => apiRequest<void>(`/topics/${topicId}/indicator-targets:publish`, { method: 'POST', headers: { 'Idempotency-Key': key() }, body: JSON.stringify({ nodeId, draftVersion }) }),
  allocations: async (topicId: string, nodeId: string, view: 'effective' | 'draft' = 'effective'): Promise<VersionedRows<UnitAllocation>> => {
    const { data, response } = await apiRequestDetailed<UnitAllocation[]>(`/topics/${topicId}/unit-allocations?nodeId=${nodeId}&view=${view}`);
    return { rows: data, draftVersion: version(response, 'X-Draft-Version'), topicIndicatorVersion: version(response, 'X-Topic-Indicator-Version') };
  },
  saveAllocations: async (topicId: string, nodeId: string, draftVersion: number, allocations: Array<{ unitId: string; indicatorDefinitionId: string; targetQuantity: number }>): Promise<VersionedRows<UnitAllocation>> => {
    const { data, response } = await apiRequestDetailed<UnitAllocation[]>(`/topics/${topicId}/unit-allocations`, { method: 'PUT', body: JSON.stringify({ nodeId, draftVersion, allocations }) });
    return { rows: data, draftVersion: version(response, 'X-Draft-Version'), topicIndicatorVersion: version(response, 'X-Topic-Indicator-Version') };
  },
  publishAllocations: (topicId: string, nodeId: string, draftVersion: number) => apiRequest<void>(`/topics/${topicId}/unit-allocations:publish`, { method: 'POST', headers: { 'Idempotency-Key': key() }, body: JSON.stringify({ nodeId, draftVersion }) }),
};
