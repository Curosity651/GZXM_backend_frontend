import { apiRequest } from './http-client';

export interface ReportRule {
  effectiveYear: number; monthlyEnabled: boolean; monthlyOpenDay: number; monthlyDeadlineDay: number;
  quarterlyEnabled: boolean; quarterlyOpenDay: number; quarterlyDeadlineDay: number;
  quarterlyMonths: number[]; recordVersion?: number;
}
export interface ApiReport {
  id: string; topicId: string; reportType: 'MONTHLY' | 'QUARTERLY'; year: number; period: number;
  openDate: string; deadline: string; basicInformation: string; milestoneProgress: string; overallProgress: string;
  researchAchievements: string;
  demonstrationProgress: string; fundUsage: string; nextPlan: string; problemsAndMeasures: string;
  status: 'DRAFT' | 'INITIAL_REVIEW' | 'FINAL_REVIEW' | 'APPROVED' | 'RETURNED';
  overdue: boolean; recordVersion: number; submittedVersion: number; submittedAt?: string;
}
export type ReportContent = Pick<ApiReport, 'basicInformation' | 'milestoneProgress' | 'overallProgress' | 'researchAchievements' | 'demonstrationProgress' | 'fundUsage' | 'nextPlan' | 'problemsAndMeasures' | 'recordVersion'>;
export interface ApiPage<T> { items: T[]; page: number; size: number; total: number }
export interface ApiApproval { id: string; decision: string; level: string; opinion?: string; submittedVersion: number; operatedAt: string }
export interface ApiSnapshot { id: string; submittedVersion: number; submittedAt: string; payload: Record<string, unknown> }

export const reportApi = {
  list: (params: URLSearchParams = new URLSearchParams()) => apiRequest<ApiPage<ApiReport>>(`/reports?${params}`),
  get: (id: string) => apiRequest<ApiReport>(`/reports/${id}`),
  create: (data: { topicId: string; reportType: 'MONTHLY' | 'QUARTERLY'; year: number; period: number }) =>
    apiRequest<ApiReport>('/reports', { method: 'POST', body: JSON.stringify(data) }),
  save: (id: string, data: ReportContent) => apiRequest<ApiReport>(`/reports/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  submit: (id: string) => apiRequest<ApiReport>(`/reports/${id}:submit`, { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() } }),
  review: (id: string, data: { decision: 'APPROVE' | 'RETURN'; opinion?: string; submittedVersion: number }) =>
    apiRequest<ApiApproval>(`/reports/${id}/reviews`, { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(data) }),
  snapshots: (id: string) => apiRequest<ApiSnapshot[]>(`/reports/${id}/snapshots`),
  rule: (topicId: string) => apiRequest<ReportRule>(`/topics/${topicId}/report-rule`),
  saveRule: (topicId: string, rule: ReportRule) => apiRequest<ReportRule>(`/topics/${topicId}/report-rule`, { method: 'PUT', body: JSON.stringify(rule) }),
  progress: (topicId?: string) => apiRequest<{ total: number; submitted: number; approved: number; overdue: number }>(`/report-progress${topicId ? `?topicId=${topicId}` : ''}`),
};
