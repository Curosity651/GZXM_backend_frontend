import type { ApiAchievement } from '../api/achievement-api';
import type { IndicatorDefinition, IndicatorTarget, TimeNode } from '../api/indicator-api';

export const latestEnabledNode = (nodes: TimeNode[]) => [...nodes]
  .filter((node) => node.enabled)
  .sort((left, right) => left.sortOrder - right.sortOrder)
  .at(-1);

export const cumulativeBaseTarget = (
  nodes: TimeNode[],
  definitions: IndicatorDefinition[],
  targetsByNode: Record<string, IndicatorTarget[]>,
) => {
  const selected = latestEnabledNode(nodes);
  if (!selected) return 0;
  const baseIds = new Set(definitions.filter((definition) => definition.category === 'BASE').map((definition) => definition.id));
  return nodes
    .filter((node) => node.enabled && node.sortOrder <= selected.sortOrder)
    .flatMap((node) => targetsByNode[node.id] ?? [])
    .filter((target) => baseIds.has(target.indicatorDefinitionId))
    .reduce((sum, target) => sum + target.targetQuantity, 0);
};

export const completedAchievementCount = (achievements: ApiAchievement[], topicId?: string) => achievements
  .filter((achievement) => achievement.countsToIndicator && (!topicId || achievement.topicId === topicId))
  .length;

interface Page<T> { items: T[]; page: number; size: number; total: number }

export async function collectAllPages<T>(loader: (page: number, size: number) => Promise<Page<T>>, size = 200) {
  const first = await loader(1, size);
  if (first.items.length >= first.total) return first.items;
  const pages = Math.ceil(first.total / size);
  const remaining = await Promise.all(Array.from({ length: pages - 1 }, (_, index) => loader(index + 2, size)));
  return [first, ...remaining].flatMap((page) => page.items);
}
