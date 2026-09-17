import { useCallback, useState } from 'react';
import { Alert, Button, Card, Form, Input, Select, Space, Switch, Table, message } from 'antd';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import type { ApiCurrentUser } from '../../api/auth-api';
import { researchApi } from '../../api/research/client';
import type { Topic, TopicMembership, TopicWriteRequest, Unit } from '../../api/research/contracts';
import { leadOf, managesTopics, operational, permitted } from '../achievement/real-permissions';
import { ResearchSession } from './ResearchSession';
import { topicStatuses, useResearchLoad, useResearchMutation } from './research-hooks';
import { RealIndicatorEditor } from './RealIndicatorEditor';

export function RealTopicIndicatorConfigPage() {
  const { topicId = '' } = useParams();
  const [search] = useSearchParams();
  return <ResearchSession page="topic-indicator">{user => <TopicEditor key={`${topicId}:${user.id}`} id={topicId} user={user} readOnly={search.get('mode') === 'view'} />}</ResearchSession>;
}
function TopicEditor({ id, user, readOnly }: { id: string; user: ApiCurrentUser; readOnly: boolean }) {
  const navigate = useNavigate();
  const load = useResearchLoad(useCallback(async () => {
    const [topic, units] = await Promise.all([id === 'new' ? Promise.resolve(undefined) : researchApi.topic(id), researchApi.units()]);
    return { topic, units };
  }, [id]));
  return <Space orientation="vertical" style={{ width: '100%' }} size="large">
    <Button onClick={() => navigate('/indicator')}>返回课题列表</Button>
    {load.error && <Alert type="error" title={load.error} action={<Button onClick={load.refresh}>重试</Button>} />}
    <Card title={id === 'new' ? '新建课题' : '课题与成员'} loading={load.loading}>
      {load.data && <TopicFields key={`${id}:${load.data.topic?.recordVersion}`} user={user} topic={load.data.topic} units={load.data.units} readOnly={readOnly} refresh={load.refresh} />}
    </Card>
    {load.data?.topic && <RealIndicatorEditor key={id} topic={load.data.topic} user={user} readOnly={readOnly} />}
  </Space>;
}
function TopicFields({ user, topic, units, readOnly, refresh }: { user: ApiCurrentUser; topic?: Topic; units: Unit[]; readOnly: boolean; refresh: () => void }) {
  const navigate = useNavigate();
  const [form] = Form.useForm<TopicWriteRequest>();
  const [newMember, setNewMember] = useState<string>();
  const mutation = useResearchMutation();
  const canManage = managesTopics(user) && !readOnly;
  const canEdit = canManage && (!topic || operational(topic));
  const canMembers = Boolean(topic && operational(topic) && leadOf(user, topic) && permitted(user, 'topic-unit.manage') && !readOnly);
  const initial: Partial<TopicWriteRequest> = topic ? { code: topic.code, name: topic.name, summary: topic.summary, leadUnitId: topic.leadUnitId, startDate: topic.startDate, endDate: topic.endDate } : {};
  const enabledUnits = units.filter(unit => unit.enabled);
  return <>
    {mutation.error && <Alert type="error" showIcon title={mutation.error} description="若提示版本冲突，请先刷新课题后重新核对修改。" action={<Button onClick={refresh}>重新读取</Button>} />}
    <Form form={form} layout="vertical" initialValues={initial} disabled={!canEdit || mutation.busy} onFinish={values => void mutation.run(async () => {
      const body = { ...values, startDate: values.startDate || undefined, endDate: values.endDate || undefined, ...(topic ? { recordVersion: topic.recordVersion } : {}) };
      const saved = topic ? await researchApi.updateTopic(topic.id, body) : await researchApi.createTopic(body);
      message.success('课题已保存');
      if (!topic) navigate(`/indicator/topic/${encodeURIComponent(saved.id)}`, { replace: true }); else refresh();
    })}>
      <Form.Item name="code" label="课题编码" rules={[{ required: true }, { max: 64 }]}><Input /></Form.Item>
      <Form.Item name="name" label="课题名称" rules={[{ required: true }, { max: 300 }]}><Input /></Form.Item>
      <Form.Item name="summary" label="研究内容摘要"><Input.TextArea rows={3} /></Form.Item>
      <Form.Item name="leadUnitId" label="牵头单位" rules={[{ required: true }]}><Select options={enabledUnits.map(unit => ({ value: unit.id, label: unit.name }))} /></Form.Item>
      {!topic && <Form.Item name="participantUnitIds" label="承担单位"><Select mode="multiple" options={enabledUnits.map(unit => ({ value: unit.id, label: unit.name }))} /></Form.Item>}
      <Form.Item name="startDate" label="开始日期"><Input type="date" /></Form.Item>
      <Form.Item name="endDate" label="结束日期"><Input type="date" /></Form.Item>
      {canEdit && <Button type="primary" htmlType="submit" loading={mutation.busy}>保存课题</Button>}
    </Form>
    {topic && <>
      <Space style={{ margin: '16px 0' }}>
        <span>课题状态</span><Select aria-label="课题状态" value={topic.status} disabled={!canManage || mutation.busy} style={{ width: 130 }} options={Object.entries(topicStatuses).map(([value, label]) => ({ value, label }))} onChange={status => void mutation.run(async () => { await researchApi.topicStatus(topic.id, { enabled: topic.enabled, status }); refresh(); })} />
        <Switch checked={topic.enabled} checkedChildren="启用" unCheckedChildren="停用" disabled={!canManage || mutation.busy} onChange={enabled => void mutation.run(async () => { await researchApi.topicStatus(topic.id, { enabled, status: topic.status }); refresh(); })} />
      </Space>
      <Table<TopicMembership> rowKey="id" pagination={false} dataSource={topic.members ?? []} columns={[
        { title: '单位', dataIndex: 'unitName' }, { title: '课题身份', render: (_, member) => member.membershipType === 'LEAD' ? '牵头单位' : '承担单位' },
        { title: '状态', render: (_, member) => member.enabled ? '启用' : '停用' },
        { title: '操作', render: (_, member) => canMembers && member.membershipType !== 'LEAD' && <Button disabled={mutation.busy} onClick={() => void mutation.run(async () => { await researchApi.memberStatus(topic.id, member.id, !member.enabled); refresh(); })}>{member.enabled ? '停用' : '恢复'}</Button> },
      ]} />
      {canMembers && <Space style={{ marginTop: 16 }}><Select aria-label="新增承担单位" placeholder="请选择承担单位" style={{ width: 260 }} value={newMember} onChange={setNewMember} options={enabledUnits.filter(unit => !topic.members?.some(member => member.unitId === unit.id)).map(unit => ({ value: unit.id, label: unit.name }))} /><Button disabled={!newMember || mutation.busy} onClick={() => void mutation.run(async () => { await researchApi.addMember(topic.id, newMember!); setNewMember(undefined); refresh(); })}>添加成员</Button></Space>}
    </>}
  </>;
}
