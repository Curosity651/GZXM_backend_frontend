import { apiRequest } from './http-client';

export interface ReportRule {
  effectiveYear: number; monthlyEnabled: boolean;
  monthlyStartYear?: number; monthlyStartPeriod?: number; monthlyEndYear?: number; monthlyEndPeriod?: number;
  monthlyOpenDay: number; monthlyDeadlineDay: number;
  quarterlyEnabled: boolean;
  quarterlyStartYear?: number; quarterlyStartPeriod?: number; quarterlyEndYear?: number; quarterlyEndPeriod?: number;
  quarterlyOpenDay: number; quarterlyDeadlineDay: number;
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
export interface ApiApproval {
  id: string; businessType: string; businessId: string; stage: string; level: string;
  decision: string; opinion?: string; operatorId: string; submittedVersion: number; operatedAt: string;
}
export interface ApiSnapshot { id: string; submittedVersion: number; submittedAt: string; payload: Record<string, unknown> }
export interface ReportProgressPeriod {
  reportId?: string; reportType: 'MONTHLY' | 'QUARTERLY'; year: number; period: number;
  openDate: string; deadline: string;
  status: ApiReport['status'] | 'NOT_OPEN' | 'NOT_CREATED'; timing: 'UPCOMING' | 'NORMAL' | 'OVERDUE';
  submittedVersion: number;
}
export interface ReportProgressTopic {
  topicId: string; topicCode: string; topicName: string; expected: number; submitted: number;
  approved: number; missing: number; overdue: number; periods: ReportProgressPeriod[];
}
export interface ReportProgress { year: number; topics: ReportProgressTopic[] }

export const reportApi = {
  list: (params: URLSearchParams = new URLSearchParams()) => apiRequest<ApiPage<ApiReport>>(`/reports?${params}`),
  get: (id: string) => apiRequest<ApiReport>(`/reports/${id}`),
  create: (data: { topicId: string; reportType: 'MONTHLY' | 'QUARTERLY'; year: number; period: number }) =>
    apiRequest<ApiReport>('/reports', { method: 'POST', body: JSON.stringify(data) }),
  save: (id: string, data: ReportContent) => apiRequest<ApiReport>(`/reports/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  submit: (id: string) => apiRequest<ApiReport>(`/reports/${id}:submit`, { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() } }),
  review: (id: string, data: { decision: 'APPROVE' | 'RETURN'; opinion?: string; submittedVersion: number }) =>
    apiRequest<ApiApproval>(`/reports/${id}/reviews`, { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(data) }),
  approvals: (id: string) => apiRequest<ApiApproval[]>(`/reports/${id}/reviews`),
  snapshots: (id: string) => apiRequest<ApiSnapshot[]>(`/reports/${id}/snapshots`),
  rule: (topicId: string, effectiveYear?: number) => apiRequest<ReportRule>(`/topics/${topicId}/report-rule${effectiveYear ? `?effectiveYear=${effectiveYear}` : ''}`),
  saveRule: (topicId: string, rule: ReportRule) => apiRequest<ReportRule>(`/topics/${topicId}/report-rule`, { method: 'PUT', body: JSON.stringify(rule) }),
  progress: (filters: { topicId?: string; year?: number; reportType?: 'MONTHLY' | 'QUARTERLY' } = {}) => {
    const params = new URLSearchParams();
    if (filters.topicId) params.set('topicId', filters.topicId);
    if (filters.year) params.set('year', String(filters.year));
    if (filters.reportType) params.set('reportType', filters.reportType);
    return apiRequest<ReportProgress>(`/report-progress${params.size ? `?${params}` : ''}`);
  },
};
