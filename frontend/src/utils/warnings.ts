import dayjs from 'dayjs';
import type {
  Achievement,
  AchievementType,
  IndicatorConfig,
  TimeNode,
  Topic,
  WarningLevel,
  WarningResult,
  WarningRule,
} from '../types';
import { ACHIEVEMENT_EVIDENCE_RULES } from '../types';
import { daysUntil } from './helpers';
import { calculateCompletionStats, calculateDomesticJournalRatio, calculateTopicChineseJournalCount } from './stats';

const findRule = (rules: WarningRule[], type: string) => rules.find((r) => r.type === type && r.enabled);

const getLevelByCompletionRate = (rate: number, levels: WarningRule['levels']): WarningLevel | null => {
  // Check from highest threshold to lowest (worst to best)
  const sorted = [...levels].sort((a, b) => b.completionRateThreshold - a.completionRateThreshold);
  for (const lvl of sorted) {
    if (rate <= lvl.completionRateThreshold) return lvl.level;
  }
  return null;
};

const getLevelByAdvanceDays = (days: number, levels: WarningRule['levels']): WarningLevel | null => {
  // Strict red->orange->yellow check: check smallest advanceDays (red) first
  const sorted = [...levels].sort((a, b) => a.advanceDays - b.advanceDays);
  for (const lvl of sorted) {
    if (days <= lvl.advanceDays) return lvl.level;
  }
  return null;
};

const getLevelByPendingDays = (pendingDays: number, levels: WarningRule['levels']): WarningLevel | null => {
  // For material pending: cumulative pending days — higher = worse
  // Sort levels by advanceDays descending (most strict = largest threshold for pending days)
  const sorted = [...levels].sort((a, b) => b.advanceDays - a.advanceDays);
  for (const lvl of sorted) {
    if (pendingDays >= lvl.advanceDays) return lvl.level;
  }
  return null;
};

const getUnitName = (unitId: string, _topics: Topic[], unitMap: Record<string, string>): string => {
  return unitMap[unitId] || unitId;
};

