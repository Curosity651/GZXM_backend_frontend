import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Descriptions, Drawer, Form, Input, Modal, Progress, Row, Select, Space, Statistic, Table, Tag, Typography, message } from 'antd';
import { CheckOutlined, DownOutlined, EditOutlined, EyeOutlined, PlusOutlined, ReloadOutlined, RollbackOutlined, SearchOutlined, SendOutlined, UpOutlined, UploadOutlined } from '@ant-design/icons';
import { achievementApi, type ApiAchievement, type AchievementProgress, type AchievementWrite } from '../../api/achievement-api';
import { indicatorApi, type IndicatorDefinition, type TimeNode, type UnitAllocation } from '../../api/indicator-api';
import { topicApi, type ApiTopic } from '../../api/topic-api';
import { systemApi, type ApiUnit } from '../../api/system-api';
import { fileApi } from '../../api/file-api';
import { useSessionStore } from '../../store/session';
import { AchievementForm } from '../../components/achievement/AchievementForm';
import { formalMaterialRequirements, supplementMaterialRequirements } from '../../domain/achievement-materials';
import type { Achievement, AchievementType } from '../../types';

const { Text } = Typography;
const typeNames: Record<ApiAchievement['achievementType'], AchievementType> = { PAPER: '学术论文', PATENT: '发明专利', COPYRIGHT: '软件著作权', STANDARD: '标准规范', TALENT: '人才培养' };
const statusNames: Record<string, string> = {
  DRAFT: '预审草稿', PRE_INITIAL: '预审初审中', PRE_FINAL: '预审终审中', PRE_RETURNED: '预审退回', PRE_APPROVED: '允许投稿/申请',
  EXTERNAL_SUBMITTED: '已投稿/已申请', FORMAL_DRAFT: '正式成果草稿', FORMAL_INITIAL: '正式初审中', FORMAL_FINAL: '正式终审中', FORMAL_RETURNED: '正式退回',
  WAIT_PUBLICATION: '待见刊补充', WAIT_GRANT: '待授权补充', SUPPLEMENT_INITIAL: '补充初审中', SUPPLEMENT_FINAL: '补充终审中', SUPPLEMENT_RETURNED: '补充退回', EFFECTIVE: '已生效',
};
const statusColor = (status: string) => status === 'EFFECTIVE' ? 'green' : status.includes('RETURNED') ? 'red' : status.includes('INITIAL') || status.includes('FINAL') ? 'processing' : 'default';
const editableStatuses = new Set(['DRAFT', 'PRE_RETURNED', 'FORMAL_DRAFT', 'FORMAL_RETURNED', 'WAIT_PUBLICATION', 'WAIT_GRANT', 'SUPPLEMENT_RETURNED']);
const detailKeys = ['abstract', 'keywords', 'researchDirection', 'remarks', 'paperStatus', 'paperFormType', 'paperType', 'journalName', 'cnNumber', 'issn', 'doi', 'firstAuthor', 'correspondingAuthor', 'allAuthors', 'signingUnitList', 'firstSigningUnit', 'submissionDate', 'externalSubmissionNumber', 'acceptanceDate', 'publicationDate', 'projectLabeling', 'englishTitle', 'journalLevel', 'intendedJournal', 'isChineseCoreJournal', 'isPowerGridFirstAuthor', 'patentStatus', 'patentScope', 'applicantList', 'firstApplicant', 'inventorList', 'applicationNumber', 'receiptNumber', 'applicationDate', 'publicationNumber', 'receiptDate', 'grantDate', 'grantPublicationNumber', 'legalStatus', 'technicalField', 'applicationCountry', 'ownershipDescription', 'isPowerGridFirstApplicant', 'shortName', 'version', 'copyrightOwnerList', 'firstCopyrightOwner', 'developers', 'firstCompleter', 'softwareMainFunctions', 'completionDate', 'registrationApplicationDate', 'registrationNumber', 'certificateDate', 'firstPublicationDate', 'developmentMode', 'rightsScope', 'softwareCategory', 'operatingPlatform', 'developmentLanguage', 'technicalFeatures', 'isPowerGridFirstCompleter', 'standardLevel', 'leadingUnit', 'participatingUnits', 'drafters', 'responsibleOrganization', 'currentStage', 'draftSubmissionDate', 'draftCommitDate', 'studentName', 'educationLevel', 'trainingUnit', 'supervisorName', 'thesisTitle', 'enrollmentDate', 'expectedGraduationDate', 'actualGraduationDate', 'trainingStatus'];

