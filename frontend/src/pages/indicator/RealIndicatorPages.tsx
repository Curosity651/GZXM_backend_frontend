import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Form, Input, InputNumber, Modal, Result, Row, Select, Space, Spin, Table, Tag, Typography, message } from 'antd';
import { ArrowLeftOutlined, DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SettingOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { topicApi, type ApiTopic, type TopicMember, type TopicWrite } from '../../api/topic-api';
import { indicatorApi, type IndicatorDefinition, type IndicatorTarget, type TimeNode } from '../../api/indicator-api';
import { systemApi, type ApiUnit } from '../../api/system-api';
import { useSessionStore } from '../../store/session';

const statusLabel: Record<ApiTopic['status'], string> = { DRAFT: '草稿', ACTIVE: '实施中', PAUSED: '已暂停', CLOSED: '已结题' };
const statusColor: Record<ApiTopic['status'], string> = { DRAFT: 'default', ACTIVE: 'green', PAUSED: 'orange', CLOSED: 'default' };
const has = (actions: string[], permission: string) => actions.includes(permission);
type TopicFilterStatus = ApiTopic['status'] | 'STOPPED';
interface TimeNodeForm { name: string; deadline: string; sortOrder: number }

export function RealIndicatorConfigPage() {
  const navigate = useNavigate();
  const user = useSessionStore((state) => state.user)!;
  const [topics, setTopics] = useState<ApiTopic[]>([]);
  const [units, setUnits] = useState<ApiUnit[]>([]);
  const [nodes, setNodes] = useState<TimeNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<{ keyword: string; status: TopicFilterStatus | ''; leadUnitId: string }>({ keyword: '', status: '', leadUnitId: '' });
  const [nodeModalOpen, setNodeModalOpen] = useState(false);
  const [nodeEditorOpen, setNodeEditorOpen] = useState(false);
  const [editingNode, setEditingNode] = useState<TimeNode>();
  const [nodeSaving, setNodeSaving] = useState(false);
  const [nodeForm] = Form.useForm<TimeNodeForm>();
  const canManage = user.roleCode === 'RESEARCH_ASSISTANT' && has(user.actionPermissions, 'topic.manage');
  const canManageNodes = user.roleCode === 'RESEARCH_ASSISTANT' && has(user.actionPermissions, 'indicator.manage');
  const canAllocate = (topic: ApiTopic) => topic.enabled && topic.status === 'ACTIVE' && has(user.actionPermissions, 'unit-allocation.manage')
    && user.memberships.some((membership) => membership.topicId === topic.id && membership.membershipType === 'LEAD' && membership.enabled);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const requests: [ReturnType<typeof topicApi.list>, ReturnType<typeof systemApi.units>, ReturnType<typeof indicatorApi.nodes>] = [
        topicApi.list(), systemApi.units(), indicatorApi.nodes(canManageNodes),
      ];
      const [page, unitRows, nodeRows] = await Promise.all(requests);
      setTopics(page.items); setUnits(unitRows.filter((unit) => unit.enabled)); setNodes(nodeRows);
    } catch (error) { message.error(error instanceof Error ? error.message : '课题加载失败'); }
    finally { setLoading(false); }
  }, [canManageNodes]);
  useEffect(() => { void load(); }, [load]);

  const unitMap = useMemo(() => Object.fromEntries(units.map((unit) => [unit.id, unit.name])), [units]);
  const rows = topics.filter((topic) => {
    const statusMatches = !query.status || query.status === 'STOPPED' ? !query.status || !topic.enabled : topic.enabled && topic.status === query.status;
    return statusMatches && (!query.keyword || `${topic.code}${topic.name}`.toLowerCase().includes(query.keyword.toLowerCase()))
      && (!query.leadUnitId || topic.leadUnitId === query.leadUnitId);
  });

  const setTopicState = (topic: ApiTopic, enabled: boolean, status: ApiTopic['status'], title: string, content: string) => Modal.confirm({
    title, content, okText: '确认', cancelText: '取消',
    onOk: async () => {
      try { await topicApi.setStatus(topic.id, enabled, status); message.success('课题状态已更新'); await load(); }
      catch (error) { message.error(error instanceof Error ? error.message : '状态修改失败'); }
    },
  });

  const openNodeEditor = (node?: TimeNode) => {
    setEditingNode(node);
    nodeForm.setFieldsValue(node ? { name: node.name, deadline: node.deadline, sortOrder: node.sortOrder } : {
      name: '', deadline: '', sortOrder: Math.max(0, ...nodes.map((item) => item.sortOrder)) + 1,
    });
    setNodeEditorOpen(true);
  };
  const saveNode = async () => {
    const values = await nodeForm.validateFields(); setNodeSaving(true);
    try {
      if (editingNode) await indicatorApi.updateNode(editingNode.id, values); else await indicatorApi.createNode(values);
      setNodeEditorOpen(false); setEditingNode(undefined); nodeForm.resetFields();
      setNodes(await indicatorApi.nodes(true)); message.success(editingNode ? '时间节点已更新' : '时间节点已创建');
    } catch (error) { message.error(error instanceof Error ? error.message : '时间节点保存失败'); }
    finally { setNodeSaving(false); }
  };
  const setNodeStatus = async (node: TimeNode, enabled: boolean) => {
    try { await indicatorApi.setNodeStatus(node.id, enabled); setNodes(await indicatorApi.nodes(true)); message.success(enabled ? '时间节点已启用' : '时间节点已停用'); }
    catch (error) { message.error(error instanceof Error ? error.message : '时间节点状态修改失败'); }
  };
  const deleteNode = (node: TimeNode) => Modal.confirm({
    title: '确认删除时间节点',
    content: `确定删除“${node.name}”吗？删除后无法恢复。已经产生指标、分配或成果数据的节点不能删除。`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      try { await indicatorApi.deleteNode(node.id); setNodes(await indicatorApi.nodes(true)); message.success('时间节点已删除'); }
      catch (error) { message.error(error instanceof Error ? error.message : '时间节点删除失败'); }
    },
  });

  const topicActions = (topic: ApiTopic) => {
    if (!canManage) return canAllocate(topic) ? <Button type="link" onClick={() => navigate(`/indicator/topic/${topic.id}?mode=allocation`)}>分配指标</Button> : null;
    if (!topic.enabled) return <Button type="link" onClick={() => setTopicState(topic, true, topic.status, '确认启用课题', '启用后恢复到停用前的业务状态。')}>启用</Button>;
    return <Space>
      {(topic.status === 'DRAFT' || topic.status === 'ACTIVE') && <Button type="link" icon={<EditOutlined />} onClick={() => navigate(`/indicator/topic/${topic.id}`)}>编辑</Button>}
      {topic.status === 'ACTIVE' && <Button type="link" onClick={() => setTopicState(topic, true, 'PAUSED', '确认暂停课题', '暂停期间课题配置及业务数据只读。')}>暂停</Button>}
      {topic.status === 'PAUSED' && <Button type="link" onClick={() => setTopicState(topic, true, 'ACTIVE', '确认恢复实施', '课题将恢复为实施中状态。')}>恢复实施</Button>}
      {(topic.status === 'ACTIVE' || topic.status === 'PAUSED') && <Button type="link" onClick={() => setTopicState(topic, true, 'CLOSED', '确认课题结题', '结题后课题配置只读。')}>结题</Button>}
      <Button type="link" danger onClick={() => setTopicState(topic, false, topic.status, '确认停用课题', '停用用于软删除，历史数据保留，停用期间全部只读。')}>停用</Button>
    </Space>;
  };

  return <div className="indicator-config-page">
    <Card style={{ marginBottom: 18 }}><Form layout="inline">
      <Form.Item label="课题"><Input allowClear value={query.keyword} placeholder="编号或名称" onChange={(event) => setQuery({ ...query, keyword: event.target.value })} /></Form.Item>
      <Form.Item label="状态"><Select allowClear style={{ width: 160 }} value={query.status || undefined} onChange={(value) => setQuery({ ...query, status: value ?? '' })}
        options={[...Object.entries(statusLabel).map(([value, label]) => ({ value, label })), { value: 'STOPPED', label: '已停用' }]} /></Form.Item>
      <Form.Item label="牵头单位"><Select allowClear showSearch optionFilterProp="label" style={{ width: 240 }} value={query.leadUnitId || undefined} onChange={(value) => setQuery({ ...query, leadUnitId: value ?? '' })} options={units.map((unit) => ({ value: unit.id, label: unit.name }))} /></Form.Item>
      <Form.Item><Space><Button onClick={() => setQuery({ keyword: '', status: '', leadUnitId: '' })}>重置</Button><Button icon={<ReloadOutlined />} onClick={() => void load()}>刷新</Button></Space></Form.Item>
    </Form></Card>
    <Card title={<Space>课题列表<Typography.Text type="secondary">共 {rows.length} 个课题</Typography.Text></Space>} extra={<Space>
      {canManageNodes && <Button icon={<SettingOutlined />} onClick={() => setNodeModalOpen(true)}>时间节点配置</Button>}
      {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/indicator/topic/new')}>新建课题</Button>}
    </Space>}>
      <Table loading={loading} rowKey="id" dataSource={rows} pagination={false} columns={[
        { title: '课题', render: (_: unknown, row: ApiTopic) => <Space direction="vertical" size={0}><span><Tag color="blue">{row.code}</Tag><b>{row.name}</b></span><Typography.Text type="secondary">{row.summary || '暂无研究内容摘要'}</Typography.Text></Space> },
        { title: '牵头单位', render: (_: unknown, row: ApiTopic) => unitMap[row.leadUnitId] ?? row.leadUnitId },
        { title: '承担单位', render: (_: unknown, row: ApiTopic) => `${row.members.filter((member) => member.enabled && member.membershipType === 'PARTICIPANT').length} 个` },
        { title: '状态', render: (_: unknown, row: ApiTopic) => <Tag color={row.enabled ? statusColor[row.status] : 'default'}>{row.enabled ? statusLabel[row.status] : '已停用'}</Tag> },
        { title: '操作', width: 330, render: (_: unknown, row: ApiTopic) => topicActions(row) },
      ]} />
    </Card>

    <Modal title="时间节点配置" width={820} open={nodeModalOpen} onCancel={() => setNodeModalOpen(false)} footer={<Button onClick={() => setNodeModalOpen(false)}>关闭</Button>}>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}><Button type="primary" icon={<PlusOutlined />} onClick={() => openNodeEditor()}>新增时间节点</Button></div>
      <Table rowKey="id" pagination={false} dataSource={nodes} columns={[
        { title: '顺序', dataIndex: 'sortOrder', width: 80 }, { title: '时间阶段', dataIndex: 'name' }, { title: '截止日期', dataIndex: 'deadline', width: 140 },
        { title: '状态', dataIndex: 'enabled', width: 100, render: (enabled: boolean) => <Tag color={enabled ? 'green' : 'default'}>{enabled ? '启用' : '停用'}</Tag> },
        { title: '操作', width: 240, render: (_: unknown, node: TimeNode) => <Space><Button type="link" onClick={() => openNodeEditor(node)}>编辑</Button><Button type="link" onClick={() => void setNodeStatus(node, !node.enabled)}>{node.enabled ? '停用' : '启用'}</Button><Button type="link" danger icon={<DeleteOutlined />} onClick={() => deleteNode(node)}>删除</Button></Space> },
      ]} />
    </Modal>
    <Modal title={editingNode ? '编辑时间节点' : '新增时间节点'} open={nodeEditorOpen} onCancel={() => { setNodeEditorOpen(false); setEditingNode(undefined); nodeForm.resetFields(); }} onOk={() => void saveNode()} confirmLoading={nodeSaving} okText="保存" cancelText="取消">
      <Form form={nodeForm} layout="vertical"><Form.Item name="name" label="时间阶段名称" rules={[{ required: true, message: '请输入时间阶段名称' }]}><Input maxLength={100} placeholder="例如：第一年度" /></Form.Item>
        <Row gutter={16}><Col span={12}><Form.Item name="deadline" label="截止日期" rules={[{ required: true, message: '请选择截止日期' }]}><Input type="date" /></Form.Item></Col><Col span={12}><Form.Item name="sortOrder" label="排序序号" rules={[{ required: true, message: '请输入排序序号' }]}><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item></Col></Row>
      </Form>
    </Modal>
  </div>;
}

