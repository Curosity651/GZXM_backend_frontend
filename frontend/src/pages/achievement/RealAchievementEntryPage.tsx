import { useCallback, useState } from 'react';
import { Alert, Button, Card, Descriptions, Drawer, Form, Input, Modal, Select, Space, Switch, Table, Tag, Typography, message } from 'antd';
import type { ApiCurrentUser } from '../../api/auth-api';
import { allTopics, ResearchOperationKeys, researchApi } from '../../api/research/client';
import type { Achievement, AchievementActionRequest, SubmissionSnapshot } from '../../api/research/contracts';
import { ResearchSession } from '../indicator/ResearchSession';
import { useResearchLoad, useResearchMutation } from '../indicator/research-hooks';
import { actionNames, canReview, editableAchievement, nextAction, ownsAchievement, permitted, statusNames, unitAccount } from './real-permissions';
import { RealAchievementForm } from './RealAchievementForm';
import { RealAchievementProgress } from './RealAchievementProgress';
import { detailFields } from './detail-fields';

export function RealAchievementEntryPage() {
  return <ResearchSession page="achievement-entry">{user => <AchievementWorkspace key={user.id} user={user} />}</ResearchSession>;
}
type ActiveOperation = { item: Achievement; action?: AchievementActionRequest['action']; review?: boolean };
const stages: Record<string, string> = { PRE_REVIEW: '预审', FORMAL: '正式审核', SUPPLEMENT: '补充审核' };
const levels: Record<string, string> = { INITIAL: '初审', FINAL: '终审' };
const decisions: Record<string, string> = { APPROVED: '通过', RETURNED: '退回' };
function AchievementWorkspace({ user }: { user: ApiCurrentUser }) {
  const catalog = useResearchLoad(useCallback(async () => {
    const [topics, nodes, definitions, units] = await Promise.all([allTopics(), researchApi.nodes(), researchApi.definitions(), researchApi.units()]);
    return { topics, nodes, definitions, units };
  }, []));
  const [topicId, setTopicId] = useState<string>();
  const [unitId, setUnitId] = useState<string>();
  const [nodeId, setNodeId] = useState<string>();
  const [status, setStatus] = useState<string>();
  const [page, setPage] = useState(1);
  const [pending, setPending] = useState(false);
  const [revision, setRevision] = useState(0);
  const [editing, setEditing] = useState<Achievement | 'new'>();
  const [detail, setDetail] = useState<{ item: Achievement; snapshots: SubmissionSnapshot[] }>();
  const [operation, setOperation] = useState<ActiveOperation>();
  const mutation = useResearchMutation();
  const [keys] = useState(() => new ResearchOperationKeys());
  const rows = useResearchLoad(useCallback(() => researchApi.achievements({ topicId, unitId, status, page, size: 20, pendingForMe: pending }), [topicId, unitId, status, page, pending]), revision);
  const selectedNode = nodeId ?? catalog.data?.nodes.at(-1)?.id;
  const refresh = () => { rows.refresh(); catalog.refresh(); setRevision(value => value + 1); };
  const topicFor = (item: Achievement) => catalog.data?.topics.find(topic => topic.id === item.topicId);
  const open = (item: Achievement, mode: 'edit' | 'detail' | 'action' | 'review') => void mutation.run(async () => {
    const latest = await researchApi.achievement(item.id);
    if (mode === 'edit') setEditing(latest);
    else if (mode === 'detail') setDetail({ item: latest, snapshots: await researchApi.snapshots(item.id) });
    else setOperation({ item: latest, action: mode === 'action' ? nextAction(latest) : undefined, review: mode === 'review' });
  });
  return <Space orientation="vertical" style={{ width: '100%' }} size="large">
    <Card title="成果填报与审批" loading={catalog.loading} extra={<Space><Button onClick={refresh}>刷新</Button>{unitAccount(user) && permitted(user, 'achievement.submit') && <Button type="primary" disabled={!catalog.data} onClick={() => setEditing('new')}>新建成果</Button>}</Space>}>
      {(catalog.error || rows.error || mutation.error) && <Alert type="error" showIcon title={catalog.error || rows.error || mutation.error} />}
      <Space wrap style={{ marginBottom: 16 }}>
        <Select allowClear placeholder="全部可见课题" aria-label="筛选课题" style={{ width: 260 }} value={topicId} onChange={value => { setTopicId(value); setUnitId(undefined); setPage(1); }} options={catalog.data?.topics.map(topic => ({ value: topic.id, label: topic.name }))} />
        <Select allowClear placeholder="全部可见单位" aria-label="筛选单位" style={{ width: 220 }} value={unitId} onChange={value => { setUnitId(value); setPage(1); }} options={catalog.data?.units.filter(unit => {
          if (!unitAccount(user)) return true;
          if (unit.id === user.unitId) return true;
          return catalog.data?.topics.some(topic => (!topicId || topic.id === topicId) && topic.members?.some(member => member.enabled && member.unitId === user.unitId && member.membershipType === 'LEAD') && topic.members?.some(member => member.enabled && member.unitId === unit.id));
        }).map(unit => ({ value: unit.id, label: unit.name }))} />
        <Select allowClear placeholder="全部状态" aria-label="筛选成果状态" style={{ width: 190 }} value={status} onChange={value => { setStatus(value); setPage(1); }} options={Object.entries(statusNames).map(([value, label]) => ({ value, label }))} />
        {['RESEARCH_ASSISTANT', 'PROJECT_TECH_LEADER'].includes(user.roleCode) && <Space><Switch checked={pending} onChange={value => { setPending(value); setPage(1); }} />只看待我审批</Space>}
      </Space>
      <Table<Achievement> rowKey="id" loading={rows.loading} dataSource={rows.data?.items ?? []} pagination={{ current: page, pageSize: 20, total: rows.data?.total ?? 0, showSizeChanger: false, onChange: setPage }} columns={[
        { title: '标题', dataIndex: 'title' }, { title: '所属课题', render: (_, item) => topicFor(item)?.name ?? item.topicId },
        { title: '所属单位', render: (_, item) => catalog.data?.units.find(unit => unit.id === item.unitId)?.name ?? item.unitId },
        { title: '状态', render: (_, item) => <Tag>{statusNames[item.status] ?? item.status}</Tag> },
        { title: '操作', render: (_, item) => <Space wrap>
          <Button disabled={mutation.busy} onClick={() => open(item, 'detail')}>详情与历史</Button>
          {editableAchievement(user, item, topicFor(item)) && <Button disabled={mutation.busy} onClick={() => open(item, 'edit')}>编辑</Button>}
          {ownsAchievement(user, item, topicFor(item)) && nextAction(item) && <Button disabled={mutation.busy} onClick={() => open(item, 'action')}>{actionNames[nextAction(item)!]}</Button>}
          {canReview(user, item, topicFor(item)) && <Button disabled={mutation.busy} onClick={() => open(item, 'review')}>审批</Button>}
        </Space> },
      ]} />
    </Card>
    {catalog.data && <>
      <Space><span>累计统计节点</span><Select aria-label="累计统计节点" value={selectedNode} onChange={setNodeId} style={{ width: 250 }} options={catalog.data.nodes.map(node => ({ value: node.id, label: node.name }))} /></Space>
      {selectedNode && <RealAchievementProgress nodeId={selectedNode} topicId={topicId} unitId={unitId} topics={catalog.data.topics} units={catalog.data.units} definitions={catalog.data.definitions} revision={revision} />}
      <Drawer open={Boolean(editing)} title={editing === 'new' ? '新建成果' : '编辑成果'} onClose={() => setEditing(undefined)} size={820} destroyOnHidden>
        {editing && <RealAchievementForm key={editing === 'new' ? 'new' : `${editing.id}:${editing.recordVersion}`} user={user} item={editing === 'new' ? undefined : editing} {...catalog.data} saved={() => { setEditing(undefined); message.success('成果已保存'); refresh(); }} />}
      </Drawer>
    </>}
    <Drawer open={Boolean(detail)} title="成果详情与提交历史" onClose={() => setDetail(undefined)} size={900} destroyOnHidden>
      {detail && <>
        <Descriptions bordered column={1} items={[
          { key: 'title', label: '标题', children: detail.item.title }, { key: 'responsible', label: '负责人', children: detail.item.responsiblePerson },
          { key: 'status', label: '状态', children: statusNames[detail.item.status] },
          { key: 'version', label: '记录 / 提交版本', children: `${detail.item.recordVersion} / ${detail.item.submittedVersion}` },
          ...Object.entries(detail.item.detail ?? {}).map(([key, value]) => ({ key, label: detailFields[detail.item.achievementType]?.find(field => field.key === key)?.label ?? key, children: typeof value === 'boolean' ? (value ? '是' : '否') : String(value ?? '') })),
        ]} />
        <Typography.Title level={5}>审批记录</Typography.Title>
        <Table rowKey="id" dataSource={detail.item.approvals ?? []} pagination={false} columns={[{ title: '阶段', render: (_, row) => stages[row.stage] ?? row.stage }, { title: '级别', render: (_, row) => levels[row.level] ?? row.level }, { title: '结论', render: (_, row) => decisions[row.decision] ?? row.decision }, { title: '意见', dataIndex: 'opinion' }, { title: '提交版本', dataIndex: 'submittedVersion' }, { title: '时间', dataIndex: 'operatedAt' }]} />
        <Typography.Title level={5}>提交快照</Typography.Title>
        <Table rowKey="id" dataSource={detail.snapshots} pagination={false} columns={[{ title: '提交版本', dataIndex: 'submittedVersion' }, { title: '阶段', render: (_, row) => stages[row.stage] ?? row.stage }, { title: '提交时间', dataIndex: 'submittedAt' }]} expandable={{ expandedRowRender: snapshot => <SnapshotContent payload={snapshot.payload} /> }} />
        <Typography.Title level={5}>材料关联历史</Typography.Title>
        <Table rowKey="id" dataSource={detail.item.materialLinks ?? []} pagination={false} columns={[{ title: '材料类别', dataIndex: 'materialType' }, { title: '版本', dataIndex: 'version' }, { title: '有效', render: (_, row) => row.active ? '是' : '历史版本' }]} />
      </>}
    </Drawer>
    {operation && <OperationDialog key={`${operation.item.id}:${operation.item.recordVersion}`} operation={operation} keys={keys} close={() => setOperation(undefined)} done={() => { setOperation(undefined); message.success('操作成功'); refresh(); }} />}
  </Space>;
}
function SnapshotContent({ payload }: { payload: unknown }) {
  if (!payload || typeof payload !== 'object') return <Alert type="warning" title="该历史快照暂无可展示内容" />;
  const value = payload as Record<string, unknown>;
  const type = typeof value.achievementType === 'string' ? value.achievementType : '';
  const detail = value.detail && typeof value.detail === 'object' ? value.detail : {};
  return <Descriptions column={1} bordered items={[
    { key: 'title', label: '提交时标题', children: String(value.title ?? '') },
    { key: 'person', label: '提交时负责人', children: String(value.responsiblePerson ?? '') },
    ...Object.entries(detail).map(([key, entry]) => ({ key, label: detailFields[type]?.find(field => field.key === key)?.label ?? key, children: typeof entry === 'boolean' ? (entry ? '是' : '否') : String(entry ?? '') })),
  ]} />;
}
function OperationDialog({ operation, keys, close, done }: { operation: ActiveOperation; keys: ResearchOperationKeys; close: () => void; done: () => void }) {
  const mutation = useResearchMutation();
  const [form] = Form.useForm<{ decision: 'APPROVE' | 'RETURN'; opinion?: string; externalSubmissionDate?: string; externalSubmissionNumber?: string }>();
  const { item, action, review } = operation;
  return <Modal open title={review ? '审批成果' : action ? actionNames[action] : '状态已变化，请重新查询'} onCancel={close} footer={null} closable={!mutation.busy} mask={{ closable: !mutation.busy }}>
    <Typography.Paragraph>{item.title} · {statusNames[item.status]}</Typography.Paragraph>
    {mutation.error && <Alert type="error" showIcon title={mutation.error} />}
    {action && ['SUBMIT_FORMAL', 'SUBMIT_SUPPLEMENT'].includes(action) && <Alert type="warning" title="文件能力尚未接入，当前提交将由后端拒绝，不会跳过材料校验。" />}
    <Form form={form} layout="vertical" disabled={mutation.busy} initialValues={{ decision: 'APPROVE' }} onFinish={values => void mutation.run(async () => {
      if (review) {
        const body = { decision: values.decision, opinion: values.opinion, submittedVersion: item.submittedVersion, recordVersion: item.recordVersion };
        await researchApi.review(item.id, body, keys.forRequest(`review:${item.id}`, body));
      } else if (action) {
        const body = { action, recordVersion: item.recordVersion, ...(action === 'REGISTER_EXTERNAL_SUBMISSION' ? { externalSubmissionDate: values.externalSubmissionDate, externalSubmissionNumber: values.externalSubmissionNumber } : {}) };
        await researchApi.action(item.id, body, keys.forRequest(`action:${item.id}`, body));
      } else return;
      done();
    })}>
      {review && <>
        <Form.Item name="decision" label="审批结论" rules={[{ required: true }]}><Select options={[{ value: 'APPROVE', label: '通过' }, { value: 'RETURN', label: '退回' }]} /></Form.Item>
        <Form.Item name="opinion" label="审批意见" dependencies={['decision']} rules={[({ getFieldValue }) => ({ required: getFieldValue('decision') === 'RETURN', whitespace: true, message: '退回必须填写意见' }), { max: 1000 }]}><Input.TextArea maxLength={1000} /></Form.Item>
      </>}
      {action === 'REGISTER_EXTERNAL_SUBMISSION' && <>
        <Form.Item name="externalSubmissionDate" label="投稿 / 申请日期" rules={[{ required: true }]}><Input type="date" /></Form.Item>
        <Form.Item name="externalSubmissionNumber" label="投稿 / 申请编号"><Input maxLength={500} /></Form.Item>
      </>}
      <Button type="primary" htmlType="submit" loading={mutation.busy} disabled={!review && !action}>确认操作</Button>
    </Form>
  </Modal>;
}
