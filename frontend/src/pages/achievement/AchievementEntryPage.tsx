import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Drawer, Form, Input, Modal, Progress, Row, Select, Space, Table, Tabs, Tag, Typography, message } from 'antd';
import { CheckOutlined, DownOutlined, EditOutlined, EyeOutlined, PlusOutlined, ReloadOutlined, RollbackOutlined, SendOutlined, UpOutlined, UploadOutlined } from '@ant-design/icons';
import { achievementApi, type ApiAchievement, type AchievementProgress, type AchievementProgressRow, type AchievementWrite } from '../../api/achievement-api';
import { indicatorApi, type IndicatorDefinition, type TimeNode, type UnitAllocation } from '../../api/indicator-api';
import { isBusinessTopic, topicApi, type ApiTopic } from '../../api/topic-api';
import { systemApi, type ApiUnit } from '../../api/system-api';
import { fileApi } from '../../api/file-api';
import { useSessionStore } from '../../store/session';
import { AchievementForm } from '../../components/achievement/AchievementForm';
import { ApiAchievementDetail } from '../../components/achievement/ApiAchievementDetail';
import { formalMaterialRequirements, supplementMaterialRequirements } from '../../domain/achievement-materials';
import { classifyUnitProgress } from '../../domain/achievement-progress';
import { achievementPhaseLabel, achievementPhaseOptions, matchesAchievementPhase } from '../../domain/achievement-status';
import type { Achievement, AchievementType } from '../../types';

const { Text } = Typography;
const typeNames: Record<ApiAchievement['achievementType'], AchievementType> = { PAPER: '学术论文', PATENT: '发明专利', COPYRIGHT: '软件著作权', STANDARD: '标准规范', TALENT: '人才培养' };
const statusColor = (status: string) => status === 'EFFECTIVE' ? 'green' : status.includes('RETURNED') ? 'red' : status.includes('INITIAL') || status.includes('FINAL') ? 'processing' : 'default';
const editableStatuses = new Set(['DRAFT', 'PRE_RETURNED', 'FORMAL_DRAFT', 'FORMAL_RETURNED', 'WAIT_PUBLICATION', 'WAIT_GRANT', 'WAIT_CERTIFICATE', 'SUPPLEMENT_RETURNED']);
const detailKeys = ['abstract', 'keywords', 'researchDirection', 'remarks', 'paperStatus', 'paperFormType', 'paperType', 'journalName', 'cnNumber', 'issn', 'doi', 'firstAuthor', 'correspondingAuthor', 'allAuthors', 'signingUnitList', 'submissionDate', 'externalSubmissionNumber', 'acceptanceDate', 'publicationDate', 'projectLabeling', 'englishTitle', 'journalLevel', 'isChineseCoreJournal', 'isPowerGridFirstAuthor', 'patentStatus', 'patentScope', 'applicantList', 'firstInventor', 'inventorList', 'applicationNumber', 'applicationDate', 'publicationNumber', 'receiptDate', 'grantDate', 'grantPublicationNumber', 'technicalField', 'ownershipDescription', 'isPowerGridFirstApplicant', 'copyrightStatus', 'softwareFullName', 'version', 'copyrightOwnerList', 'firstCopyrightOwner', 'developers', 'softwareMainFunctions', 'completionDate', 'registrationApplicationDate', 'registrationNumber', 'copyrightPublicationDate', 'firstPublicationDate', 'developmentMode', 'softwareCategory', 'hardwareEnvironment', 'developmentOperatingSystem', 'softwareDevelopmentEnvironment', 'operatingPlatform', 'softwareSupportEnvironment', 'developmentLanguage', 'sourceCodeQuantity', 'developmentPurpose', 'industryField', 'technicalFeatures', 'isPowerGridFirstCopyrightOwner', 'standardLevel', 'leadingUnit', 'participatingUnits', 'drafters', 'responsibleOrganization', 'currentStage', 'draftSubmissionDate', 'draftCommitDate', 'studentName', 'educationLevel', 'trainingUnit', 'supervisorName', 'thesisTitle', 'enrollmentDate', 'expectedGraduationDate', 'actualGraduationDate', 'trainingStatus'];

