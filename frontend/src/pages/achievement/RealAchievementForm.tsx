import { useCallback } from 'react';
import { Alert, Button, Col, Form, Input, Row, Select } from 'antd';
import type { ApiCurrentUser } from '../../api/auth-api';
import { researchApi } from '../../api/research/client';
import type { Achievement, AchievementWriteRequest, IndicatorDefinition, TimeNode, Topic } from '../../api/research/contracts';
import { useResearchLoad, useResearchMutation } from '../indicator/research-hooks';
import { operational } from './real-permissions';
import { detailFields } from './detail-fields';

export function RealAchievementForm({ user, item, topics, nodes, definitions, saved }: {
  user: ApiCurrentUser; item?: Achievement; topics: Topic[]; nodes: TimeNode[]; definitions: IndicatorDefinition[]; saved: () => void;
}) {
  const [form] = Form.useForm<AchievementWriteRequest>();
  const topicId = Form.useWatch('topicId', form) ?? item?.topicId;
  const nodeId = Form.useWatch('nodeId', form) ?? item?.nodeId;
  const definitionId = Form.useWatch('indicatorDefinitionId', form) ?? item?.indicatorDefinitionId;
  const type = definitions.find(definition => definition.id === definitionId)?.achievementType ?? item?.achievementType;
  const allocations = useResearchLoad(useCallback(() => topicId && nodeId ? researchApi.allocations(topicId, nodeId) : Promise.resolve([]), [topicId, nodeId]));
  const mutation = useResearchMutation();
  const ownDefinitions = new Set(allocations.data?.filter(row => row.unitId === user.unitId && row.status === 'PUBLISHED').map(row => row.indicatorDefinitionId));
  const availableDefinitions = definitions.filter(definition => definition.enabled && definition.category === 'BASE' && ownDefinitions.has(definition.id));
  const resetDefinition = () => form.setFieldsValue({ indicatorDefinitionId: undefined, detail: {} });
  return <Form form={form} layout="vertical" initialValues={item ? { topicId: item.topicId, nodeId: item.nodeId, indicatorDefinitionId: item.indicatorDefinitionId, title: item.title, responsiblePerson: item.responsiblePerson, detail: item.detail ?? {} } : { detail: {} }}
    disabled={mutation.busy} onFinish={values => void mutation.run(async () => {
      const body: AchievementWriteRequest = { ...values, ...(item ? { topicId: item.topicId, nodeId: item.nodeId, indicatorDefinitionId: item.indicatorDefinitionId, recordVersion: item.recordVersion } : {}) };
      // Omit materialAttachments: metadata-only editing must preserve existing material links.
      if (item) await researchApi.updateAchievement(item.id, body); else await researchApi.createAchievement(body);
      saved();
    })}>
    <Alert type="info" showIcon title="草稿只要求标题、负责人和有效归属；其余详情可以稍后补充。" style={{ marginBottom: 16 }} />
    {mutation.error && <Alert type="error" title={mutation.error} description="若版本已变化，请关闭后重新打开，核对最新记录再保存。" />}
    <Form.Item name="topicId" label="所属课题" rules={[{ required: true }]}><Select disabled={Boolean(item)} onChange={resetDefinition} options={topics.filter(topic => item ? topic.id === item.topicId : operational(topic) && topic.members?.some(member => member.enabled && member.unitId === user.unitId)).map(topic => ({ value: topic.id, label: topic.name }))} /></Form.Item>
    <Form.Item name="nodeId" label="考核节点" rules={[{ required: true }]}><Select disabled={Boolean(item)} onChange={resetDefinition} options={nodes.filter(node => node.enabled || node.id === item?.nodeId).map(node => ({ value: node.id, label: node.name }))} /></Form.Item>
    {allocations.error && <Alert type="error" title={allocations.error} />}
    <Form.Item name="indicatorDefinitionId" label="本单位已下发基础指标（含零目标）" rules={[{ required: true }]}><Select loading={allocations.loading} disabled={Boolean(item) || allocations.loading || Boolean(allocations.error)} onChange={() => form.setFieldsValue({ detail: {} })} options={(item ? definitions.filter(definition => definition.id === item.indicatorDefinitionId) : availableDefinitions).map(definition => ({ value: definition.id, label: definition.name }))} /></Form.Item>
    <Form.Item name="title" label="成果标题" rules={[{ required: true, whitespace: true }, { max: 500 }]}><Input /></Form.Item>
    <Form.Item name="responsiblePerson" label="负责人" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input /></Form.Item>
    <Row gutter={16}>{(detailFields[type ?? ''] ?? []).map(field => <Col xs={24} md={12} key={`${type}:${field.key}`}>
      <Form.Item name={['detail', field.key]} label={field.label} preserve={false}>
        {field.kind === 'boolean' ? <Select allowClear options={[{ value: true, label: '是' }, { value: false, label: '否' }]} />
          : field.options ? <Select allowClear options={field.options.map(value => ({ value, label: value }))} />
          : field.kind === 'date' ? <Input type="date" />
          : field.maxLength > 500 ? <Input.TextArea maxLength={field.maxLength} rows={3} /> : <Input maxLength={field.maxLength} />}
      </Form.Item>
    </Col>)}</Row>
    <Alert type="warning" showIcon title="材料上传暂不可用" description="成果文件服务尚待接入。保存详情不会删除已有材料；正式和补充提交仍执行后端材料校验。" style={{ marginBottom: 16 }} />
    <Button type="primary" htmlType="submit" loading={mutation.busy} disabled={!item && (allocations.loading || Boolean(allocations.error))}>保存成果草稿</Button>
  </Form>;
}
