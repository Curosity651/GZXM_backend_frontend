import type { ApiCurrentUser } from '../../api/auth-api';
import type { Achievement, AchievementActionRequest, Topic } from '../../api/research/contracts';

export const operational = (topic?: Topic) => Boolean(topic?.enabled && topic.status === 'ACTIVE');
export const permitted = (user: ApiCurrentUser, permission: string) => user.actionPermissions.includes(permission);
export const unitAccount = (user: ApiCurrentUser) => ['INTERNAL_TOPIC_UNIT', 'EXTERNAL_TOPIC_UNIT'].includes(user.roleCode) && Boolean(user.unitId);
export const managesTopics = (user: ApiCurrentUser) => user.roleCode === 'RESEARCH_ASSISTANT' && permitted(user, 'topic.manage');
export const leadOf = (user: ApiCurrentUser, topic: Topic) => unitAccount(user) && topic.members?.some(member => member.enabled && member.unitId === user.unitId && member.membershipType === 'LEAD');
export function ownsAchievement(user: ApiCurrentUser, row: Achievement, topic?: Topic): boolean {
  return unitAccount(user) && permitted(user, 'achievement.submit') && user.unitId === row.unitId && operational(topic)
    && Boolean(topic?.members?.some(member => member.enabled && member.unitId === user.unitId));
}
export function editableAchievement(user: ApiCurrentUser, row: Achievement, topic?: Topic): boolean {
  return ownsAchievement(user, row, topic) && ['DRAFT', 'PRE_RETURNED', 'FORMAL_DRAFT', 'FORMAL_RETURNED', 'WAIT_PUBLICATION', 'WAIT_GRANT', 'SUPPLEMENT_RETURNED'].includes(row.status);
}
export function nextAction(row: Achievement): AchievementActionRequest['action'] | undefined {
  if (['DRAFT', 'PRE_RETURNED'].includes(row.status)) return 'SUBMIT_PRE_REVIEW';
  if (row.status === 'PRE_APPROVED' && ['PAPER', 'PATENT'].includes(row.achievementType)) return 'REGISTER_EXTERNAL_SUBMISSION';
  if (row.status === 'EXTERNAL_SUBMITTED') return 'START_FORMAL';
  if (['FORMAL_DRAFT', 'FORMAL_RETURNED'].includes(row.status)) return 'SUBMIT_FORMAL';
  if (['WAIT_PUBLICATION', 'WAIT_GRANT', 'SUPPLEMENT_RETURNED'].includes(row.status)) return 'SUBMIT_SUPPLEMENT';
}
export function canReview(user: ApiCurrentUser, row: Achievement, topic?: Topic): boolean {
  return operational(topic) && ((user.roleCode === 'RESEARCH_ASSISTANT' && permitted(user, 'achievement.initial.approve') && ['PRE_INITIAL', 'FORMAL_INITIAL', 'SUPPLEMENT_INITIAL'].includes(row.status))
    || (user.roleCode === 'PROJECT_TECH_LEADER' && permitted(user, 'achievement.final.approve') && ['PRE_FINAL', 'FORMAL_FINAL', 'SUPPLEMENT_FINAL'].includes(row.status)));
}
export const actionNames: Record<AchievementActionRequest['action'], string> = {
  SUBMIT_PRE_REVIEW: '提交预审', REGISTER_EXTERNAL_SUBMISSION: '登记投稿/申请', START_FORMAL: '转入正式草稿', SUBMIT_FORMAL: '提交正式审核', SUBMIT_SUPPLEMENT: '提交补充审核',
};
export const statusNames: Record<string, string> = {
  DRAFT: '预审草稿', PRE_INITIAL: '预审待初审', PRE_FINAL: '预审待终审', PRE_RETURNED: '预审退回', PRE_APPROVED: '预审通过',
  EXTERNAL_SUBMITTED: '已登记投稿/申请', FORMAL_DRAFT: '正式草稿', FORMAL_INITIAL: '正式待初审', FORMAL_FINAL: '正式待终审',
  FORMAL_RETURNED: '正式退回', WAIT_PUBLICATION: '待见刊补充', WAIT_GRANT: '待授权补充', SUPPLEMENT_INITIAL: '补充待初审',
  SUPPLEMENT_FINAL: '补充待终审', SUPPLEMENT_RETURNED: '补充退回', EFFECTIVE: '已生效',
};