interface FormValues extends Record<string, unknown> {
  unitIndicatorAllocationId: string; topicId: string; unitId: string; nodeId: string; indicatorDefinitionId: string;
  achievementType: ApiAchievement['achievementType']; title: string; responsiblePerson: string;
}

interface TopicProgressSummary {
  topicId: string;
  target: number;
  stages: AchievementProgressRow['stages'];
  details: Array<AchievementProgressRow & { special: boolean }>;
  units: UnitProgressSummary[];
}

interface UnitProgressSummary {
  key: string;
  topicId: string;
  unitId: string;
  allocated: boolean;
  target: number;
  stages: AchievementProgressRow['stages'];
  details: Array<AchievementProgressRow & { special: boolean }>;
}

const emptyStages = (): AchievementProgressRow['stages'] => ({ initiated: 0, submitted: 0, preApproved: 0, external: 0, formal: 0, supplement: 0, effective: 0 });
const addStages = (target: AchievementProgressRow['stages'], source: AchievementProgressRow['stages']) => {
  target.initiated += source.initiated; target.submitted += source.submitted; target.preApproved += source.preApproved; target.external += source.external;
  target.formal += source.formal; target.supplement += source.supplement; target.effective += source.effective;
};
const groupIndicatorRows = (base: AchievementProgressRow[], special: AchievementProgressRow[]) => base.flatMap((baseRow) => [
  { ...baseRow, special: false },
  ...special.filter((row) => row.achievementType === baseRow.achievementType).map((row) => ({ ...row, special: true })),
]);

