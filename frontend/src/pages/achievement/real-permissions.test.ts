import { describe, expect, it } from 'vitest';
import type { ApiCurrentUser } from '../../api/auth-api';
import type { Achievement, Topic } from '../../api/research/contracts';
import { canReview, editableAchievement, leadOf, managesTopics, nextAction, ownsAchievement } from './real-permissions';

const topic: Topic = { id: '1', code: 'T', name: '测试', leadUnitId: '2', status: 'ACTIVE', enabled: true, recordVersion: 1,
  members: [{ id: '1', topicId: '1', unitId: '2', membershipType: 'LEAD', enabled: true }, { id: '2', topicId: '1', unitId: '3', membershipType: 'PARTICIPANT', enabled: true }] };
const item: Achievement = { id: '1', topicId: '1', unitId: '2', nodeId: '1', indicatorDefinitionId: '1', achievementType: 'PAPER', title: '测试成果', status: 'DRAFT', recordVersion: 1, submittedVersion: 0, materials: [] };
function user(roleCode: string, unitId?: string): ApiCurrentUser { return { id: roleCode, username: '测试', roleCode, unitId, pagePermissions: [], actionPermissions: ['topic.manage', 'achievement.submit', 'achievement.initial.approve', 'achievement.final.approve'], memberships: [] }; }
describe('real five-role business actions', () => {
  it.each(['SYSTEM_ADMIN', 'PROJECT_TECH_LEADER', 'RESEARCH_ASSISTANT', 'INTERNAL_TOPIC_UNIT', 'EXTERNAL_TOPIC_UNIT'])('%s uses backend role semantics even with extra permission codes', role => {
    const actor = user(role, '2');
    expect(managesTopics(actor)).toBe(role === 'RESEARCH_ASSISTANT');
    expect(editableAchievement(actor, item, topic)).toBe(role.endsWith('_TOPIC_UNIT'));
    expect(canReview(actor, { ...item, status: 'PRE_INITIAL' }, topic)).toBe(role === 'RESEARCH_ASSISTANT');
    expect(canReview(actor, { ...item, status: 'PRE_FINAL' }, topic)).toBe(role === 'PROJECT_TECH_LEADER');
  });
  it('lead cannot edit other units, participants cannot act as lead, disabled membership loses ownership', () => {
    expect(ownsAchievement(user('INTERNAL_TOPIC_UNIT', '2'), { ...item, unitId: '3' }, topic)).toBe(false);
    expect(leadOf(user('EXTERNAL_TOPIC_UNIT', '3'), topic)).toBe(false);
    expect(ownsAchievement(user('INTERNAL_TOPIC_UNIT', '2'), item, { ...topic, members: [] })).toBe(false);
    expect(editableAchievement(user('INTERNAL_TOPIC_UNIT', '2'), { ...item, status: 'PRE_INITIAL' }, topic)).toBe(false);
  });
  it('paused/disabled topics and removed permissions disable edits and approvals', () => {
    expect(editableAchievement(user('INTERNAL_TOPIC_UNIT', '2'), item, { ...topic, status: 'PAUSED' })).toBe(false);
    expect(canReview(user('RESEARCH_ASSISTANT'), { ...item, status: 'PRE_INITIAL' }, { ...topic, enabled: false })).toBe(false);
    expect(editableAchievement({ ...user('INTERNAL_TOPIC_UNIT', '2'), actionPermissions: [] }, item, topic)).toBe(false);
  });
  it('maps actions without inventing a direct effective transition', () => {
    expect(nextAction(item)).toBe('SUBMIT_PRE_REVIEW');
    expect(nextAction({ ...item, status: 'PRE_APPROVED' })).toBe('REGISTER_EXTERNAL_SUBMISSION');
    expect(nextAction({ ...item, status: 'FORMAL_RETURNED' })).toBe('SUBMIT_FORMAL');
    expect(nextAction({ ...item, status: 'WAIT_GRANT', achievementType: 'PATENT' })).toBe('SUBMIT_SUPPLEMENT');
    expect(nextAction({ ...item, status: 'EFFECTIVE' })).toBeUndefined();
  });
});
