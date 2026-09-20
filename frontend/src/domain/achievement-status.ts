export type AchievementPhase =
  | 'DRAFT_OR_RETURNED'
  | 'PRE_REVIEW'
  | 'FORMAL_REVIEW'
  | 'SUPPLEMENT_REVIEW'
  | 'COMPLETED';

export const achievementPhaseOptions: Array<{ value: AchievementPhase; label: string }> = [
  { value: 'DRAFT_OR_RETURNED', label: '草稿/退回' },
  { value: 'PRE_REVIEW', label: '预审中' },
  { value: 'FORMAL_REVIEW', label: '正式成果审批中' },
  { value: 'SUPPLEMENT_REVIEW', label: '后续材料审批中' },
  { value: 'COMPLETED', label: '已完成' },
];

const statusPhase: Record<string, AchievementPhase> = {
  DRAFT: 'DRAFT_OR_RETURNED',
  PRE_RETURNED: 'DRAFT_OR_RETURNED',
  FORMAL_DRAFT: 'DRAFT_OR_RETURNED',
  FORMAL_RETURNED: 'DRAFT_OR_RETURNED',
  SUPPLEMENT_RETURNED: 'DRAFT_OR_RETURNED',
  PRE_INITIAL: 'PRE_REVIEW',
  PRE_FINAL: 'PRE_REVIEW',
  FORMAL_INITIAL: 'FORMAL_REVIEW',
  FORMAL_FINAL: 'FORMAL_REVIEW',
  WAIT_PUBLICATION: 'SUPPLEMENT_REVIEW',
  WAIT_GRANT: 'SUPPLEMENT_REVIEW',
  WAIT_CERTIFICATE: 'SUPPLEMENT_REVIEW',
  SUPPLEMENT_INITIAL: 'SUPPLEMENT_REVIEW',
  SUPPLEMENT_FINAL: 'SUPPLEMENT_REVIEW',
  EFFECTIVE: 'COMPLETED',
};

export function achievementPhase(status: string): AchievementPhase | undefined {
  return statusPhase[status];
}

export function achievementPhaseLabel(status: string): string {
  const preciseLabels: Record<string, string> = {
    DRAFT: '草稿',
    PRE_RETURNED: '第一轮预审退回',
    FORMAL_DRAFT: '待补充第二轮材料',
    FORMAL_RETURNED: '第二轮审批退回',
    WAIT_PUBLICATION: '待补充正式刊出材料',
    WAIT_GRANT: '待补充授权材料',
    WAIT_CERTIFICATE: '待补充登记证书',
    SUPPLEMENT_RETURNED: '第三轮审批退回',
    EFFECTIVE: '已完成',
  };
  if (preciseLabels[status]) return preciseLabels[status];
  const phase = achievementPhase(status);
  return achievementPhaseOptions.find((item) => item.value === phase)?.label ?? status;
}

export function matchesAchievementPhase(status: string, phase?: string): boolean {
  return !phase || achievementPhase(status) === phase;
}