export function AchievementEntryPage() {
  const user = useSessionStore((state) => state.user)!;
  const [form] = Form.useForm<FormValues>();
  const allocationId = Form.useWatch('unitIndicatorAllocationId', form);
  const [topics, setTopics] = useState<ApiTopic[]>([]);
  const [progressTopics, setProgressTopics] = useState<ApiTopic[]>([]);
  const [units, setUnits] = useState<ApiUnit[]>([]);
  const [nodes, setNodes] = useState<TimeNode[]>([]);
  const [definitions, setDefinitions] = useState<IndicatorDefinition[]>([]);
  const [allocations, setAllocations] = useState<UnitAllocation[]>([]);
  const [rows, setRows] = useState<ApiAchievement[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState({ topicId: '', status: '', indicatorDefinitionId: '', unitId: '', keyword: '', pendingForMe: false });
  const [progressFilters, setProgressFilters] = useState({ topicId: '', nodeId: '' });
  const [filterExpanded, setFilterExpanded] = useState(false);
  const [editing, setEditing] = useState<ApiAchievement>();
  const [formOpen, setFormOpen] = useState(false);
  const [detail, setDetail] = useState<ApiAchievement>();
  const [decision, setDecision] = useState<'APPROVE' | 'RETURN'>();
  const [opinion, setOpinion] = useState('');
  const [pendingFiles, setPendingFiles] = useState<Record<string, File>>({});
  const [progress, setProgress] = useState<AchievementProgress>();
  const [progressLoading, setProgressLoading] = useState(false);
  const [entryPagination, setEntryPagination] = useState({ current: 1, pageSize: 5 });
  const canSubmit = ['INTERNAL_TOPIC_UNIT', 'EXTERNAL_TOPIC_UNIT'].includes(user.roleCode) && user.actionPermissions.includes('achievement.submit');
  const canInitial = user.roleCode === 'RESEARCH_ASSISTANT' && user.actionPermissions.includes('achievement.initial.approve');
  const canFinal = user.roleCode === 'PROJECT_TECH_LEADER' && user.actionPermissions.includes('achievement.final.approve');
  const canViewMultipleUnits = ['SYSTEM_ADMIN', 'RESEARCH_ASSISTANT', 'PROJECT_TECH_LEADER'].includes(user.roleCode)
    || topics.some((topic) => topic.members.some((membership) => membership.enabled
      && membership.membershipType === 'LEAD' && membership.userIds?.includes(user.id)));

  const loadBase = useCallback(async () => {
    const [topicPage, unitRows, nodeRows, definitionRows] = await Promise.all([
      topicApi.list(), systemApi.units(), indicatorApi.nodes(user.roleCode === 'RESEARCH_ASSISTANT'), indicatorApi.definitions(),
    ]);
    const businessTopics = topicPage.items.filter(isBusinessTopic);
    setTopics(businessTopics); setProgressTopics(topicPage.items.filter((item) => item.status !== 'DRAFT'));
    setUnits(unitRows); setNodes(nodeRows); setDefinitions(definitionRows.filter((item) => item.enabled));
    if (!progressFilters.nodeId) setProgressFilters((current) => ({ ...current, nodeId: [...nodeRows].filter((node) => node.enabled).sort((a, b) => b.sortOrder - a.sortOrder)[0]?.id ?? '' }));
    if (canSubmit && user.unitId) {
      const calls = businessTopics.flatMap((topic) => nodeRows.filter((node) => node.enabled).map((node) => indicatorApi.allocations(topic.id, node.id)));
      const settled = await Promise.allSettled(calls);
      setAllocations(settled.flatMap((result) => result.status === 'fulfilled' ? result.value.rows : []).filter((item) => item.unitId === user.unitId));
    }
  }, [canSubmit, progressFilters.nodeId, user.roleCode, user.unitId]);
  const loadRows = useCallback(async () => {
    setLoading(true);
    try {
      const page = await achievementApi.list({ topicId: filters.topicId, indicatorDefinitionId: filters.indicatorDefinitionId, pendingForMe: filters.pendingForMe || undefined });
      setRows(page.items);
    } catch (error) { message.error(error instanceof Error ? error.message : '成果数据加载失败'); }
    finally { setLoading(false); }
  }, [filters.indicatorDefinitionId, filters.pendingForMe, filters.topicId]);
  const loadProgress = useCallback(async () => {
    if (!progressFilters.nodeId) return;
    setProgressLoading(true);
    try { setProgress(await achievementApi.progress(progressFilters.nodeId, progressFilters.topicId || undefined)); }
    catch (error) { message.error(error instanceof Error ? error.message : '成果进度加载失败'); }
    finally { setProgressLoading(false); }
  }, [progressFilters.nodeId, progressFilters.topicId]);
  useEffect(() => { void loadBase().catch((error) => message.error(error.message)); }, [loadBase]);
  useEffect(() => { void loadRows(); }, [loadRows]);
  useEffect(() => { void loadProgress(); }, [loadProgress]);

  const definitionMap = useMemo(() => Object.fromEntries(definitions.map((item) => [item.id, item])), [definitions]);
  const topicMap = useMemo(() => Object.fromEntries(progressTopics.map((item) => [item.id, item])), [progressTopics]);
  const unitMap = useMemo(() => Object.fromEntries(units.map((item) => [item.id, item.name])), [units]);
  const visibleUnitOptions = units.filter((unit) => rows.some((row) => row.unitId === unit.id));
  const selectedDefinitionId = String(allocationId ?? '').split(':')[1];
  const selectedType = editing?.achievementType ?? definitions.find((item) => item.id === selectedDefinitionId)?.achievementType as ApiAchievement['achievementType'] | undefined;
  const workflowStage: 'PRE' | 'FORMAL' | 'SUPPLEMENT' = editing && ['FORMAL_DRAFT', 'FORMAL_RETURNED'].includes(editing.status) ? 'FORMAL'
    : editing && ['WAIT_PUBLICATION', 'WAIT_GRANT', 'WAIT_CERTIFICATE', 'SUPPLEMENT_RETURNED'].includes(editing.status) ? 'SUPPLEMENT' : 'PRE';
  const visibleRows = rows.filter((item) => matchesAchievementPhase(item.status, filters.status)
    && (!filters.keyword || item.title.toLowerCase().includes(filters.keyword.toLowerCase()))
    && (!filters.unitId || item.unitId === filters.unitId));
  const topicProgress = useMemo<TopicProgressSummary[]>(() => {
    if (!progress) return [];
    const topicIds = [...new Set(progress.rows.map((row) => row.topicId))];
    return topicIds.map((topicId) => {
      const base = progress.rows.filter((row) => row.topicId === topicId);
      const baseTopicRows = base.filter((row) => row.scope === 'TOPIC');
      const selectedBase = baseTopicRows.length ? baseTopicRows : base.filter((row) => row.scope === 'UNIT');
      const special = progress.specialIndicators.filter((row) => row.topicId === topicId);
      const specialTopicRows = special.filter((row) => row.scope === 'TOPIC');
      const selectedSpecial = specialTopicRows.length ? specialTopicRows : special.filter((row) => row.scope === 'UNIT');
      const stages = emptyStages(); selectedBase.forEach((row) => addStages(stages, row.stages));
      const unitIds = [...new Set(base.filter((row) => row.scope === 'UNIT' && !row.historical && row.unitId).map((row) => row.unitId!))];
      const unitRows = unitIds.map((unitId) => {
        const unitBase = base.filter((row) => row.scope === 'UNIT' && row.unitId === unitId && !row.historical);
        const unitSpecial = special.filter((row) => row.scope === 'UNIT' && row.unitId === unitId && !row.historical);
        const unitStages = emptyStages(); unitBase.forEach((row) => addStages(unitStages, row.stages));
        return {
          key: `${topicId}-${unitId}`,
          topicId,
          unitId,
          allocated: unitBase.some((row) => row.targetPublished),
          target: unitBase.reduce((sum, row) => sum + (row.targetQuantity ?? 0), 0),
          stages: unitStages,
          details: groupIndicatorRows(unitBase, unitSpecial),
        };
      });
      return {
        topicId,
        target: selectedBase.reduce((sum, row) => sum + (row.targetQuantity ?? 0), 0),
        stages,
        details: groupIndicatorRows(selectedBase, selectedSpecial),
        units: unitRows,
      };
    });
  }, [progress]);
  const renderIndicatorProgress = (details: TopicProgressSummary['details']) => <Table size="small"
    rowKey={(row) => `${row.scope}-${row.unitId ?? ''}-${row.indicatorDefinitionId}-${row.special}`}
    dataSource={details} pagination={false} columns={[
      { title: '成果指标', dataIndex: 'indicatorDefinitionId', render: (value: string, row) => <span className={row.special ? 'achievement-special-indicator' : ''}>{row.special ? '其中：' : ''}{definitionMap[value]?.name ?? '未知指标'}</span> },
      { title: '累计目标', dataIndex: 'targetQuantity', width: 100, render: (value?: number) => value ?? 0 },
      { title: '已提交', width: 90, render: (_: unknown, row) => row.stages.submitted },
      { title: '已完成', width: 90, render: (_: unknown, row) => row.stages.effective },
      { title: '尚缺完成', width: 110, render: (_: unknown, row) => { const missing = Math.max((row.targetQuantity ?? 0) - row.stages.effective, 0); return missing > 0 ? <Tag color="orange">{missing} 项</Tag> : <Tag color="green">已完成</Tag>; } },
      { title: '完成率', width: 160, render: (_: unknown, row) => { const target = row.targetQuantity ?? 0; const rate = target > 0 ? Math.round(row.stages.effective * 100 / target) : 0; return <Progress size="small" percent={Math.min(rate, 100)} status={rate >= 100 ? 'success' : 'active'} />; } },
    ]} />;

  const renderUnitProgress = (summary: TopicProgressSummary) => <div className="achievement-unit-progress">
    <Alert type="info" showIcon message="按单位查看成果提交情况；展开单位可查看各项指标明细。" />
    <Table<UnitProgressSummary> size="small" rowKey="key" dataSource={summary.units} pagination={false}
      locale={{ emptyText: '当前权限范围内暂无单位分配数据' }}
      expandable={{ expandRowByClick: true, expandedRowRender: (unit) => <>{!unit.allocated && <Alert type="warning" showIcon style={{ marginBottom: 10 }} message="该单位尚未下发指标，以下目标按 0 展示，已提交成果仍正常统计。" />}{renderIndicatorProgress(unit.details)}</> }}
      columns={[
        { title: '单位', dataIndex: 'unitId', render: (value: string) => <Text strong>{unitMap[value] ?? value}</Text> },
        { title: '提交状态', width: 120, render: (_: unknown, unit) => {
          const state = classifyUnitProgress(unit.allocated, unit.target, unit.stages.submitted);
          if (state === 'UNALLOCATED') return <Tag>指标未分配</Tag>;
          if (state === 'NOT_REQUIRED') return <Tag color="blue">无需提交</Tag>;
          if (state === 'NOT_SUBMITTED') return <Tag color="red">尚未提交</Tag>;
          if (state === 'PARTIAL') return <Tag color="orange">部分提交</Tag>;
          return <Tag color="green">全部提交</Tag>;
        } },
        { title: '尚缺指标', width: 280, render: (_: unknown, unit) => {
          if (!unit.allocated) return <Text type="warning">等待牵头单位分配指标</Text>;
          const missing = unit.details.filter((item) => !item.special && (item.targetQuantity ?? 0) > item.stages.submitted)
            .map((item) => `${definitionMap[item.indicatorDefinitionId]?.name ?? '未知指标'} ${Math.max((item.targetQuantity ?? 0) - item.stages.submitted, 0)}项`);
          return missing.length ? <Text type="warning">{missing.join('、')}</Text> : <Text type="success">已全部提交</Text>;
        } },
      ]} />
  </div>;

  const openCreate = () => {
    setEditing(undefined); setPendingFiles({}); form.resetFields();
    form.setFieldsValue({
      unitId: user.unitId,
      responsiblePerson: user.principalName || user.username,
      projectLabeling: `${import.meta.env.VITE_PROJECT_NAME ?? '国家科技重大专项示范'}（${import.meta.env.VITE_PROJECT_CODE ?? 'GZ-2025-001'}）`,
    });
    setFormOpen(true);
  };
  const openEdit = (row: ApiAchievement) => {
    setEditing(row); setPendingFiles({});
    form.setFieldsValue({ unitIndicatorAllocationId: `${row.nodeId}:${row.indicatorDefinitionId}`, indicatorDefinitionId: row.indicatorDefinitionId, topicId: row.topicId, unitId: row.unitId, nodeId: row.nodeId,
      achievementType: row.achievementType, title: row.title, responsiblePerson: row.responsiblePerson, ...row.detail });
    setFormOpen(true);
  };
  const materialRequirements = useMemo(() => {
    if (!editing || !selectedType) return [];
    const chinese = typeNames[selectedType];
    if (['FORMAL_DRAFT', 'FORMAL_RETURNED'].includes(editing.status)) return formalMaterialRequirements(chinese);
    if (['WAIT_PUBLICATION', 'WAIT_GRANT', 'WAIT_CERTIFICATE', 'SUPPLEMENT_RETURNED'].includes(editing.status)) return supplementMaterialRequirements({ achievementType: chinese, paperType: String(editing.detail.paperType ?? '') as Achievement['paperType'], isChineseCoreJournal: Boolean(editing.detail.isChineseCoreJournal) });
    return [];
  }, [editing, selectedType]);

  const save = async () => {
    try {
      const values = await form.validateFields();
      const ownership = editing ? { topicId: editing.topicId, nodeId: editing.nodeId, indicatorDefinitionId: editing.indicatorDefinitionId }
        : { topicId: values.topicId, nodeId: values.nodeId, indicatorDefinitionId: values.indicatorDefinitionId };
      if (!ownership.topicId || !ownership.nodeId || !ownership.indicatorDefinitionId) return message.warning('请选择成果指标与考核节点');
      const uploaded = await Promise.all(Object.entries(pendingFiles).map(async ([materialType, file]) => ({ materialType, file: await fileApi.upload(file, 'ACHIEVEMENT') })));
      const materialAttachments = editing ? [
        ...editing.materialLinks.filter((item) => item.active && !pendingFiles[item.materialType]).map((item) => ({ fileId: item.fileId, materialType: item.materialType })),
        ...uploaded.map((item) => ({ fileId: item.file.id, materialType: item.materialType })),
      ] : uploaded.map((item) => ({ fileId: item.file.id, materialType: item.materialType }));
      const detailValues = Object.fromEntries(detailKeys.filter((key) => values[key] !== undefined && values[key] !== '').map((key) => [key, values[key]]));
      const body: AchievementWrite = { topicId: ownership.topicId, nodeId: ownership.nodeId, indicatorDefinitionId: ownership.indicatorDefinitionId, title: values.title, responsiblePerson: values.responsiblePerson, detail: detailValues, recordVersion: editing?.recordVersion, materialAttachments };
      if (editing) await achievementApi.update(editing.id, body); else await achievementApi.create(body);
      message.success(editing ? '成果已保存' : '成果已创建'); setFormOpen(false); await loadRows();
    } catch (error) { message.error(error instanceof Error ? error.message : '保存失败'); }
  };
  const act = async (row: ApiAchievement, action: string, extra: { externalSubmissionDate?: string; externalSubmissionNumber?: string } = {}) => {
    try { await achievementApi.action(row.id, { action, recordVersion: row.recordVersion, ...extra }); message.success('流程状态已更新'); await loadRows(); }
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
    const longActionStyle = { height: 'auto', maxWidth: 136, paddingInline: 8, whiteSpace: 'normal' as const, lineHeight: 1.35 };
    const editLabel = ['FORMAL_DRAFT', 'FORMAL_RETURNED'].includes(row.status) ? '补充第二轮材料'
      : ['WAIT_PUBLICATION', 'WAIT_GRANT', 'WAIT_CERTIFICATE', 'SUPPLEMENT_RETURNED'].includes(row.status) ? '补充第三轮材料' : '编辑';
    return <Space size={4} style={{ whiteSpace: 'nowrap' }}><Button type="link" icon={<EyeOutlined />} onClick={() => setDetail(row)}>{reviewable(row) ? '审批' : '查看'}</Button>
      {owner && editableStatuses.has(row.status) && <Button type="link" style={longActionStyle} icon={<EditOutlined />} onClick={() => openEdit(row)}>{editLabel}</Button>}
      {owner && ['DRAFT', 'PRE_RETURNED'].includes(row.status) && <Button type="link" style={longActionStyle} icon={<SendOutlined />} onClick={() => void act(row, 'SUBMIT_PRE_REVIEW')}>提交第一轮预审</Button>}
      {owner && ['FORMAL_DRAFT', 'FORMAL_RETURNED'].includes(row.status) && <Button type="link" style={longActionStyle} icon={<SendOutlined />} onClick={() => void act(row, 'SUBMIT_FORMAL')}>提交第二轮正式审批</Button>}
      {owner && ['WAIT_PUBLICATION', 'WAIT_GRANT', 'WAIT_CERTIFICATE', 'SUPPLEMENT_RETURNED'].includes(row.status) && <Button type="link" style={longActionStyle} icon={<SendOutlined />} onClick={() => void act(row, 'SUBMIT_SUPPLEMENT')}>提交第三轮补充审批</Button>}
    </Space>;
  };

  return <>
    <Card className="achievement-progress-card" title="成果进度" style={{ marginBottom: 16 }} extra={<Space wrap>
      <Text>课题</Text><Select allowClear placeholder="全部课题" value={progressFilters.topicId || undefined} style={{ width: 300 }}
        onChange={(value) => setProgressFilters((current) => ({ ...current, topicId: value ?? '' }))}
        options={topics.map((item) => ({ value: item.id, label: `${item.code} ${item.name}` }))} />
      <Text>时间节点</Text><Select value={progressFilters.nodeId || undefined} style={{ width: 150 }}
        onChange={(value) => setProgressFilters((current) => ({ ...current, nodeId: value }))}
        options={nodes.filter((item) => item.enabled).sort((a, b) => a.sortOrder - b.sortOrder).map((item) => ({ value: item.id, label: item.name }))} />
      <Button icon={<ReloadOutlined />} loading={progressLoading} onClick={() => void loadProgress()}>刷新</Button>
    </Space>}>
      <Table<TopicProgressSummary> loading={progressLoading} size="small" rowKey="topicId" dataSource={topicProgress} pagination={false}
        locale={{ emptyText: '当前课题和时间节点暂无已配置的成果指标' }}
        expandable={{ expandRowByClick: true, expandedRowRender: (summary) => <Tabs defaultActiveKey="units" items={[
          { key: 'units', label: `按单位查看（${summary.units.length}）`, children: renderUnitProgress(summary) },
          { key: 'indicators', label: '按指标查看', children: renderIndicatorProgress(summary.details) },
        ]} /> }}
        columns={[
          { title: '课题汇总', dataIndex: 'topicId', render: (value: string) => <Space><Text strong>{topicMap[value]?.name ?? value}</Text>{topicMap[value]?.code && <Tag color="blue">{topicMap[value].code}</Tag>}{topicMap[value] && !topicMap[value].enabled && <Tag>已停用 · 只读</Tag>}</Space> },
          { title: '单位提交情况', width: 230, render: (_: unknown, row) => {
            const complete = row.units.filter((unit) => unit.target > 0 && unit.stages.submitted >= unit.target).length;
            const rate = row.units.length > 0 ? Math.round(complete * 100 / row.units.length) : 0;
            return <Space><Text>{complete}/{row.units.length}</Text><Progress className="achievement-unit-rate" size="small" percent={rate} showInfo={false} status={rate >= 100 ? 'success' : 'active'} /></Space>;
          } },
          { title: '未提交单位', width: 120, render: (_: unknown, row) => {
            const count = row.units.filter((unit) => unit.stages.submitted === 0).length;
            return count > 0 ? <Tag color="red">{count} 个</Tag> : <Tag color="green">全部提交</Tag>;
          } },
        ]} />
    </Card>
    <Card className="achievement-record-card" title="成果记录" extra={<Space><Button icon={<ReloadOutlined />} onClick={() => void loadRows()}>刷新</Button>{canSubmit && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增成果</Button>}</Space>}>
      <div className={`achievement-filter-grid${filterExpanded ? ' is-expanded' : ''}`}>
        {(canInitial || canFinal) && <Space className="achievement-filter-field" size={8}><Text>处理范围</Text><Select value={filters.pendingForMe} onChange={(value) => setFilters({ ...filters, pendingForMe: value })} options={[{ value: true, label: '待我处理' }, { value: false, label: '全部成果' }]} /></Space>}
        <Space className="achievement-filter-field" size={8}><Text>所属课题</Text><Select allowClear placeholder="全部相关课题" value={filters.topicId || undefined} onChange={(value) => setFilters({ ...filters, topicId: value ?? '' })} options={topics.map((item) => ({ value: item.id, label: `${item.code} ${item.name}` }))} /></Space>
        <Space className="achievement-filter-field" size={8}><Text>成果阶段</Text><Select allowClear placeholder="全部阶段" value={filters.status || undefined} onChange={(value) => setFilters({ ...filters, status: value ?? '' })} options={achievementPhaseOptions} /></Space>
        {filterExpanded && <>
          <Space className="achievement-filter-field" size={8}><Text>成果指标</Text><Select allowClear placeholder="全部指标" value={filters.indicatorDefinitionId || undefined} onChange={(value) => setFilters({ ...filters, indicatorDefinitionId: value ?? '' })} options={definitions.map((item) => ({ value: item.id, label: item.name }))} /></Space>
          {canViewMultipleUnits && <Space className="achievement-filter-field" size={8}><Text>提交单位</Text><Select allowClear placeholder="全部单位" value={filters.unitId || undefined} onChange={(value) => setFilters({ ...filters, unitId: value ?? '' })} options={visibleUnitOptions.map((item) => ({ value: item.id, label: item.name }))} /></Space>}
          <Space className="achievement-filter-field" size={8}><Text>成果名称</Text><Input allowClear placeholder="请输入成果名称" value={filters.keyword} onChange={(event) => setFilters({ ...filters, keyword: event.target.value })} /></Space>
        </>}
        <Space className="achievement-filter-actions" size={10}>
          <Button onClick={() => setFilters((current) => ({ ...current, topicId: '', status: '', indicatorDefinitionId: '', unitId: '', keyword: '', pendingForMe: canInitial || canFinal }))}>重置</Button>
          <Button type="link" icon={filterExpanded ? <UpOutlined /> : <DownOutlined />} onClick={() => setFilterExpanded((value) => !value)}>{filterExpanded ? '收起' : '展开'}</Button>
        </Space>
      </div>
      <Table className="achievement-record-table" loading={loading} rowKey="id" dataSource={visibleRows} pagination={{
      current: entryPagination.current,
      pageSize: entryPagination.pageSize,
      showSizeChanger: true,
      pageSizeOptions: [5, 10, 20],
      showTotal: (total) => `共 ${total} 条`,
      onChange: (page, pageSize) => setEntryPagination((current) => ({ current: pageSize !== current.pageSize ? 1 : page, pageSize })),
    }} scroll={{ x: 1320 }} columns={[
      { title: '成果名称', dataIndex: 'title', width: 200, fixed: 'left', render: (value, row) => <Space direction="vertical" size={0}><Text strong>{value}</Text><Text type="secondary">{definitionMap[row.indicatorDefinitionId]?.name ?? typeNames[row.achievementType]}</Text></Space> },
      { title: '课题', width: 180, render: (_, row) => topicMap[row.topicId]?.name ?? row.topicId },
      { title: '提交单位', width: 170, render: (_, row) => unitMap[row.unitId] ?? row.unitId },
      { title: '成果类型', width: 110, render: (_, row) => <Tag color="blue">{typeNames[row.achievementType]}</Tag> },
      { title: '负责人', dataIndex: 'responsiblePerson', width: 110 },
      { title: '当前阶段', width: 160, render: (_, row) => <Tag color={statusColor(row.status)}>{achievementPhaseLabel(row.status)}</Tag> },
      { title: '操作', width: 390, fixed: 'right', render: (_, row) => actionButtons(row) },
    ]} />
    </Card>

    <Drawer width={980} title={editing ? '编辑成果' : '新建成果'} open={formOpen} onClose={() => setFormOpen(false)} destroyOnHidden
      extra={<Space><Button onClick={() => setFormOpen(false)}>取消</Button><Button type="primary" onClick={() => void save()}>保存草稿</Button></Space>}>
      <Form form={form} layout="vertical">
      <AchievementForm form={form} topics={topics} units={units} lockOwnership={Boolean(editing)} definitions={definitions}
        project={{ name: import.meta.env.VITE_PROJECT_NAME ?? '国家科技重大专项示范', code: import.meta.env.VITE_PROJECT_CODE ?? 'GZ-2025-001' }}
        allocations={allocations} nodes={nodes} currentUnitId={editing?.unitId ?? user.unitId} workflowStage={workflowStage} />
      {materialRequirements.length > 0 && <Card size="small" title={editing && ['FORMAL_DRAFT', 'FORMAL_RETURNED'].includes(editing.status) ? '第二轮正式成果材料' : '第三轮后续补充材料'} extra={<Text type="secondary">带红色 * 的文件为必传材料，将在保存草稿时一并上传</Text>}><Space direction="vertical" style={{ width: '100%' }}>{materialRequirements.map((requirement) => <Row key={requirement.materialType} align="middle" gutter={12}><Col span={7}><Space size={4}>{requirement.required && <Text type="danger">*</Text>}<b>{requirement.materialType}</b></Space></Col><Col span={9}><Text type="secondary">{requirement.description}</Text></Col><Col span={8}><label className="ant-btn"><UploadOutlined /> {pendingFiles[requirement.materialType]?.name ?? (editing?.materialLinks.some((item) => item.active && item.materialType === requirement.materialType) ? '已上传（点击替换）' : '选择文件')}<input hidden type="file" onChange={(event) => { const file = event.target.files?.[0]; if (file) setPendingFiles({ ...pendingFiles, [requirement.materialType]: file }); }} /></label></Col></Row>)}</Space></Card>}
      </Form>
    </Drawer>

    <Drawer width={860} title="成果详情" open={Boolean(detail)} onClose={() => setDetail(undefined)} extra={detail && reviewable(detail) && <Space><Button danger icon={<RollbackOutlined />} onClick={() => setDecision('RETURN')}>退回修改</Button><Button type="primary" icon={<CheckOutlined />} onClick={() => setDecision('APPROVE')}>审批通过</Button></Space>}>
      {detail && <><Alert style={{ marginBottom: 16 }} type="info" showIcon message={reviewable(detail) ? (detail.status.endsWith('_FINAL') ? '当前为终审环节' : '当前为初审环节') : '成果详情'} />
        <ApiAchievementDetail achievement={detail} topics={topics} units={units} /></>}
    </Drawer>
    <Modal title={decision === 'APPROVE' ? '确认审批通过' : '退回修改'} open={Boolean(decision)} onCancel={() => setDecision(undefined)} onOk={() => void review()}><Input.TextArea rows={4} value={opinion} onChange={(event) => setOpinion(event.target.value)} placeholder={decision === 'RETURN' ? '请填写退回原因' : '审批意见（选填）'} /></Modal>
  </>;
}
