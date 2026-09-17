import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Form, Input, InputNumber, Modal, Result, Row, Select, Space, Spin, Table, Tag, Typography, message } from 'antd';
import { ArrowLeftOutlined, EditOutlined, EyeOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { topicApi, type ApiTopic, type TopicMember, type TopicWrite } from '../../api/topic-api';
import { indicatorApi, type IndicatorDefinition, type IndicatorTarget, type TimeNode } from '../../api/indicator-api';
import { systemApi, type ApiUnit } from '../../api/system-api';
import { useSessionStore } from '../../store/session';

const statusLabel: Record<ApiTopic['status'], string> = { DRAFT: '草稿', ACTIVE: '实施中', PAUSED: '已暂停', CLOSED: '已结题' };
const statusColor: Record<ApiTopic['status'], string> = { DRAFT: 'default', ACTIVE: 'green', PAUSED: 'orange', CLOSED: 'default' };
const has = (actions: string[], permission: string) => actions.includes(permission);

export function RealIndicatorConfigPage() {
  const navigate = useNavigate();
  const user = useSessionStore((state) => state.user)!;
  const [topics, setTopics] = useState<ApiTopic[]>([]);
  const [units, setUnits] = useState<ApiUnit[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState({ keyword: '', status: '', leadUnitId: '' });
  const canManage = user.roleCode === 'RESEARCH_ASSISTANT' && has(user.actionPermissions, 'topic.manage');
  const canAllocate = (topic: ApiTopic) => has(user.actionPermissions, 'unit-allocation.manage')
    && user.memberships.some((membership) => membership.topicId === topic.id && membership.membershipType === 'LEAD' && membership.enabled);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [page, unitRows] = await Promise.all([topicApi.list(), systemApi.units()]);
      setTopics(page.items); setUnits(unitRows.filter((unit) => unit.enabled));
    } catch (error) { message.error(error instanceof Error ? error.message : '课题加载失败'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const unitMap = useMemo(() => Object.fromEntries(units.map((unit) => [unit.id, unit.name])), [units]);
  const rows = topics.filter((topic) => (!query.keyword || `${topic.code}${topic.name}`.toLowerCase().includes(query.keyword.toLowerCase()))
    && (!query.status || topic.status === query.status) && (!query.leadUnitId || topic.leadUnitId === query.leadUnitId));
  const toggle = (topic: ApiTopic) => Modal.confirm({
    title: topic.enabled ? '确认停用课题' : '确认启用课题',
    content: topic.enabled ? '停用后保留历史数据，但不能继续新增业务数据。' : '启用后课题恢复正常使用。',
    onOk: async () => { await topicApi.setStatus(topic.id, !topic.enabled, topic.status); message.success('课题状态已更新'); await load(); },
  });
  return <div className="indicator-config-page">
    <Card style={{ marginBottom: 18 }}><Form layout="inline">
      <Form.Item label="课题"><Input allowClear value={query.keyword} placeholder="编号或名称" onChange={(event) => setQuery({ ...query, keyword: event.target.value })} /></Form.Item>
      <Form.Item label="状态"><Select allowClear style={{ width: 160 }} value={query.status || undefined} onChange={(value) => setQuery({ ...query, status: value ?? '' })} options={Object.entries(statusLabel).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item label="牵头单位"><Select allowClear showSearch optionFilterProp="label" style={{ width: 240 }} value={query.leadUnitId || undefined} onChange={(value) => setQuery({ ...query, leadUnitId: value ?? '' })} options={units.map((unit) => ({ value: unit.id, label: unit.name }))} /></Form.Item>
      <Form.Item><Space><Button onClick={() => setQuery({ keyword: '', status: '', leadUnitId: '' })}>重置</Button><Button icon={<ReloadOutlined />} onClick={() => void load()}>刷新</Button></Space></Form.Item>
    </Form></Card>
    <Card title={<Space>课题列表<Typography.Text type="secondary">共 {rows.length} 个课题</Typography.Text></Space>} extra={canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/indicator/topic/new')}>新建课题</Button>}>
      <Table loading={loading} rowKey="id" dataSource={rows} pagination={false} columns={[
        { title: '课题', render: (_: unknown, row: ApiTopic) => <Space direction="vertical" size={0}><span><Tag color="blue">{row.code}</Tag><b>{row.name}</b></span><Typography.Text type="secondary">{row.summary || '暂无研究内容摘要'}</Typography.Text></Space> },
        { title: '牵头单位', render: (_: unknown, row: ApiTopic) => unitMap[row.leadUnitId] ?? row.leadUnitId },
        { title: '承担单位', render: (_: unknown, row: ApiTopic) => `${row.members.filter((member) => member.enabled && member.membershipType === 'PARTICIPANT').length} 个` },
        { title: '状态', render: (_: unknown, row: ApiTopic) => <Tag color={row.enabled ? statusColor[row.status] : 'default'}>{row.enabled ? statusLabel[row.status] : '已停用'}</Tag> },
        { title: '操作', width: 300, render: (_: unknown, row: ApiTopic) => <Space><Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/indicator/topic/${row.id}?mode=view`)}>详情</Button>{canManage && <Button type="link" icon={<EditOutlined />} onClick={() => navigate(`/indicator/topic/${row.id}`)}>编辑</Button>}{canAllocate(row) && <Button type="link" onClick={() => navigate(`/indicator/topic/${row.id}?mode=allocation`)}>分配指标</Button>}{canManage && <Button type="link" onClick={() => toggle(row)}>{row.enabled ? '停用' : '启用'}</Button>}</Space> },
      ]} />
    </Card>
  </div>;
}

interface TopicFormValues { code: string; name: string; summary?: string; leadUnitId: string; participantUnitIds: string[]; startDate?: string; endDate?: string }

export function RealTopicIndicatorConfigPage() {
  const { topicId } = useParams<{ topicId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const user = useSessionStore((state) => state.user)!;
  const [form] = Form.useForm<TopicFormValues>();
  const selectedLeadUnitId = Form.useWatch('leadUnitId', form);
  const isNew = topicId === 'new';
  const viewOnly = searchParams.get('mode') === 'view';
  const [loading, setLoading] = useState(true);
  const [topic, setTopic] = useState<ApiTopic | null>(null);
  const [units, setUnits] = useState<ApiUnit[]>([]);
  const [nodes, setNodes] = useState<TimeNode[]>([]);
  const [definitions, setDefinitions] = useState<IndicatorDefinition[]>([]);
  const [nodeId, setNodeId] = useState<string>();
  const [targets, setTargets] = useState<Record<string, number>>({});
  const [effectiveTargets, setEffectiveTargets] = useState<IndicatorTarget[]>([]);
  const [targetDraftVersion, setTargetDraftVersion] = useState(0);
  const [allocations, setAllocations] = useState<Record<string, number>>({});
  const [allocationDraftVersion, setAllocationDraftVersion] = useState(0);
  const isResearchAssistant = user.roleCode === 'RESEARCH_ASSISTANT';
  const isCurrentTopicLead = Boolean(topic && user.memberships.some((membership) => membership.topicId === topic.id && membership.membershipType === 'LEAD' && membership.enabled));
  const canManageTopic = isResearchAssistant && has(user.actionPermissions, 'topic.manage') && !viewOnly;
  const canManageMembers = isCurrentTopicLead && has(user.actionPermissions, 'topic-unit.manage') && !viewOnly;
  const canManageTargets = isResearchAssistant && has(user.actionPermissions, 'indicator.manage') && has(user.actionPermissions, 'topic-indicator.publish') && !viewOnly;
  const canManageAllocations = isCurrentTopicLead && has(user.actionPermissions, 'unit-allocation.manage') && has(user.actionPermissions, 'unit-allocation.publish') && !viewOnly;

  useEffect(() => {
    void (async () => {
      setLoading(true);
      try {
        const [unitRows, nodeRows, definitionRows, topicRow] = await Promise.all([
          systemApi.units(), indicatorApi.nodes(), indicatorApi.definitions(), isNew ? Promise.resolve(null) : topicApi.get(topicId!),
        ]);
        setUnits(unitRows.filter((unit) => unit.enabled)); setNodes(nodeRows.filter((node) => node.enabled)); setDefinitions(definitionRows.filter((item) => item.enabled)); setTopic(topicRow);
        const latestNode = [...nodeRows].filter((node) => node.enabled).sort((a, b) => b.sortOrder - a.sortOrder)[0]; setNodeId(latestNode?.id);
        if (topicRow) form.setFieldsValue({ code: topicRow.code, name: topicRow.name, summary: topicRow.summary, leadUnitId: topicRow.leadUnitId, participantUnitIds: topicRow.members.filter((m) => m.enabled && m.membershipType === 'PARTICIPANT').map((m) => m.unitId), startDate: topicRow.startDate, endDate: topicRow.endDate });
      } catch (error) { message.error(error instanceof Error ? error.message : '课题详情加载失败'); }
      finally { setLoading(false); }
    })();
  }, [form, isNew, topicId]);

  const loadNode = useCallback(async (selectedNodeId: string) => {
    if (!topic) return;
    try {
      const [effective, effectiveAllocations] = await Promise.all([
        indicatorApi.targets(topic.id, selectedNodeId),
        indicatorApi.allocations(topic.id, selectedNodeId),
      ]);
      setEffectiveTargets(effective.rows);
      const targetSource = canManageTargets ? await indicatorApi.targets(topic.id, selectedNodeId, 'draft') : effective;
      const targetRows = targetSource.rows.length ? targetSource.rows : effective.rows;
      setTargetDraftVersion(targetSource.draftVersion); setTargets(Object.fromEntries(targetRows.map((row) => [row.indicatorDefinitionId, row.targetQuantity])));
      const allocationSource = canManageAllocations ? await indicatorApi.allocations(topic.id, selectedNodeId, 'draft') : effectiveAllocations;
      const allocationRows = allocationSource.rows.length ? allocationSource.rows : effectiveAllocations.rows;
      setAllocationDraftVersion(allocationSource.draftVersion); setAllocations(Object.fromEntries(allocationRows.map((row) => [`${row.unitId}:${row.indicatorDefinitionId}`, row.targetQuantity])));
    } catch (error) { message.error(error instanceof Error ? error.message : '指标数据加载失败'); }
  }, [canManageAllocations, canManageTargets, topic]);
  useEffect(() => { if (nodeId && topic) void loadNode(nodeId); }, [loadNode, nodeId, topic]);

  const saveTopic = async () => {
    const values = await form.validateFields();
    const data: TopicWrite = { ...values, participantUnitIds: (values.participantUnitIds ?? []).filter((unitId) => unitId !== values.leadUnitId), recordVersion: topic?.recordVersion };
    try {
      const saved = topic ? await topicApi.update(topic.id, data) : await topicApi.create(data);
      if (!topic && nodeId && canManageTargets) {
        const savedDraft = await indicatorApi.saveTargets(saved.id, nodeId, 0, definitions.map((definition) => ({ indicatorDefinitionId: definition.id, targetQuantity: targets[definition.id] ?? 0 })));
        await indicatorApi.publishTargets(saved.id, nodeId, savedDraft.draftVersion);
      }
      message.success(topic ? '课题信息已保存' : '课题已创建并下发指标'); navigate('/indicator');
    } catch (error) { message.error(error instanceof Error ? error.message : '课题保存失败'); }
  };
  const saveTargets = async () => {
    if (!topic || !nodeId) return;
    try { const draft = await indicatorApi.saveTargets(topic.id, nodeId, targetDraftVersion, definitions.map((definition) => ({ indicatorDefinitionId: definition.id, targetQuantity: targets[definition.id] ?? 0 }))); await indicatorApi.publishTargets(topic.id, nodeId, draft.draftVersion); message.success('课题总体指标已下发'); await loadNode(nodeId); }
    catch (error) { message.error(error instanceof Error ? error.message : '指标下发失败'); }
  };
  const activeMembers = (topic?.members ?? []).filter((member) => member.enabled);
  const saveAllocations = async () => {
    if (!topic || !nodeId) return;
    const rows = activeMembers.flatMap((member) => definitions.map((definition) => ({ unitId: member.unitId, indicatorDefinitionId: definition.id, targetQuantity: allocations[`${member.unitId}:${definition.id}`] ?? 0 })));
    try { const draft = await indicatorApi.saveAllocations(topic.id, nodeId, allocationDraftVersion, rows); await indicatorApi.publishAllocations(topic.id, nodeId, draft.draftVersion); message.success('单位指标分配已下发'); await loadNode(nodeId); }
    catch (error) { message.error(error instanceof Error ? error.message : '单位指标下发失败'); }
  };
  const updateMember = async (unitId: string, enabled: boolean) => {
    if (!topic) return;
    try {
      const existing = topic.members.find((member) => member.unitId === unitId && member.membershipType === 'PARTICIPANT');
      if (existing) await topicApi.setMemberStatus(topic.id, existing.id, enabled); else await topicApi.addMember(topic.id, unitId);
      const refreshed = await topicApi.get(topic.id); setTopic(refreshed); message.success('承担单位已更新');
    } catch (error) { message.error(error instanceof Error ? error.message : '成员关系更新失败'); }
  };
  if (loading) return <Card><Spin /></Card>;
  if (!isNew && !topic) return <Result status="404" title="课题不存在" extra={<Button onClick={() => navigate('/indicator')}>返回</Button>} />;
  const unitMap = Object.fromEntries(units.map((unit) => [unit.id, unit.name]));
  const allocationTargets = effectiveTargets.length ? effectiveTargets : definitions.map((definition) => ({ indicatorDefinitionId: definition.id, targetQuantity: targets[definition.id] ?? 0 } as IndicatorTarget));
  return <div className="topic-indicator-editor">
    <Button style={{ marginBottom: 16 }} icon={<ArrowLeftOutlined />} onClick={() => navigate('/indicator')}>返回课题列表</Button>
    <Row gutter={[18, 18]}>
      <Col span={10}><Card title={isNew ? '新建课题' : '课题信息'} extra={canManageTopic && <Button type="primary" onClick={() => void saveTopic()}>保存</Button>}>
        <Form form={form} layout="vertical" disabled={!canManageTopic} initialValues={{ participantUnitIds: [] }}>
          <Row gutter={12}><Col span={8}><Form.Item name="code" label="课题编号" rules={[{ required: true }]}><Input /></Form.Item></Col><Col span={16}><Form.Item name="name" label="课题名称" rules={[{ required: true }]}><Input /></Form.Item></Col></Row>
          <Form.Item name="leadUnitId" label="牵头单位" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={units.map((unit) => ({ value: unit.id, label: unit.name }))} /></Form.Item>
          {isNew && <Form.Item name="participantUnitIds" label="承担单位"><Select mode="multiple" options={units.filter((unit) => unit.id !== selectedLeadUnitId).map((unit) => ({ value: unit.id, label: unit.name }))} /></Form.Item>}
          <Row gutter={12}><Col span={12}><Form.Item name="startDate" label="开始日期"><Input type="date" /></Form.Item></Col><Col span={12}><Form.Item name="endDate" label="结束日期"><Input type="date" /></Form.Item></Col></Row>
          <Form.Item name="summary" label="研究内容摘要"><Input.TextArea rows={4} /></Form.Item>
        </Form>
        {!isNew && canManageMembers && <><Typography.Title level={5}>承担单位维护</Typography.Title><Select mode="multiple" style={{ width: '100%' }} value={activeMembers.filter((member) => member.membershipType === 'PARTICIPANT').map((member) => member.unitId)} options={units.filter((unit) => unit.id !== topic?.leadUnitId).map((unit) => ({ value: unit.id, label: unit.name }))} onSelect={(value) => void updateMember(value, true)} onDeselect={(value) => void updateMember(value, false)} /></>}
      </Card></Col>
      <Col span={14}><Card title="课题总体指标" extra={<Space><Select style={{ width: 170 }} value={nodeId} onChange={setNodeId} options={nodes.map((node) => ({ value: node.id, label: node.name }))} />{!isNew && canManageTargets && <Button type="primary" onClick={() => void saveTargets()}>确认并下发</Button>}</Space>}>
        {!canManageTargets && <Alert type="info" showIcon style={{ marginBottom: 12 }} message="当前账号为只读查看" />}
        <Table size="small" pagination={false} rowKey="id" dataSource={definitions} columns={[{ title: '指标名称', dataIndex: 'name' }, { title: '类型', render: (_: unknown, row: IndicatorDefinition) => <Tag color={row.category === 'SPECIAL' ? 'purple' : 'blue'}>{row.category === 'SPECIAL' ? '专项' : '基础'}</Tag> }, { title: '单位', dataIndex: 'unit', width: 70 }, { title: '累计目标', width: 130, render: (_: unknown, row: IndicatorDefinition) => <InputNumber min={0} precision={0} disabled={!canManageTargets} value={targets[row.id] ?? 0} onChange={(value) => setTargets({ ...targets, [row.id]: value ?? 0 })} /> }]} />
      </Card></Col>
      {!isNew && <Col span={24}><Card title="单位指标分配" extra={canManageAllocations && <Button type="primary" onClick={() => void saveAllocations()}>确认并下发</Button>}>
        {!canManageAllocations && <Alert type="info" showIcon style={{ marginBottom: 12 }} message="牵头单位可维护分配；其他账号按数据范围查看。" />}
        <Table size="small" pagination={false} rowKey="indicatorDefinitionId" dataSource={allocationTargets} scroll={{ x: 800 }} columns={[{ title: '指标', fixed: 'left', width: 220, render: (_: unknown, row: IndicatorTarget) => definitions.find((item) => item.id === row.indicatorDefinitionId)?.name }, ...activeMembers.map((member: TopicMember) => ({ title: unitMap[member.unitId] ?? member.unitName, width: 150, render: (_: unknown, row: IndicatorTarget) => <InputNumber min={0} precision={0} disabled={!canManageAllocations} value={allocations[`${member.unitId}:${row.indicatorDefinitionId}`] ?? 0} onChange={(value) => setAllocations({ ...allocations, [`${member.unitId}:${row.indicatorDefinitionId}`]: value ?? 0 })} /> })), { title: '课题目标', dataIndex: 'targetQuantity', width: 100 }]} />
      </Card></Col>}
    </Row>
  </div>;
}