interface TopicFormValues { code: string; name: string; summary?: string; leadUnitId: string; participantUnitIds: string[]; startDate?: string; endDate?: string }
type TargetMap = Record<string, Record<string, number>>;
type VersionMap = Record<string, number>;

export function RealTopicIndicatorConfigPage() {
  const { topicId } = useParams<{ topicId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const user = useSessionStore((state) => state.user)!;
  const [form] = Form.useForm<TopicFormValues>();
  const selectedLeadUnitId = Form.useWatch('leadUnitId', form);
  const isNew = topicId === 'new';
  const allocationMode = searchParams.get('mode') === 'allocation';
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [topic, setTopic] = useState<ApiTopic | null>(null);
  const [units, setUnits] = useState<ApiUnit[]>([]);
  const [nodes, setNodes] = useState<TimeNode[]>([]);
  const [definitions, setDefinitions] = useState<IndicatorDefinition[]>([]);
  const [nodeId, setNodeId] = useState<string>();
  const [targetsByNode, setTargetsByNode] = useState<TargetMap>({});
  const [targetDraftVersions, setTargetDraftVersions] = useState<VersionMap>({});
  const [allocationTargets, setAllocationTargets] = useState<IndicatorTarget[]>([]);
  const [allocations, setAllocations] = useState<Record<string, number>>({});
  const [allocationDraftVersion, setAllocationDraftVersion] = useState(0);
  const [allocationAssigned, setAllocationAssigned] = useState(false);
  const isResearchAssistant = user.roleCode === 'RESEARCH_ASSISTANT';
  const topicEditable = isNew || Boolean(topic?.enabled && (topic.status === 'DRAFT' || topic.status === 'ACTIVE'));
  const isCurrentTopicLead = Boolean(topic && user.memberships.some((membership) => membership.topicId === topic.id && membership.membershipType === 'LEAD' && membership.enabled));
  const canManageTopic = isResearchAssistant && has(user.actionPermissions, 'topic.manage') && topicEditable && !allocationMode;
  const canManageTargets = isResearchAssistant && has(user.actionPermissions, 'indicator.manage') && has(user.actionPermissions, 'topic-indicator.publish') && topicEditable && !allocationMode;
  const canManageAllocations = isCurrentTopicLead && has(user.actionPermissions, 'unit-allocation.manage') && has(user.actionPermissions, 'unit-allocation.publish') && allocationMode && topic?.enabled && topic.status === 'ACTIVE';

  useEffect(() => {
    void (async () => {
      setLoading(true);
      try {
        const [unitRows, nodeRows, definitionRows, topicRow] = await Promise.all([
          systemApi.units(), indicatorApi.nodes(), indicatorApi.definitions(), isNew ? Promise.resolve(null) : topicApi.get(topicId!),
        ]);
        const activeNodes = nodeRows.filter((node) => node.enabled).sort((a, b) => a.sortOrder - b.sortOrder);
        setUnits(unitRows.filter((unit) => unit.enabled)); setNodes(activeNodes); setDefinitions(definitionRows.filter((item) => item.enabled)); setTopic(topicRow); setNodeId(activeNodes[0]?.id);
        setTargetsByNode(Object.fromEntries(activeNodes.map((node) => [node.id, Object.fromEntries(definitionRows.filter((item) => item.enabled).map((definition) => [definition.id, 0]))])));
        if (topicRow) form.setFieldsValue({ code: topicRow.code, name: topicRow.name, summary: topicRow.summary, leadUnitId: topicRow.leadUnitId, participantUnitIds: topicRow.members.filter((member) => member.enabled && member.membershipType === 'PARTICIPANT').map((member) => member.unitId), startDate: topicRow.startDate, endDate: topicRow.endDate });
      } catch (error) { message.error(error instanceof Error ? error.message : '课题配置加载失败'); }
      finally { setLoading(false); }
    })();
  }, [form, isNew, topicId]);

  const loadTargets = useCallback(async () => {
    if (!topic || allocationMode || !nodes.length) return;
    try {
      const loaded = await Promise.all(nodes.map(async (node) => {
        const effective = await indicatorApi.targets(topic.id, node.id);
        const draft = canManageTargets ? await indicatorApi.targets(topic.id, node.id, 'draft') : effective;
        return { nodeId: node.id, source: draft.rows.length ? draft.rows : effective.rows, version: draft.draftVersion };
      }));
      setTargetsByNode(Object.fromEntries(loaded.map((item) => [item.nodeId, Object.fromEntries(definitions.map((definition) => [definition.id, item.source.find((row) => row.indicatorDefinitionId === definition.id)?.targetQuantity ?? 0]))])));
      setTargetDraftVersions(Object.fromEntries(loaded.map((item) => [item.nodeId, item.version])));
    } catch (error) { message.error(error instanceof Error ? error.message : '总体指标加载失败'); }
  }, [allocationMode, canManageTargets, definitions, nodes, topic]);
  useEffect(() => { void loadTargets(); }, [loadTargets]);

  const loadAllocationNode = useCallback(async () => {
    if (!topic || !nodeId || !allocationMode) return;
    try {
      const [effective, allocationEffective] = await Promise.all([indicatorApi.targets(topic.id, nodeId), indicatorApi.allocations(topic.id, nodeId)]);
      const allocationDraft = canManageAllocations ? await indicatorApi.allocations(topic.id, nodeId, 'draft') : allocationEffective;
      setAllocationTargets(effective.rows); setAllocationDraftVersion(allocationDraft.draftVersion); setAllocationAssigned(allocationEffective.rows.length > 0);
      setAllocations(Object.fromEntries(allocationEffective.rows.map((row) => [`${row.unitId}:${row.indicatorDefinitionId}`, row.targetQuantity])));
    } catch (error) { message.error(error instanceof Error ? error.message : '单位指标加载失败'); }
  }, [allocationMode, canManageAllocations, nodeId, topic]);
  useEffect(() => { void loadAllocationNode(); }, [loadAllocationNode]);

  const validateTargets = () => {
    if (!nodes.length) { message.warning('请先配置并启用至少一个时间节点'); return false; }
    for (const definition of definitions) {
      let previous = 0;
      for (const node of nodes) {
        const value = targetsByNode[node.id]?.[definition.id] ?? 0;
        if (value < previous) { message.warning(`${node.name}的“${definition.name}”累计目标不能低于前序时间节点`); return false; }
        previous = value;
      }
    }
    for (const node of nodes) {
      for (const special of definitions.filter((item) => item.category === 'SPECIAL')) {
        const base = definitions.find((item) => item.category === 'BASE' && item.achievementType === special.achievementType);
        if (base && (targetsByNode[node.id]?.[special.id] ?? 0) > (targetsByNode[node.id]?.[base.id] ?? 0)) {
          message.warning(`${node.name}的“${special.name}”不能超过同类型总目标`); return false;
        }
      }
    }
    return true;
  };

  const saveConfiguration = async (submit: boolean) => {
    if (!canManageTopic || !canManageTargets || !validateTargets()) return;
    const values = await form.validateFields(); setSaving(true);
    try {
      const data: TopicWrite = { ...values, participantUnitIds: (values.participantUnitIds ?? []).filter((unitId) => unitId !== values.leadUnitId), recordVersion: topic?.recordVersion };
      const saved = topic ? await topicApi.update(topic.id, data) : await topicApi.create(data);
      const versions: VersionMap = {};
      for (const node of nodes) {
        const draft = await indicatorApi.saveTargets(saved.id, node.id, targetDraftVersions[node.id] ?? 0,
          definitions.map((definition) => ({ indicatorDefinitionId: definition.id, targetQuantity: targetsByNode[node.id]?.[definition.id] ?? 0 })));
        versions[node.id] = draft.draftVersion;
      }
      if (submit) {
        for (const node of nodes) await indicatorApi.publishTargets(saved.id, node.id, versions[node.id]);
        await topicApi.setStatus(saved.id, true, 'ACTIVE');
      }
      message.success(submit ? (topic?.status === 'ACTIVE' ? '课题配置修改已提交' : '课题已提交并进入实施中') : (topic?.status === 'ACTIVE' ? '课题修改草稿已保存' : '课题草稿已保存'));
      navigate('/indicator');
    } catch (error) { message.error(error instanceof Error ? error.message : '课题配置保存失败'); }
    finally { setSaving(false); }
  };

  const activeMembers = (topic?.members ?? []).filter((member) => member.enabled);
  const confirmAllocations = () => {
    if (!topic || !nodeId || !canManageAllocations) return;
    const rows = activeMembers.flatMap((member) => definitions.map((definition) => ({ unitId: member.unitId, indicatorDefinitionId: definition.id, targetQuantity: allocations[`${member.unitId}:${definition.id}`] ?? 0 })));
    const selectedNode = nodes.find((node) => node.id === nodeId);
    Modal.confirm({
      title: allocationAssigned ? '确认更新指标分配' : '确认提交指标分配',
      content: `确认提交“${selectedNode?.name ?? '当前时间节点'}”的单位指标分配吗？提交后立即生效。`,
      okText: '确认分配', cancelText: '取消',
      onOk: async () => {
        setSaving(true);
        try {
          await indicatorApi.confirmAllocations(topic.id, nodeId, allocationDraftVersion, rows);
          message.success('单位指标分配已确认并生效'); await loadAllocationNode();
        } catch (error) { message.error(error instanceof Error ? error.message : '单位指标分配失败'); throw error; }
        finally { setSaving(false); }
      },
    });
  };

  if (loading) return <Card><Spin /></Card>;
  if (!isNew && !topic) return <Result status="404" title="课题不存在" extra={<Button onClick={() => navigate('/indicator')}>返回</Button>} />;
  if (allocationMode && !topic) return null;
  const unitMap = Object.fromEntries(units.map((unit) => [unit.id, unit.name]));
  const currentTargets = nodeId ? targetsByNode[nodeId] ?? {} : {};
  const allocationRows = allocationTargets.length ? allocationTargets : definitions.map((definition) => ({ indicatorDefinitionId: definition.id, targetQuantity: 0 } as IndicatorTarget));

  if (allocationMode) return <div className="topic-indicator-editor">
    <Button style={{ marginBottom: 16 }} icon={<ArrowLeftOutlined />} onClick={() => navigate('/indicator')}>返回课题列表</Button>
    <Row gutter={[18, 18]}>
      <Col span={24}><Card extra={<Space><Tag color={allocationAssigned ? 'green' : 'default'}>{allocationAssigned ? '已分配' : '未分配'}</Tag><span>时间节点</span><Select style={{ width: 180 }} value={nodeId} onChange={setNodeId} options={nodes.map((node) => ({ value: node.id, label: node.name }))} />
        {canManageAllocations && <Button type="primary" loading={saving} onClick={confirmAllocations}>提交分配</Button>}</Space>}>
        {!canManageAllocations && <Alert type="info" showIcon style={{ marginBottom: 12 }} message="当前账号为只读查看，或课题已暂停、结题、停用。" />}
        {!nodes.length && <Alert type="warning" showIcon style={{ marginBottom: 12 }} message="科研助理尚未配置时间节点，暂时无法分配指标。" />}
        <Table size="small" pagination={false} rowKey="indicatorDefinitionId" dataSource={allocationRows} scroll={{ x: 800 }} columns={[
          { title: '指标', fixed: 'left', width: 220, render: (_: unknown, row: IndicatorTarget) => definitions.find((item) => item.id === row.indicatorDefinitionId)?.name },
          ...activeMembers.map((member: TopicMember) => ({ title: unitMap[member.unitId] ?? member.unitName, width: 150, render: (_: unknown, row: IndicatorTarget) => <InputNumber min={0} precision={0} disabled={!canManageAllocations} value={allocations[`${member.unitId}:${row.indicatorDefinitionId}`] ?? 0} onChange={(value) => setAllocations({ ...allocations, [`${member.unitId}:${row.indicatorDefinitionId}`]: value ?? 0 })} /> })),
          { title: '课题累计目标', dataIndex: 'targetQuantity', width: 120 },
        ]} />
      </Card></Col>
    </Row>
  </div>;

  return <div className="topic-indicator-editor">
    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
      <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/indicator')}>返回课题列表</Button>
      {canManageTopic && canManageTargets && <Space>
        <Button loading={saving} onClick={() => void saveConfiguration(false)}>{topic?.status === 'ACTIVE' ? '保存修改' : '保存草稿'}</Button>
        <Button type="primary" loading={saving} onClick={() => void saveConfiguration(true)}>{topic?.status === 'ACTIVE' ? '提交修改' : '提交'}</Button>
      </Space>}
    </div>
    {!topicEditable && <Alert type="warning" showIcon style={{ marginBottom: 16 }} message={topic?.enabled ? '当前课题已暂停或结题，配置只读。' : '当前课题已停用，配置只读。'} />}
    <Row gutter={[18, 18]}>
      <Col span={10}><Card title={isNew ? '新建课题' : '课题基本信息'}>
        <Form form={form} layout="vertical" disabled={!canManageTopic} initialValues={{ participantUnitIds: [] }}>
          <Row gutter={12}><Col span={8}><Form.Item name="code" label="课题编号" rules={[{ required: true, message: '请输入课题编号' }]}><Input /></Form.Item></Col><Col span={16}><Form.Item name="name" label="课题名称" rules={[{ required: true, message: '请输入课题名称' }]}><Input /></Form.Item></Col></Row>
          <Form.Item name="leadUnitId" label="牵头单位" rules={[{ required: true, message: '请选择牵头单位' }]}><Select showSearch optionFilterProp="label" options={units.map((unit) => ({ value: unit.id, label: unit.name }))} /></Form.Item>
          <Form.Item name="participantUnitIds" label="承担单位"><Select mode="multiple" options={units.filter((unit) => unit.id !== selectedLeadUnitId).map((unit) => ({ value: unit.id, label: unit.name }))} /></Form.Item>
          <Row gutter={12}><Col span={12}><Form.Item name="startDate" label="开始日期"><Input type="date" /></Form.Item></Col><Col span={12}><Form.Item name="endDate" label="结束日期"><Input type="date" /></Form.Item></Col></Row>
          <Form.Item name="summary" label="研究内容摘要"><Input.TextArea rows={4} /></Form.Item>
        </Form>
      </Card></Col>
      <Col span={14}><Card title="课题总体指标" extra={<Space><span>累计时间节点</span><Select style={{ width: 180 }} value={nodeId} onChange={setNodeId} options={nodes.map((node) => ({ value: node.id, label: node.name }))} /></Space>}>
        <Alert type="info" showIcon style={{ marginBottom: 12 }} message="各时间节点填写累计完成要求，后续节点不得低于前序节点。" />
        <Table size="small" pagination={false} rowKey="id" dataSource={definitions} columns={[
          { title: '指标名称', dataIndex: 'name' },
          { title: '类型', render: (_: unknown, row: IndicatorDefinition) => <Tag color={row.category === 'SPECIAL' ? 'purple' : 'blue'}>{row.category === 'SPECIAL' ? '专项' : '基础'}</Tag> },
          { title: '单位', dataIndex: 'unit', width: 70 },
          { title: '累计目标', width: 130, render: (_: unknown, row: IndicatorDefinition) => <InputNumber min={0} precision={0} disabled={!canManageTargets || !nodeId} value={currentTargets[row.id] ?? 0}
            onChange={(value) => nodeId && setTargetsByNode({ ...targetsByNode, [nodeId]: { ...currentTargets, [row.id]: value ?? 0 } })} /> },
        ]} />
      </Card></Col>
    </Row>
  </div>;
}
