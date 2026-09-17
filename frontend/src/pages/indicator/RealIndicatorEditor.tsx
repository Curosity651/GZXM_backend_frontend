import { useCallback, useState } from 'react';
import { Alert, Button, Card, Form, InputNumber, Select, Space, Table, Tabs, Typography, message } from 'antd';
import type { ApiCurrentUser } from '../../api/auth-api';
import { hasResearchMetadataRequest, ResearchOperationKeys, researchApi, type Draft } from '../../api/research/client';
import type { IndicatorDefinition, Topic, TopicIndicator, UnitIndicatorAllocation } from '../../api/research/contracts';
import { leadOf, operational, permitted } from '../achievement/real-permissions';
import { useResearchLoad, useResearchMutation } from './research-hooks';

type Item = { unitId?: string; indicatorDefinitionId: string; targetQuantity: number };
type DraftRow = TopicIndicator | UnitIndicatorAllocation;
export function RealIndicatorEditor({ topic, user, readOnly }: { topic: Topic; user: ApiCurrentUser; readOnly: boolean }) {
  const catalog = useResearchLoad(useCallback(async () => {
    const [nodes, definitions] = await Promise.all([researchApi.nodes(), researchApi.definitions()]);
    return { nodes, definitions };
  }, []));
  const [node, setNode] = useState<string>();
  const nodeId = node ?? catalog.data?.nodes.find(item => item.enabled)?.id ?? catalog.data?.nodes[0]?.id;
  return <Card title="累计指标与单位分配" loading={catalog.loading}>
    {catalog.error && <Alert type="error" title={catalog.error} action={<Button onClick={catalog.refresh}>重试</Button>} />}
    {catalog.data && <>
      <Select aria-label="考核节点" placeholder="请选择考核节点" value={nodeId} onChange={setNode} style={{ width: 300, marginBottom: 16 }} options={catalog.data.nodes.map(item => ({ value: item.id, label: `${item.name}${item.enabled ? '' : '（停用）'}` }))} />
      {!nodeId && <Alert type="info" title="尚未配置考核节点，请联系项目管理员" />}
      {nodeId && <IndicatorNode key={nodeId} nodeId={nodeId} topic={topic} user={user} definitions={catalog.data.definitions} readOnly={readOnly || !catalog.data.nodes.find(item => item.id === nodeId)?.enabled} />}
    </>}
  </Card>;
}
function IndicatorNode({ nodeId, topic, user, definitions, readOnly }: { nodeId: string; topic: Topic; user: ApiCurrentUser; definitions: IndicatorDefinition[]; readOnly: boolean }) {
  return <Tabs items={[
    { key: 'targets', label: '课题指标', children: <IndicatorSheet topic={topic} nodeId={nodeId} definitions={definitions} allocation={false}
      canEdit={!readOnly && operational(topic) && user.roleCode === 'RESEARCH_ASSISTANT' && permitted(user, 'indicator.manage')}
      canPublish={permitted(user, 'topic-indicator.publish')} /> },
    { key: 'allocations', label: '单位分配', children: <IndicatorSheet topic={topic} nodeId={nodeId} definitions={definitions} allocation
      canEdit={!readOnly && operational(topic) && Boolean(leadOf(user, topic)) && permitted(user, 'unit-allocation.manage')}
      canPublish={permitted(user, 'unit-allocation.publish')} /> },
  ]} />;
}
function IndicatorSheet({ topic, nodeId, definitions, allocation, canEdit, canPublish }: { topic: Topic; nodeId: string; definitions: IndicatorDefinition[]; allocation: boolean; canEdit: boolean; canPublish: boolean }) {
  const effective = useResearchLoad(useCallback(() => allocation ? researchApi.allocations(topic.id, nodeId) : researchApi.targets(topic.id, nodeId), [allocation, topic.id, nodeId]));
  const [loadedDraft, setLoadedDraft] = useState<Draft<DraftRow>>();
  const [dirty, setDirty] = useState(false);
  const [form] = Form.useForm<{ items: Item[] }>();
  const [keys] = useState(() => new ResearchOperationKeys());
  const mutation = useResearchMutation();
  const name = (id: string) => definitions.find(item => item.id === id)?.name ?? id;
  const unitName = (id: string) => topic.members?.find(member => member.unitId === id)?.unitName ?? id;
  const accept = (value: Draft<DraftRow>) => {
    setLoadedDraft(value); setDirty(false);
    form.setFieldsValue({ items: value.items.map(item => ({ indicatorDefinitionId: item.indicatorDefinitionId, targetQuantity: item.targetQuantity, ...('unitId' in item ? { unitId: item.unitId } : {}) })) });
  };
  return <Space orientation="vertical" style={{ width: '100%' }}>
    <Typography.Text>以下为当前已下发累计要求；保存草稿不会改变这些要求。</Typography.Text>
    {effective.error && <Alert type="error" title={effective.error} action={<Button onClick={effective.refresh}>重试</Button>} />}
    <Table<DraftRow> rowKey="id" loading={effective.loading} pagination={false} dataSource={effective.data ?? []} columns={[
      ...(allocation ? [{ title: '单位', render: (_: unknown, row: DraftRow) => 'unitId' in row ? unitName(row.unitId) : '' }] : []),
      { title: '指标', render: (_, row) => name(row.indicatorDefinitionId) }, { title: '累计要求', dataIndex: 'targetQuantity' }, { title: '发布版本', dataIndex: 'version' },
    ]} />
    {canEdit && <>
      {!hasResearchMetadataRequest() && <Alert type="warning" title="草稿编辑待接入" description="公共 HTTP 尚不能读取草稿版本响应头，保存与下发暂不可用；已下发指标可正常查询。" />}
      {mutation.error && <Alert type="error" title={mutation.error} description="版本冲突时请重新读取草稿；重新读取将放弃当前未保存的编辑。" />}
      <Button disabled={!hasResearchMetadataRequest() || mutation.busy} onClick={() => void mutation.run(async () => accept(await (allocation ? researchApi.allocationDraft(topic.id, nodeId) : researchApi.targetDraft(topic.id, nodeId))))}>读取草稿 / 放弃本地修改</Button>
      {loadedDraft && <Form form={form} layout="vertical" disabled={mutation.busy} onValuesChange={() => setDirty(true)} onFinish={values => void mutation.run(async () => {
        const items = values.items ?? [];
        const body = { nodeId, draftVersion: loadedDraft.draftVersion };
        const saved = allocation
          ? await researchApi.saveAllocations(topic.id, { ...body, allocations: items.map(item => ({ ...item, unitId: item.unitId! })) })
          : await researchApi.saveTargets(topic.id, { ...body, targets: items.map(({ indicatorDefinitionId, targetQuantity }) => ({ indicatorDefinitionId, targetQuantity })) });
        accept(saved); message.success('草稿已保存，尚未下发');
      })}>
        <Typography.Paragraph>草稿版本 {loadedDraft.draftVersion}{allocation && `；绑定课题发布版本 ${loadedDraft.topicIndicatorVersion}`}。本次保存完整替换该节点草稿；删除全部行后保存将清空草稿。</Typography.Paragraph>
        <Form.List name="items">{(fields, { add, remove }) => <>
          {fields.map(field => <Space key={field.key} align="start" wrap>
            {allocation && <Form.Item name={[field.name, 'unitId']} label="成员单位" rules={[{ required: true }]}><Select style={{ width: 240 }} options={topic.members?.filter(member => member.enabled).map(member => ({ value: member.unitId, label: member.unitName }))} /></Form.Item>}
            <Form.Item name={[field.name, 'indicatorDefinitionId']} label="指标" rules={[{ required: true }]}><Select style={{ width: 220 }} options={definitions.filter(item => item.enabled).map(item => ({ value: item.id, label: item.name }))} /></Form.Item>
            <Form.Item name={[field.name, 'targetQuantity']} label="累计数量" rules={[{ required: true }, { type: 'integer', min: 0, max: 2147483647 }]}><InputNumber min={0} max={2147483647} precision={0} /></Form.Item>
            <Button style={{ marginTop: 30 }} onClick={() => { remove(field.name); setDirty(true); }}>删除行</Button>
          </Space>)}
          <Button onClick={() => { add({ targetQuantity: 0 }); setDirty(true); }}>新增一行</Button>
        </>}</Form.List>
        <Space style={{ marginTop: 16 }}>
          <Button htmlType="submit" type="primary" loading={mutation.busy}>保存完整草稿</Button>
          {canPublish && <Button disabled={dirty || loadedDraft.draftVersion < 1 || loadedDraft.items.length === 0 || mutation.busy} onClick={() => void mutation.run(async () => {
            const body = { nodeId, draftVersion: loadedDraft.draftVersion };
            const key = keys.forRequest(`${allocation ? 'allocations' : 'targets'}:${topic.id}`, body);
            if (allocation) await researchApi.publishAllocations(topic.id, body, key); else await researchApi.publishTargets(topic.id, body, key);
            message.success('指标已下发'); effective.refresh(); setLoadedDraft(undefined);
          })}>下发已保存草稿</Button>}
        </Space>
      </Form>}
    </>}
  </Space>;
}
