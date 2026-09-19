import { describe, expect, it } from 'vitest';
import homeSource from './HomePage.tsx?raw';
import achievementSource from './achievement/AchievementEntryPage.tsx?raw';
import reportSource from './report/ReportManagementPage.tsx?raw';
import archiveSource from './archive/ArchiveMonitoringPage.tsx?raw';
import archivePagesSource from './archive/RealArchivePages.tsx?raw';
import indicatorSource from './indicator/RealIndicatorPages.tsx?raw';

describe('A branch page design on real API contracts', () => {
  it('keeps the dashboard overview and task layout while loading server data', () => {
    const code = homeSource;
    expect(code).toContain('课题执行概览');
    expect(code).toContain('我的待办');
    expect(code).toContain("apiRequest<Summary>('/dashboard/summary')");
    expect(code).not.toContain('useAppStore');
  });

  it('keeps the complete achievement filter, progress and workflow UI on real APIs', () => {
    const code = achievementSource;
    expect(code).toContain('achievement-filter-grid');
    expect(code).toContain('成果进度');
    expect(code).toContain('提交预审');
    expect(code).toContain('achievementApi.create');
    expect(code).toContain('achievementApi.review');
    expect(code).not.toContain('useAppStore');
  });

  it('keeps report filters, progress, Excel form and approval operations on real APIs', () => {
    const code = reportSource;
    expect(code).toContain('月季报进度');
    expect(code).toContain('新建月季报');
    expect(code).toContain('<ReportForm');
    expect(code).toContain('reportApi.submit');
    expect(code).toContain('reportApi.review');
    expect(code).not.toContain('useAppStore');
  });

  it('keeps both archive progress sections and uses the scoped server progress endpoint', () => {
    const code = archiveSource;
    expect(code).toContain('课题国家材料');
    expect(code).toContain('配套自筹项目归档');
    expect(code).toContain('archiveApi.progress()');
    expect(code).not.toContain('useAppStore');
  });

  it('excludes draft topics from achievement, report and archive business selectors', () => {
    expect(achievementSource).toContain('filter(isBusinessTopic)');
    expect(reportSource).toContain("topic.status !== 'DRAFT'");
    expect(archiveSource).toContain('filter(isBusinessTopic)');
    expect(archivePagesSource).toContain('filter(isBusinessTopic)');
  });

  it('uses complete-plan allocation confirmation and configurable time-node deletion', () => {
    expect(indicatorSource).not.toContain('title="承担单位维护"');
    expect(indicatorSource).not.toContain('课题目标由科研助理下发，牵头单位按照时间节点分配至各参与单位。');
    expect(indicatorSource).not.toContain('saveAllocations(false)');
    expect(indicatorSource).toContain('提交全部分配方案');
    expect(indicatorSource).toContain('此阶段分配情况');
    expect(indicatorSource).toContain('indicatorApi.confirmAllocationPlan');
    expect(indicatorSource).toContain('截至本阶段累计');
    expect(indicatorSource).toContain('indicatorApi.deleteNode');
  });
});
