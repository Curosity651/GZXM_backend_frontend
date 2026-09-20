import { describe, expect, it } from 'vitest';
import type { ApiAchievement } from '../api/achievement-api';
import type { IndicatorDefinition, IndicatorTarget, TimeNode } from '../api/indicator-api';
import { collectAllPages, completedAchievementCount, cumulativeBaseTarget } from './dashboard';

const nodes: TimeNode[] = [
  { id: 'n1', code: 'N1', name: '第一年度', deadline: '2026-12-31', sortOrder: 1, enabled: true },
  { id: 'n2', code: 'N2', name: '第二年度', deadline: '2027-12-31', sortOrder: 2, enabled: true },
];
const definitions: IndicatorDefinition[] = [
  { id: 'paper', code: 'PAPER', name: '论文', achievementType: 'PAPER', unit: '篇', category: 'BASE', enabled: true },
  { id: 'core', code: 'CORE', name: '中文核心', achievementType: 'PAPER', unit: '篇', category: 'SPECIAL', enabled: true },
  { id: 'patent', code: 'PATENT', name: '专利', achievementType: 'PATENT', unit: '项', category: 'BASE', enabled: true },
];
const target = (nodeId: string, indicatorDefinitionId: string, targetQuantity: number): IndicatorTarget => ({
  id: `${nodeId}-${indicatorDefinitionId}`, topicId: 't1', nodeId, indicatorDefinitionId,
  targetQuantity, status: 'PUBLISHED', version: 1,
});

describe('dashboard statistics', () => {
  it('accumulates base targets through the latest enabled node without double-counting special subsets', () => {
    expect(cumulativeBaseTarget(nodes, definitions, {
      n1: [target('n1', 'paper', 10), target('n1', 'core', 6), target('n1', 'patent', 5)],
      n2: [target('n2', 'paper', 7), target('n2', 'core', 4), target('n2', 'patent', 3)],
    })).toBe(25);
  });

  it('counts every recognized achievement regardless of its post-formal-review status', () => {
    const achievement = (id: string, status: string, countsToIndicator: boolean): ApiAchievement => ({
      id, topicId: 't1', unitId: 'u1', nodeId: 'n2', indicatorDefinitionId: 'paper', achievementType: 'PAPER',
      title: id, responsiblePerson: 'tester', status, countsToIndicator, recordVersion: 1, submittedVersion: 1,
      detail: {}, materials: [], materialLinks: [], approvals: [], createdAt: '', updatedAt: '',
    });
    expect(completedAchievementCount([
      achievement('published', 'WAIT_PUBLICATION', true),
      achievement('granted', 'WAIT_GRANT', true),
      achievement('effective', 'EFFECTIVE', true),
      achievement('draft', 'DRAFT', false),
    ])).toBe(3);
  });

  it('collects every server page instead of truncating dashboard data', async () => {
    const data = Array.from({ length: 405 }, (_, index) => index + 1);
    const result = await collectAllPages(async (page, size) => ({
      items: data.slice((page - 1) * size, page * size), page, size, total: data.length,
    }));
    expect(result).toHaveLength(405);
    expect(result.at(-1)).toBe(405);
  });
});
