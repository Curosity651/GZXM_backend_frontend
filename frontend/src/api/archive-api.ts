import { apiRequest } from './http-client';
import type { ApiFile } from './file-api';

export interface ApiArchiveDirectory { topicId: string; topicName: string; unitId: string; unitName: string; folderCount: number; completedCount: number; completionRate: number }
export interface ApiArchiveFolder { id: string; topicId: string; unitId: string; ownerType: 'TOPIC_NATIONAL' | 'SELF_FUNDED'; ownerId: string; name: string; required: boolean; requiredQuantity: number; custom: boolean; fileCount: number; completed: boolean; canDelete: boolean }
export interface ApiSelfFundedProject { id: string; topicId: string; ownerUnitId: string; code: string; name: string; projectType: 'TECHNOLOGY' | 'RENOVATION' | 'INFRASTRUCTURE'; principalName: string; startDate?: string; endDate?: string; budget?: number; status: string; recordVersion: number; templateSnapshotId: string; completionRate: number }
export type SelfFundedWrite = Pick<ApiSelfFundedProject, 'topicId' | 'code' | 'name' | 'projectType' | 'principalName'> &
  { startDate: string; endDate: string } & Partial<Pick<ApiSelfFundedProject, 'budget' | 'status' | 'recordVersion'>>;
export interface ApiArchiveProgress { topicId: string; unitId: string; ownerType: string; requiredCount: number; completedCount: number; completionRate: number }

export const archiveApi = {
  directories: () => apiRequest<ApiArchiveDirectory[]>('/archive/national'),
  nationalFolders: (topicId: string, unitId: string) => apiRequest<ApiArchiveFolder[]>(`/archive/national/topics/${topicId}/units/${unitId}/folders`),
  addNationalFolder: (topicId: string, unitId: string, name: string, required: boolean) => apiRequest<ApiArchiveFolder>(`/archive/national/topics/${topicId}/units/${unitId}/folders`, { method: 'POST', body: JSON.stringify({ name, required }) }),
  deleteFolder: (id: string) => apiRequest<void>(`/archive/folders/${id}`, { method: 'DELETE' }),
  files: (folderId: string) => apiRequest<ApiFile[]>(`/archive/folders/${folderId}/files`),
  attach: (folderId: string, fileId: string) => apiRequest<ApiFile>(`/archive/folders/${folderId}/files`, { method: 'POST', body: JSON.stringify({ fileId }) }),
  remove: (folderId: string, fileId: string) => apiRequest<void>(`/archive/folders/${folderId}/files/${fileId}`, { method: 'DELETE' }),
  projects: () => apiRequest<ApiSelfFundedProject[]>('/self-funded-projects'),
  createProject: (data: SelfFundedWrite) => apiRequest<ApiSelfFundedProject>('/self-funded-projects', { method: 'POST', body: JSON.stringify(data) }),
  updateProject: (id: string, data: SelfFundedWrite) => apiRequest<ApiSelfFundedProject>(`/self-funded-projects/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  projectFolders: (id: string) => apiRequest<ApiArchiveFolder[]>(`/self-funded-projects/${id}/folders`),
  progress: () => apiRequest<ApiArchiveProgress[]>('/archive-progress'),
};