interface FormValues extends Record<string, unknown> {
  unitIndicatorAllocationId: string; topicId: string; unitId: string; nodeId: string;
  achievementType: ApiAchievement['achievementType']; title: string; responsiblePerson: string;
}

export function AchievementEntryPage() {
  const user = useSessionStore((state) => state.user)!;
  const [form] = Form.useForm<FormValues>();
  const allocationId = Form.useWatch('unitIndicatorAllocationId', form);
  const [topics, setTopics] = useState<ApiTopic[]>([]);
  const [units, setUnits] = useState<ApiUnit[]>([]);
  const [nodes, setNodes] = useState<TimeNode[]>([]);
  const [definitions, setDefinitions] = useState<IndicatorDefinition[]>([]);
  const [allocations, setAllocations] = useState<UnitAllocation[]>([]);
  const [rows, setRows] = useState<ApiAchievement[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState({ topicId: '', nodeId: '', status: '', indicatorDefinitionId: '', unitId: '', keyword: '', pendingForMe: false });
  const [filterExpanded, setFilterExpanded] = useState(false);
  const [editing, setEditing] = useState<ApiAchievement>();
  const [formOpen, setFormOpen] = useState(false);
  const [detail, setDetail] = useState<ApiAchievement>();
  const [external, setExternal] = useState<ApiAchievement>();
  const [externalForm] = Form.useForm<{ externalSubmissionDate: string; externalSubmissionNumber: string }>();
  const [decision, setDecision] = useState<'APPROVE' | 'RETURN'>();
  const [opinion, setOpinion] = useState('');
  const [pendingFiles, setPendingFiles] = useState<Record<string, File>>({});
  const [progress, setProgress] = useState<AchievementProgress>();
  const [progressPagination, setProgressPagination] = useState({ current: 1, pageSize: 5 });
  const [entryPagination, setEntryPagination] = useState({ current: 1, pageSize: 5 });
  const canSubmit = ['INTERNAL_TOPIC_UNIT', 'EXTERNAL_TOPIC_UNIT'].includes(user.roleCode) && user.actionPermissions.includes('achievement.submit');
  const canInitial = user.roleCode === 'RESEARCH_ASSISTANT' && user.actionPermissions.includes('achievement.initial.approve');
  const canFinal = user.roleCode === 'PROJECT_TECH_LEADER' && user.actionPermissions.includes('achievement.final.approve');

  const loadBase = useCallback(async () => {
    const [topicPage, unitRows, nodeRows, definitionRows] = await Promise.all([topicApi.list(), systemApi.units(), indicatorApi.nodes(true), indicatorApi.definitions()]);
    setTopics(topicPage.items); setUnits(unitRows); setNodes(nodeRows); setDefinitions(definitionRows.filter((item) => item.enabled));
    if (!filters.nodeId) setFilters((current) => ({ ...current, nodeId: [...nodeRows].filter((node) => node.enabled).sort((a, b) => b.sortOrder - a.sortOrder)[0]?.id ?? '' }));
    if (canSubmit && user.unitId) {
      const calls = topicPage.items.flatMap((topic) => nodeRows.filter((node) => node.enabled).map((node) => indicatorApi.allocations(topic.id, node.id)));
      const settled = await Promise.allSettled(calls);
      setAllocations(settled.flatMap((result) => result.status === 'fulfilled' ? result.value.rows : []).filter((item) => item.unitId === user.unitId && item.targetQuantity > 0));
    }
  }, [canSubmit, filters.nodeId, user.unitId]);
  const loadRows = useCallback(async () => {
    setLoading(true);
    try {
      // 成果列表按所选考核节点累计展示，不能让后端按 nodeId 精确过滤掉历史节点成果。
      const page = await achievementApi.list({ topicId: filters.topicId, status: filters.status, indicatorDefinitionId: filters.indicatorDefinitionId, pendingForMe: filters.pendingForMe || undefined });
      setRows(page.items);
      if (filters.nodeId) setProgress(await achievementApi.progress(filters.nodeId, filters.topicId || undefined));
    } catch (error) { message.error(error instanceof Error ? error.message : '成果数据加载失败'); }
    finally { setLoading(false); }
  }, [filters.indicatorDefinitionId, filters.nodeId, filters.pendingForMe, filters.status, filters.topicId]);
  useEffect(() => { void loadBase().catch((error) => message.error(error.message)); }, [loadBase]);
  useEffect(() => { void loadRows(); }, [loadRows]);

  const definitionMap = useMemo(() => Object.fromEntries(definitions.map((item) => [item.id, item])), [definitions]);
  const topicMap = useMemo(() => Object.fromEntries(topics.map((item) => [item.id, item])), [topics]);
  const unitMap = useMemo(() => Object.fromEntries(units.map((item) => [item.id, item.name])), [units]);
  const nodeOrder = useMemo(() => Object.fromEntries(nodes.map((item) => [item.id, item.sortOrder])), [nodes]);
  const selectedNodeOrder = filters.nodeId ? nodeOrder[filters.nodeId] : undefined;
  const selectedAllocation = allocations.find((item) => item.id === allocationId);
  const selectedType = editing?.achievementType ?? (selectedAllocation ? definitions.find((item) => item.id === selectedAllocation.indicatorDefinitionId)?.achievementType as ApiAchievement['achievementType'] : undefined);
  const visibleRows = rows.filter((item) => (!filters.keyword || item.title.toLowerCase().includes(filters.keyword.toLowerCase()))
    && (!filters.unitId || item.unitId === filters.unitId)
    && (selectedNodeOrder === undefined || (nodeOrder[item.nodeId] ?? Number.MAX_SAFE_INTEGER) <= selectedNodeOrder));
  const targetTotal = useMemo(() => {
    if (!progress) return 0;
    // 有课题汇总行时使用课题目标，避免再累加单位行造成重复统计；
    // 普通课题单位仅能看到自己的单位行，因此直接汇总其单位目标。
    const topicRows = progress.rows.filter((row) => row.scope === 'TOPIC');
    const targetRows = topicRows.length > 0 ? topicRows : progress.rows.filter((row) => row.scope === 'UNIT');
    return targetRows.reduce((sum, row) => sum + (row.targetQuantity ?? 0), 0);
  }, [progress]);
  const completionRate = targetTotal > 0 ? Math.round(((progress?.baseStages.effective ?? 0) / targetTotal) * 100) : 0;

  const openCreate = () => {
    setEditing(undefined); setPendingFiles({}); form.resetFields();
    form.setFieldsValue({
      unitId: user.unitId,
      responsiblePerson: user.username,
      projectLabeling: `${import.meta.env.VITE_PROJECT_NAME ?? '国家科技重大专项示范'}（${import.meta.env.VITE_PROJECT_CODE ?? 'GZ-2025-001'}）`,
    });
    setFormOpen(true);
  };
  const openEdit = (row: ApiAchievement) => {
    setEditing(row); setPendingFiles({});
    const allocation = allocations.find((item) => item.topicId === row.topicId && item.unitId === row.unitId
      && item.nodeId === row.nodeId && item.indicatorDefinitionId === row.indicatorDefinitionId);
    form.setFieldsValue({ unitIndicatorAllocationId: allocation?.id, topicId: row.topicId, unitId: row.unitId, nodeId: row.nodeId,
      achievementType: row.achievementType, title: row.title, responsiblePerson: row.responsiblePerson, ...row.detail });
    setFormOpen(true);
  };
  const materialRequirements = useMemo(() => {
    if (!editing || !selectedType) return [];
    const chinese = typeNames[selectedType];
    if (['FORMAL_DRAFT', 'FORMAL_RETURNED'].includes(editing.status)) return formalMaterialRequirements(chinese);
    if (['WAIT_PUBLICATION', 'WAIT_GRANT', 'SUPPLEMENT_RETURNED'].includes(editing.status)) return supplementMaterialRequirements({ achievementType: chinese, paperType: String(editing.detail.paperType ?? '') as Achievement['paperType'], isChineseCoreJournal: Boolean(editing.detail.isChineseCoreJournal) });
    return [];
  }, [editing, selectedType]);

  const save = async () => {
    try {
      const values = await form.validateFields();
      const allocation = editing ? { topicId: editing.topicId, nodeId: editing.nodeId, indicatorDefinitionId: editing.indicatorDefinitionId } : selectedAllocation;
      if (!allocation) return message.warning('请选择已下发的成果指标');
      const uploaded = await Promise.all(Object.entries(pendingFiles).map(async ([materialType, file]) => ({ materialType, file: await fileApi.upload(file, 'ACHIEVEMENT') })));
      const materialAttachments = editing ? [
        ...editing.materialLinks.filter((item) => item.active && !pendingFiles[item.materialType]).map((item) => ({ fileId: item.fileId, materialType: item.materialType })),
        ...uploaded.map((item) => ({ fileId: item.file.id, materialType: item.materialType })),
      ] : uploaded.map((item) => ({ fileId: item.file.id, materialType: item.materialType }));
      const detailValues = Object.fromEntries(detailKeys.filter((key) => values[key] !== undefined && values[key] !== '').map((key) => [key, values[key]]));
      const body: AchievementWrite = { topicId: allocation.topicId, nodeId: allocation.nodeId, indicatorDefinitionId: allocation.indicatorDefinitionId, title: values.title, responsiblePerson: values.responsiblePerson, detail: detailValues, recordVersion: editing?.recordVersion, materialAttachments };
      if (editing) await achievementApi.update(editing.id, body); else await achievementApi.create(body);
      message.success(editing ? '成果已保存' : '成果已创建'); setFormOpen(false); await loadRows();
    } catch (error) { message.error(error instanceof Error ? error.message : '保存失败'); }
  };
  const act = async (row: ApiAchievement, action: string, extra: { externalSubmissionDate?: string; externalSubmissionNumber?: string } = {}) => {
    try { await achievementApi.action(row.id, { action, recordVersion: row.recordVersion, ...extra }); message.success('流程状态已更新'); setExternal(undefined); await loadRows(); }
    catch (error) { message.error(error instanceof Error ? error.message : '操作失败'); }
  };
  const reviewable = (row: ApiAchievement) => canInitial && row.status.endsWith('_INITIAL') || canFinal && row.status.endsWith('_FINAL');
  const review = async () => {
    if (!detail || !decision) return;
    if (decision === 'RETURN' && !opinion.trim()) return message.warning('退回时必须填写意见');
    try { await achievementApi.review(detail.id, { decision, opinion: opinion || '同意', submittedVersion: detail.submittedVersion, recordVersion: detail.recordVersion }); message.success(decision === 'APPROVE' ? '审批已通过' : '已退回修改'); setDecision(undefined); setDetail(undefined); setOpinion(''); await loadRows(); }
    catch (error) { message.error(error instanceof Error ? error.message : '审批失败'); }
  };

  const actionButtons = (row: ApiAchievement) => {
    const owner = canSubmit && row.unitId === user.unitId;
    return <Space wrap><Button type="link" icon={<EyeOutlined />} onClick={() => setDetail(row)}>{reviewable(row) ? '审批' : '查看'}</Button>
      {owner && editableStatuses.has(row.status) && <Button type="link" icon={<EditOutlined />} onClick={() => openEdit(row)}>编辑</Button>}
      {owner && ['DRAFT', 'PRE_RETURNED'].includes(row.status) && <Button type="link" icon={<SendOutlined />} onClick={() => void act(row, 'SUBMIT_PRE_REVIEW')}>提交预审</Button>}
      {owner && row.status === 'PRE_APPROVED' && <Button type="link" onClick={() => { setExternal(row); externalForm.resetFields(); }}>登记投稿/申请</Button>}
      {owner && row.status === 'EXTERNAL_SUBMITTED' && <Button type="link" onClick={() => void act(row, 'START_FORMAL')}>补充正式成果</Button>}
      {owner && ['FORMAL_DRAFT', 'FORMAL_RETURNED'].includes(row.status) && <Button type="link" icon={<SendOutlined />} onClick={() => void act(row, 'SUBMIT_FORMAL')}>提交正式审批</Button>}
      {owner && ['WAIT_PUBLICATION', 'WAIT_GRANT', 'SUPPLEMENT_RETURNED'].includes(row.status) && <Button type="link" icon={<SendOutlined />} onClick={() => void act(row, 'SUBMIT_SUPPLEMENT')}>提交补充审批</Button>}
    </Space>;
  };

  return <>
    <Card className="achievement-filter-card" style={{ marginBottom: 16 }}>
      <div className={`achievement-filter-grid${filterExpanded ? ' is-expanded' : ''}`}>
        {(canInitial || canFinal) && <Space className="achievement-filter-field" size={8}><Text>处理范围</Text><Select value={filters.pendingForMe} onChange={(value) => setFilters({ ...filters, pendingForMe: value })} options={[{ value: true, label: '待我处理' }, { value: false, label: '全部成果' }]} /></Space>}
        <Space className="achievement-filter-field" size={8}><Text>考核节点</Text><Select value={filters.nodeId || undefined} onChange={(value) => setFilters({ ...filters, nodeId: value })} options={nodes.filter((item) => item.enabled).map((item) => ({ value: item.id, label: item.name }))} /></Space>
        <Space className="achievement-filter-field" size={8}><Text>所属课题</Text><Select allowClear placeholder="全部相关课题" value={filters.topicId || undefined} onChange={(value) => setFilters({ ...filters, topicId: value ?? '' })} options={topics.map((item) => ({ value: item.id, label: `${item.code} ${item.name}` }))} /></Space>
        <Space className="achievement-filter-field" size={8}><Text>成果状态</Text><Select allowClear placeholder="全部状态" value={filters.status || undefined} onChange={(value) => setFilters({ ...filters, status: value ?? '' })} options={Object.entries(statusNames).map(([value, label]) => ({ value, label }))} /></Space>
        {filterExpanded && <>
          <Space className="achievement-filter-field" size={8}><Text>成果指标</Text><Select allowClear placeholder="全部指标" value={filters.indicatorDefinitionId || undefined} onChange={(value) => setFilters({ ...filters, indicatorDefinitionId: value ?? '' })} options={definitions.map((item) => ({ value: item.id, label: item.name }))} /></Space>
          <Space className="achievement-filter-field" size={8}><Text>提交单位</Text><Select allowClear placeholder="全部单位" value={filters.unitId || undefined} onChange={(value) => setFilters({ ...filters, unitId: value ?? '' })} options={units.map((item) => ({ value: item.id, label: item.name }))} /></Space>
          <Space className="achievement-filter-field" size={8}><Text>成果名称</Text><Input allowClear placeholder="请输入成果名称" value={filters.keyword} onChange={(event) => setFilters({ ...filters, keyword: event.target.value })} /></Space>
        </>}
        <Space className="achievement-filter-actions" size={10}>
          <Button type="primary" icon={<SearchOutlined />} onClick={() => void loadRows()}>查询</Button>
          <Button onClick={() => setFilters((current) => ({ ...current, topicId: '', status: '', indicatorDefinitionId: '', unitId: '', keyword: '', pendingForMe: canInitial || canFinal }))}>重置</Button>
          <Button type="link" icon={filterExpanded ? <UpOutlined /> : <DownOutlined />} onClick={() => setFilterExpanded((value) => !value)}>{filterExpanded ? '收起' : '展开'}</Button>
        </Space>
      </div>
    </Card>
    {progress && <Card title="成果进度" style={{ marginBottom: 16 }}><Row gutter={[12, 12]}>
      {[
        ['分配指标', targetTotal], ['已发起', progress.baseStages.initiated], ['预审通过', progress.baseStages.preApproved],
        ['已投稿/申请', progress.baseStages.external], ['正式成果', progress.baseStages.formal], ['进入补充阶段', progress.baseStages.supplement], ['已生效', progress.baseStages.effective],
      ].map(([label, value]) => <Col flex="1 1 125px" key={String(label)}><Statistic title={label} value={value} /></Col>)}
      <Col flex="1 1 220px"><Text type="secondary">完成率</Text><Progress percent={Math.min(completionRate, 100)} status={completionRate >= 100 ? 'success' : 'active'} format={() => `${completionRate}%`} /></Col>
    </Row>
      <Table size="small" rowKey={(row) => `${row.scope}-${row.topicId ?? ''}-${row.unitId ?? ''}-${row.indicatorDefinitionId ?? ''}`} style={{ marginTop: 16 }} dataSource={progress.rows} pagination={{
        current: progressPagination.current,
        pageSize: progressPagination.pageSize,
        showSizeChanger: true,
        pageSizeOptions: [5, 10, 20],
        showTotal: (total) => `共 ${total} 条`,
        onChange: (page, pageSize) => setProgressPagination((current) => ({ current: pageSize !== current.pageSize ? 1 : page, pageSize })),
      }} scroll={{ x: 1100 }} columns={[
        { title: '课题', dataIndex: 'topicId', width: 190, render: (value: string) => topicMap[value]?.name ?? '全部课题' },
        { title: '单位', dataIndex: 'unitId', width: 160, render: (value: string) => unitMap[value] ?? '全部单位' },
        { title: '成果指标', dataIndex: 'indicatorDefinitionId', width: 160, render: (value: string) => definitionMap[value]?.name ?? '综合统计' },
        { title: '分配指标', dataIndex: 'targetQuantity', width: 90 },
        { title: '已发起', width: 80, render: (_: unknown, row) => row.stages.initiated },
        { title: '预审通过', width: 90, render: (_: unknown, row) => row.stages.preApproved },
        { title: '正式成果', width: 90, render: (_: unknown, row) => row.stages.formal },
        { title: '已生效', width: 80, render: (_: unknown, row) => row.stages.effective },
        { title: '完成率', dataIndex: 'completionRate', width: 130, render: (value: number) => <Progress size="small" percent={Math.min(Math.round(value ?? 0), 100)} /> },
      ]} />
    </Card>}
    <Card><Table loading={loading} rowKey="id" dataSource={visibleRows} pagination={{
      current: entryPagination.current,
      pageSize: entryPagination.pageSize,
      showSizeChanger: true,
      pageSizeOptions: [5, 10, 20],
      showTotal: (total) => `共 ${total} 条`,
      onChange: (page, pageSize) => setEntryPagination((current) => ({ current: pageSize !== current.pageSize ? 1 : page, pageSize })),
    }} scroll={{ x: 1150 }} columns={[
      { title: '成果名称', dataIndex: 'title', width: 260, fixed: 'left', render: (value, row) => <Space direction="vertical" size={0}><Text strong>{value}</Text><Text type="secondary">{definitionMap[row.indicatorDefinitionId]?.name ?? typeNames[row.achievementType]}</Text></Space> },
      { title: '课题', width: 180, render: (_, row) => topicMap[row.topicId]?.name ?? row.topicId },
      { title: '提交单位', width: 170, render: (_, row) => unitMap[row.unitId] ?? row.unitId },
      { title: '成果类型', width: 110, render: (_, row) => <Tag color="blue">{typeNames[row.achievementType]}</Tag> },
      { title: '负责人', dataIndex: 'responsiblePerson', width: 110 },
      { title: '状态', width: 130, render: (_, row) => <Tag color={statusColor(row.status)}>{statusNames[row.status] ?? row.status}</Tag> },
      { title: '版本', width: 110, render: (_, row) => `记录 V${row.recordVersion} / 提交 V${row.submittedVersion}` },
      { title: '操作', width: 310, fixed: 'right', render: (_, row) => actionButtons(row) },
    ]} title={() => <div style={{ display: 'flex', justifyContent: 'flex-end' }}><Space><Button icon={<ReloadOutlined />} onClick={() => void loadRows()}>刷新</Button>{canSubmit && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增成果</Button>}</Space></div>} /></Card>

    <Drawer width={980} title={editing ? '编辑成果' : '新建成果'} open={formOpen} onClose={() => setFormOpen(false)} destroyOnHidden
      extra={<Space><Button onClick={() => setFormOpen(false)}>取消</Button><Button type="primary" onClick={() => void save()}>保存草稿</Button></Space>}>
      <Form form={form} layout="vertical">
      <AchievementForm form={form} topics={topics} units={units} lockOwnership={Boolean(editing)} definitions={definitions}
        project={{ name: import.meta.env.VITE_PROJECT_NAME ?? '国家科技重大专项示范', code: import.meta.env.VITE_PROJECT_CODE ?? 'GZ-2025-001' }}
        allocations={allocations} nodes={nodes} currentUnitId={editing?.unitId ?? user.unitId} />
      {materialRequirements.length > 0 && <Card size="small" title="本阶段材料"><Space direction="vertical" style={{ width: '100%' }}>{materialRequirements.map((requirement) => <Row key={requirement.materialType} align="middle" gutter={12}><Col span={7}><b>{requirement.materialType}</b></Col><Col span={9}><Text type="secondary">{requirement.description}</Text></Col><Col span={8}><label className="ant-btn"><UploadOutlined /> {pendingFiles[requirement.materialType]?.name ?? (editing?.materialLinks.some((item) => item.active && item.materialType === requirement.materialType) ? '已上传（点击替换）' : '选择文件')}<input hidden type="file" onChange={(event) => { const file = event.target.files?.[0]; if (file) setPendingFiles({ ...pendingFiles, [requirement.materialType]: file }); }} /></label></Col></Row>)}</Space></Card>}
      </Form>
    </Drawer>

    <Drawer width={900} title="成果详情" open={Boolean(detail)} onClose={() => setDetail(undefined)} extra={detail && reviewable(detail) && <Space><Button danger icon={<RollbackOutlined />} onClick={() => setDecision('RETURN')}>退回</Button><Button type="primary" icon={<CheckOutlined />} onClick={() => setDecision('APPROVE')}>通过</Button></Space>}>
      {detail && <Space direction="vertical" style={{ width: '100%' }} size={14}><Descriptions bordered column={2} items={[
        { key: 'title', label: '成果名称', children: detail.title, span: 2 }, { key: 'topic', label: '课题', children: topicMap[detail.topicId]?.name ?? detail.topicId }, { key: 'unit', label: '提交单位', children: unitMap[detail.unitId] ?? detail.unitId },
        { key: 'type', label: '成果类型', children: typeNames[detail.achievementType] }, { key: 'status', label: '状态', children: statusNames[detail.status] ?? detail.status }, { key: 'owner', label: '负责人', children: detail.responsiblePerson }, { key: 'version', label: '版本', children: `记录 V${detail.recordVersion} / 提交 V${detail.submittedVersion}` },
      ]} /><Card size="small" title="详细信息"><Descriptions bordered size="small" column={2} items={Object.entries(detail.detail).map(([key, value]) => ({ key, label: key, children: typeof value === 'boolean' ? value ? '是' : '否' : String(value) }))} /></Card>
        <Card size="small" title="附件"><Table pagination={false} rowKey="id" dataSource={detail.materialLinks.filter((item) => item.active)} columns={[{ title: '材料类型', dataIndex: 'materialType' }, { title: '文件', render: (_, link) => detail.materials.find((file) => file.id === link.fileId)?.originalName ?? link.fileId }, { title: '状态', dataIndex: 'status' }, { title: '操作', render: (_, link) => { const file = detail.materials.find((item) => item.id === link.fileId); return file && <Space><Button type="link" onClick={() => void fileApi.download(file, true)}>查看</Button><Button type="link" onClick={() => void fileApi.download(file)}>下载</Button></Space>; } }]} /></Card>
        <Card size="small" title="审批记录"><Table pagination={false} rowKey="id" dataSource={detail.approvals} columns={[{ title: '阶段', dataIndex: 'stage' }, { title: '级别', dataIndex: 'level' }, { title: '结论', render: (_, row) => row.decision === 'APPROVE' ? '通过' : '退回' }, { title: '意见', dataIndex: 'opinion' }, { title: '时间', dataIndex: 'operatedAt' }]} /></Card>
      </Space>}
    </Drawer>
    <Modal title={decision === 'APPROVE' ? '确认审批通过' : '退回修改'} open={Boolean(decision)} onCancel={() => setDecision(undefined)} onOk={() => void review()}><Input.TextArea rows={4} value={opinion} onChange={(event) => setOpinion(event.target.value)} placeholder={decision === 'RETURN' ? '请填写退回原因' : '审批意见（选填）'} /></Modal>
    <Modal title="登记投稿/申请" open={Boolean(external)} onCancel={() => setExternal(undefined)} onOk={() => void externalForm.validateFields().then((values) => external && act(external, 'REGISTER_EXTERNAL_SUBMISSION', values))}><Form form={externalForm} layout="vertical"><Form.Item name="externalSubmissionDate" label="投稿/申请日期" rules={[{ required: true }]}><Input type="date" /></Form.Item><Form.Item name="externalSubmissionNumber" label="投稿/申请编号"><Input /></Form.Item></Form></Modal>
  </>;
}