export const generateWarnings = (
  indicators: IndicatorConfig[],
  achievements: Achievement[],
  nodes: TimeNode[],
  topics: Topic[],
  warningRules: WarningRule[],
  unitMap: Record<string, string>
): WarningResult[] => {
  const results: WarningResult[] = [];
  const nodeMap = new Map(nodes.map((n) => [n.id, n]));

  const timeRule = findRule(warningRules, 'time');
  const qtyRule = findRule(warningRules, 'quantity_gap');
  const progressRule = findRule(warningRules, 'progress');
  const materialRule = findRule(warningRules, 'material');
  const cjRule = findRule(warningRules, 'chinese_journal_ratio');

  indicators
    .forEach((indicator) => {
      const node = nodeMap.get(indicator.nodeId);
      if (!node) return;

      const stats = calculateCompletionStats(indicator, achievements, nodes);
      const days = daysUntil(node.deadline);
      const topic = topics.find((t) => t.id === indicator.topicId);
      const unitName = getUnitName(indicator.unitId, topics, unitMap);
      const hasGap = stats.recognizedCount < indicator.plannedQuantity;
      const rate = stats.completionRate * 100;

      // 时间预警 — strict red->orange->yellow (check red first)
      if (timeRule && hasGap && node.participatesInWarning) {
        const level = getLevelByAdvanceDays(days, timeRule.levels);
        if (level) {
          results.push({
            id: `w-time-${indicator.id}`, type: 'time', level, title: '时间预警',
            message: `${topic?.name} ${unitName} ${indicator.achievementType} ${node.name} 剩余 ${days} 天，已认定 ${stats.recognizedCount}/${indicator.plannedQuantity}`,
            topicId: indicator.topicId, unitId: indicator.unitId, achievementType: indicator.achievementType,
            nodeId: indicator.nodeId, deadline: node.deadline, daysRemaining: days, gap: stats.missingCount,
          });
        }
      }

      // 数量缺口预警 — check rate against thresholds from high to low
      if (qtyRule && hasGap) {
        const level = getLevelByCompletionRate(rate, qtyRule.levels);
        if (level) {
          results.push({
            id: `w-qty-${indicator.id}`, type: 'quantity_gap', level, title: '数量缺口预警',
            message: `${topic?.name} ${unitName} ${indicator.achievementType} ${node.name} 计划 ${indicator.plannedQuantity}，已认定 ${stats.recognizedCount}，缺口 ${stats.missingCount}`,
            topicId: indicator.topicId, unitId: indicator.unitId, achievementType: indicator.achievementType,
            nodeId: indicator.nodeId, deadline: node.deadline, daysRemaining: days, gap: stats.missingCount,
          });
        }
      }

      // 成果进度预警
      if (progressRule && node.participatesInWarning) {
        const relevant = achievements.filter(
          (a) => a.topicId === indicator.topicId && a.unitId === indicator.unitId && a.achievementType === indicator.achievementType
        );
        const registered = relevant.filter((a) => ['已提交', '审批中', '审批通过', '审批不通过', '退回修改'].includes(a.status)).length;
        const recognized = relevant.filter((a) => a.status === '审批通过' && a.countsToIndicator).length;
        if (registered >= indicator.plannedQuantity && recognized < indicator.plannedQuantity) {
          const level = getLevelByAdvanceDays(days, progressRule.levels);
          if (level) {
            results.push({
              id: `w-progress-${indicator.id}`, type: 'progress', level, title: '成果进度预警',
              message: `${topic?.name} ${unitName} ${indicator.achievementType} ${node.name} 已登记 ${registered}、已认定 ${recognized}，存在交付风险`,
              topicId: indicator.topicId, unitId: indicator.unitId, achievementType: indicator.achievementType,
              nodeId: indicator.nodeId, deadline: node.deadline, daysRemaining: days, gap: indicator.plannedQuantity - recognized,
            });
          }
        }
      }

      // 佐证材料预警
      if (materialRule) {
        const relevant = achievements.filter(
          (a) => a.topicId === indicator.topicId && a.unitId === indicator.unitId && a.achievementType === indicator.achievementType
        );
        relevant.forEach((a) => {
          // Check for completely missing materials by comparing
          // ACHIEVEMENT_EVIDENCE_RULES[type] required options vs actually uploaded materials
          const evidenceDef = ACHIEVEMENT_EVIDENCE_RULES[a.achievementType];
          if (evidenceDef) {
            const rule = evidenceDef.rule;
            const uploadedMaterialTypes = a.materials.map((m) => m.materialType);

            if (rule.type === 'SINGLE') {
              // All options must be uploaded and approved
              const missingOptions = rule.options.filter(
                (opt) => !uploadedMaterialTypes.includes(opt)
              );
              missingOptions.forEach((opt) => {
                results.push({
                  id: `w-material-missing-${a.id}-${opt}`, type: 'material', level: 'orange', title: '佐证材料预警',
                  message: `${topic?.name} ${a.title} 缺少材料：${opt}`,
                  topicId: indicator.topicId, unitId: indicator.unitId, achievementType: indicator.achievementType,
                });
              });
            } else if (rule.type === 'OR') {
              // At least one option must be uploaded
              const hasAny = rule.options.some((opt) => uploadedMaterialTypes.includes(opt));
              if (!hasAny && rule.options.length > 0) {
                results.push({
                  id: `w-material-missing-${a.id}-or`, type: 'material', level: 'orange', title: '佐证材料预警',
                  message: `${topic?.name} ${a.title} 缺少佐证材料（需上传：${rule.options.join(' 或 ')}）`,
                  topicId: indicator.topicId, unitId: indicator.unitId, achievementType: indicator.achievementType,
                });
              }
            }
          }

          // Check existing materials for pending/returned status
          a.materials.forEach((m) => {
            if (m.status === '未提交') {
              results.push({
                id: `w-material-unsubmitted-${a.id}-${m.id}`, type: 'material', level: 'orange', title: '佐证材料预警',
                message: `${topic?.name} ${a.title} 材料 ${m.name} 未提交`,
                topicId: indicator.topicId, unitId: indicator.unitId, achievementType: indicator.achievementType,
              });
            } else if (m.status === '退回修改') {
              results.push({
                id: `w-material-ret-${a.id}-${m.id}`, type: 'material', level: 'red', title: '佐证材料预警',
                message: `${topic?.name} ${a.title} 材料 ${m.name} 被退回`,
                topicId: indicator.topicId, unitId: indicator.unitId, achievementType: indicator.achievementType,
              });
            } else if (m.status === '待审核' && m.uploadedAt) {
              const pd = dayjs().diff(dayjs(m.uploadedAt), 'day');
              const level = getLevelByPendingDays(pd, materialRule.levels);
              if (level) {
                results.push({
                  id: `w-material-pending-${a.id}-${m.id}`, type: 'material', level, title: '佐证材料预警',
                  message: `${topic?.name} ${a.title} 材料 ${m.name} 待审核 ${pd} 天`,
                  topicId: indicator.topicId, unitId: indicator.unitId, achievementType: indicator.achievementType,
                });
              }
            }
          });
        });
      }
    });

  // 国内期刊比例预警 — using topic.domesticJournalRequiredCount
  if (cjRule) {
    const ratioData = calculateDomesticJournalRatio(achievements, topics);
    const ratioText = ratioData.ratio !== null ? `${ratioData.ratio}%` : '暂无数据';
    const projectedText = ratioData.projectedRatio !== null ? `${ratioData.projectedRatio}%` : '暂无数据';

    if (ratioData.gapCount > 0) {
      results.push({
        id: 'w-cj-ratio', type: 'chinese_journal_ratio', level: 'yellow', title: '国内期刊比例预警',
        message: `当前比例 ${ratioText}，预计比例 ${projectedText}，最低要求 ${ratioData.minRequiredCount} 篇，缺口约 ${ratioData.gapCount} 篇`,
      });
    }

    // Per-topic domestic journal warnings
    topics.forEach((topic) => {
      const required = topic.domesticJournalRequiredCount || 0;
      if (required > 0) {
        const actual = calculateTopicChineseJournalCount(topic.id, achievements);
        if (actual < required) {
          results.push({
            id: `w-cj-topic-${topic.id}`, type: 'chinese_journal_ratio', level: 'yellow', title: '国内期刊比例预警',
            message: `${topic.name} 国内期刊论文最低要求 ${required} 篇，当前 ${actual} 篇，缺口 ${required - actual} 篇`,
            topicId: topic.id, gap: required - actual,
          });
        }
      }
    });
  }

  return results;
};

export const filterWarnings = (
  warnings: WarningResult[],
  filters: { level?: string; topicId?: string; unitId?: string; achievementType?: AchievementType; nodeId?: string; type?: string }
) => {
  return warnings.filter((w) => {
    return (
      (!filters.level || w.level === filters.level) &&
      (!filters.topicId || w.topicId === filters.topicId) &&
      (!filters.unitId || w.unitId === filters.unitId) &&
      (!filters.achievementType || w.achievementType === filters.achievementType) &&
      (!filters.nodeId || w.nodeId === filters.nodeId) &&
      (!filters.type || w.type === filters.type)
    );
  });
};
