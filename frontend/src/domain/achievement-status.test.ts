import { describe, expect, it } from 'vitest';
import { achievementPhase, achievementPhaseLabel, matchesAchievementPhase } from './achievement-status';

describe('成果状态业务阶段', () => {
  it('将三轮审批状态完整归入5个前台阶段', () => {
    const statuses = [
      'DRAFT', 'PRE_INITIAL', 'PRE_FINAL', 'PRE_RETURNED',
      'FORMAL_DRAFT', 'FORMAL_INITIAL', 'FORMAL_FINAL', 'FORMAL_RETURNED', 'WAIT_PUBLICATION',
      'WAIT_GRANT', 'WAIT_CERTIFICATE', 'SUPPLEMENT_INITIAL', 'SUPPLEMENT_FINAL', 'SUPPLEMENT_RETURNED', 'EFFECTIVE',
    ];
    expect(statuses.every((status) => achievementPhase(status))).toBe(true);
    expect(new Set(statuses.map(achievementPhase))).toHaveLength(5);
  });

  it('区分正式审批、后续材料审批和最终完成', () => {
    expect(achievementPhaseLabel('FORMAL_DRAFT')).toBe('待补充第二轮材料');
    expect(achievementPhaseLabel('FORMAL_FINAL')).toBe('正式成果审批中');
    expect(achievementPhaseLabel('WAIT_PUBLICATION')).toBe('待补充正式刊出材料');
    expect(achievementPhaseLabel('EFFECTIVE')).toBe('已完成');
    expect(matchesAchievementPhase('SUPPLEMENT_RETURNED', 'DRAFT_OR_RETURNED')).toBe(true);
  });
});
