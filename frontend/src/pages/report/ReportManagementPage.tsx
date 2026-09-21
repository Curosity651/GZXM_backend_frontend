import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Col, Divider, Drawer, Form, Input, InputNumber, Modal, Progress, Row, Select, Space, Table, Tabs, Tag, Typography, message } from 'antd';
import { CheckOutlined, DownOutlined, EditOutlined, EyeOutlined, FileAddOutlined, ReloadOutlined, RollbackOutlined, SendOutlined, UpOutlined } from '@ant-design/icons';
import { apiRequest } from '../../api/http-client';
import { authApi, type ApiCurrentUser } from '../../api/auth-api';
import { reportApi, type ApiApproval, type ApiReport, type ReportContent, type ReportProgress, type ReportProgressPeriod, type ReportProgressTopic, type ReportRule } from '../../api/report-api';
import { validDemonstrationProgress } from './report-validation';
import { ReportForm, type ReportFormValues } from '../../components/report/ReportForm';
import { ApprovalTimeline } from '../../components/common/ApprovalTimeline';
import type { ApprovalRecord } from '../../types';

interface Topic { id: string; code: string; name: string; enabled: boolean; status: string; leadUnitId: string;
  members: Array<{ membershipType: string; enabled: boolean; userIds?: string[] }> }
interface TopicPage { items: Topic[] }
const { Text } = Typography;
const statusNames: Record<ApiReport['status'], string> = {
  DRAFT: '草稿', INITIAL_REVIEW: '初审中', FINAL_REVIEW: '终审中', APPROVED: '已通过', RETURNED: '退回修改',
};
const progressStatusNames: Record<ReportProgressPeriod['status'], string> = {
  NOT_OPEN: '尚未到填报期', NOT_CREATED: '尚未创建', DRAFT: '草稿未提交', INITIAL_REVIEW: '初审中',
  FINAL_REVIEW: '终审中', APPROVED: '已通过', RETURNED: '退回修改',
};

export function ReportManagementPage() {
  const [user, setUser] = useState<ApiCurrentUser>();
  const [topics, setTopics] = useState<Topic[]>([]);
  const [reports, setReports] = useState<ApiReport[]>([]);
  const [progress, setProgress] = useState<ReportProgress>({ year: new Date().getFullYear(), topics: [] });
  const [progressLoading, setProgressLoading] = useState(false);
  const [progressFilters, setProgressFilters] = useState<{ topicId?: string; year: number; reportType?: 'MONTHLY' | 'QUARTERLY' }>({ year: new Date().getFullYear() });
  const [selected, setSelected] = useState<ApiReport>();
  const [creating, setCreating] = useState(false);
  const [configuring, setConfiguring] = useState(false);
  const [ruleTopic, setRuleTopic] = useState<string>();
  const [ruleYear, setRuleYear] = useState(new Date().getFullYear());
  const [ruleStatuses, setRuleStatuses] = useState<Record<string, boolean>>({});
  const [createRule, setCreateRule] = useState<ReportRule>();
  const [approvals, setApprovals] = useState<ApiApproval[]>([]);
  const [decision, setDecision] = useState<'APPROVE' | 'RETURN'>();
  const [opinion, setOpinion] = useState('');
  const [busy, setBusy] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [filters, setFilters] = useState<{ topicId?: string; reportType?: ApiReport['reportType']; year?: number; period?: number; status?: ApiReport['status']; pendingOnly: boolean }>({ pendingOnly: false });
  const [createForm] = Form.useForm();
  const [contentForm] = Form.useForm<ReportFormValues>();
  const [ruleForm] = Form.useForm<ReportRule>();
  const createValues = Form.useWatch([], createForm) as { topicId?: string; reportType?: ApiReport['reportType']; year?: number; period?: number } | undefined;
  const monthlyRuleEnabled = Form.useWatch('monthlyEnabled', ruleForm);
  const quarterlyRuleEnabled = Form.useWatch('quarterlyEnabled', ruleForm);
  const pendingDefaultInitialized = useRef(false);

  const refresh = useCallback(async () => {
    try {
      const params = new URLSearchParams({ page: '1', size: '200' });
      if (filters.topicId) params.set('topicId', filters.topicId);
      if (filters.reportType) params.set('reportType', filters.reportType);
      if (filters.year) params.set('year', String(filters.year));
      if (filters.period) params.set('period', String(filters.period));
      if (filters.status) params.set('status', filters.status);
      if (filters.pendingOnly) params.set('pendingForMe', 'true');
      const [who, topicPage, reportPage] = await Promise.all([
        authApi.me(), apiRequest<TopicPage>('/topics?page=1&size=200'), reportApi.list(params),
      ]);
      setUser(who); setTopics(topicPage.items.filter((topic) => topic.enabled && topic.status !== 'DRAFT')); setReports(reportPage.items);
      if (!pendingDefaultInitialized.current) {
        pendingDefaultInitialized.current = true;
        const reviewer = who.actionPermissions.includes('report.initial.approve') || who.actionPermissions.includes('report.final.approve');
        if (reviewer && !filters.pendingOnly) setFilters((current) => ({ ...current, pendingOnly: true }));
      }
    } catch (error) { message.error(error instanceof Error ? error.message : '报告加载失败'); }
  }, [filters]);
  useEffect(() => { void refresh(); }, [refresh]);
  const refreshProgress = useCallback(async () => {
    setProgressLoading(true);
    try { setProgress(await reportApi.progress(progressFilters)); }
    catch (error) { message.error(error instanceof Error ? error.message : '进度统计加载失败'); }
    finally { setProgressLoading(false); }
  }, [progressFilters]);
  useEffect(() => { void refreshProgress(); }, [refreshProgress]);
  const canSubmit = Boolean(user && ['INTERNAL_TOPIC_UNIT', 'EXTERNAL_TOPIC_UNIT'].includes(user.roleCode) && user.actionPermissions.includes('report.submit'));
  const canConfigure = user?.roleCode === 'RESEARCH_ASSISTANT' && user.actionPermissions.includes('report.rule.manage');
  const canReview = Boolean(user?.actionPermissions.includes('report.initial.approve') || user?.actionPermissions.includes('report.final.approve'));
  const leadTopicIds = new Set(topics.filter(t => user?.unitId === t.leadUnitId
    && t.members.some(m => m.membershipType === 'LEAD' && m.enabled && m.userIds?.includes(user.id))).map((topic) => topic.id));
  const leadTopics = topics.filter(t => t.enabled && t.status === 'ACTIVE' && leadTopicIds.has(t.id));
  const canSeeEditableStatuses = user?.roleCode === 'SYSTEM_ADMIN' || (filters.topicId
    ? leadTopicIds.has(filters.topicId)
    : leadTopicIds.size > 0);
  const visibleStatusCodes = user?.roleCode === 'RESEARCH_ASSISTANT'
    ? new Set<ApiReport['status']>(['INITIAL_REVIEW', 'FINAL_REVIEW', 'APPROVED'])
    : user?.roleCode === 'PROJECT_TECH_LEADER'
      ? new Set<ApiReport['status']>(['FINAL_REVIEW', 'APPROVED'])
      : canSeeEditableStatuses
        ? new Set<ApiReport['status']>(Object.keys(statusNames) as ApiReport['status'][])
        : new Set<ApiReport['status']>(['INITIAL_REVIEW', 'FINAL_REVIEW', 'APPROVED']);
  const editable = Boolean(selected && canSubmit && leadTopics.some(t => t.id === selected.topicId) && ['DRAFT', 'RETURNED'].includes(selected.status));
  const reviewable = Boolean(selected && ((selected.status === 'INITIAL_REVIEW' && user?.roleCode === 'RESEARCH_ASSISTANT' && user.actionPermissions.includes('report.initial.approve')) ||
    (selected.status === 'FINAL_REVIEW' && user?.roleCode === 'PROJECT_TECH_LEADER' && user.actionPermissions.includes('report.final.approve'))));
  const filteredReports = reports.filter((report) => (!filters.topicId || report.topicId === filters.topicId)
    && (!filters.reportType || report.reportType === filters.reportType)
    && (!filters.year || report.year === filters.year)
    && (!filters.period || report.period === filters.period)
    && (!filters.status || report.status === filters.status)
    && (!filters.pendingOnly || (user?.roleCode === 'RESEARCH_ASSISTANT' && report.status === 'INITIAL_REVIEW') || (user?.roleCode === 'PROJECT_TECH_LEADER' && report.status === 'FINAL_REVIEW')));
  const open = async (report: ApiReport) => {
    setSelected(report);
    contentForm.setFieldsValue(report);
    try { setApprovals(await reportApi.approvals(report.id)); }
    catch { setApprovals([]); }
  };
  const execute = async (action: () => Promise<unknown>, success: string) => {
    setBusy(true);
    try { await action(); message.success(success); setSelected(undefined); await Promise.all([refresh(), refreshProgress()]); }
    catch (error) { message.error(error instanceof Error ? error.message : '操作失败'); }
    finally { setBusy(false); }
  };
  const create = async () => {
    const values = await createForm.validateFields();
    setBusy(true);
    try {
      const item = await reportApi.create(values);
      setCreating(false); await open(item); await Promise.all([refresh(), refreshProgress()]); message.success('报告已创建');
    } catch (error) { message.error(error instanceof Error ? error.message : '报告创建失败'); }
    finally { setBusy(false); }
  };
  const save = async () => {
    if (!selected) return;
    const values = await contentForm.validateFields();
    setBusy(true);
    try {
      const saved = await reportApi.save(selected.id, { ...values, recordVersion: selected.recordVersion } as ReportContent);
      setSelected(saved); contentForm.setFieldsValue(saved); await refresh(); message.success('草稿已保存');
    } catch (error) { message.error(error instanceof Error ? error.message : '保存失败'); }
    finally { setBusy(false); }
  };
  const submit = async () => {
    if (!selected) return;
    const values = await contentForm.validateFields();
    if (!validDemonstrationProgress(values.demonstrationProgress)) {
      message.warning('请先保存示范工程进展：至少 300 字，确无进展填“无”');
      return;
    }
    await execute(async () => {
      const saved = await reportApi.save(selected.id, { ...values, recordVersion: selected.recordVersion } as ReportContent);
      await reportApi.submit(saved.id);
    }, '已提交初审');
  };
  const confirmReview = async () => {
    if (!selected || !decision) return;
    if (decision === 'RETURN' && !opinion.trim()) { message.warning('退回时必须填写审批意见'); return; }
    await execute(() => reportApi.review(selected.id, { decision, opinion: opinion.trim() || undefined, submittedVersion: selected.submittedVersion }), decision === 'APPROVE' ? '审批已通过' : '已退回修改');
    setDecision(undefined); setOpinion('');
  };
  const loadCreateRule = async (topicId?: string) => {
    setCreateRule(undefined);
    if (!topicId) return;
    try { setCreateRule(await reportApi.rule(topicId)); }
    catch { message.warning('该课题尚未配置月季报规则'); }
  };
  const prepareNew = () => {
    const now = new Date();
    const topicId = leadTopics[0]?.id;
    createForm.resetFields();
    createForm.setFieldsValue({ topicId, reportType: 'MONTHLY', year: now.getFullYear(), period: now.getMonth() + 1 });
    void loadCreateRule(topicId);
    setCreating(true);
  };
  const createWindow = (() => {
    if (!createRule || !createValues?.reportType || !createValues.year || !createValues.period) return undefined;
    const monthly = createValues.reportType === 'MONTHLY';
    if ((monthly && !createRule.monthlyEnabled) || (!monthly && !createRule.quarterlyEnabled)) return undefined;
    const month = monthly ? createValues.period : createRule.quarterlyMonths[createValues.period - 1];
    if (!month) return undefined;
    const lastDay = new Date(createValues.year, month, 0).getDate();
    const date = (day: number) => `${createValues.year}-${String(month).padStart(2, '0')}-${String(Math.min(day, lastDay)).padStart(2, '0')}`;
    return { openDate: date(monthly ? createRule.monthlyOpenDay : createRule.quarterlyOpenDay), deadline: date(monthly ? createRule.monthlyDeadlineDay : createRule.quarterlyDeadlineDay) };
  })();
  const approvalRecords: ApprovalRecord[] = approvals.map((item) => ({
    id: item.id, businessType: 'REPORT', businessId: selected?.id ?? '', stage: 'REPORT',
    level: item.level === 'INITIAL' ? 'INITIAL' : 'FINAL', decision: item.decision === 'APPROVED' ? 'APPROVED' : 'RETURNED',
    opinion: item.opinion ?? '', operatorId: item.operatorId, operatedAt: item.operatedAt, submittedVersion: item.submittedVersion,
  }));
  const loadRuleStatuses = async (effectiveYear: number) => {
    setRuleStatuses({});
    const entries = await Promise.all(topics.map(async (topic) => {
      try { await reportApi.rule(topic.id, effectiveYear); return [topic.id, true] as const; }
      catch { return [topic.id, false] as const; }
    }));
    setRuleStatuses(Object.fromEntries(entries));
  };
  const openRule = async (topicId: string, effectiveYear = ruleYear) => {
    setRuleTopic(topicId);
    try { ruleForm.setFieldsValue(await reportApi.rule(topicId, effectiveYear)); }
    catch { ruleForm.setFieldsValue({ effectiveYear, monthlyEnabled: true, monthlyStartYear: effectiveYear, monthlyStartPeriod: 1,
      monthlyEndYear: effectiveYear, monthlyEndPeriod: 12, monthlyOpenDay: 1,
      monthlyDeadlineDay: 25, quarterlyEnabled: true, quarterlyStartYear: effectiveYear, quarterlyStartPeriod: 1,
      quarterlyEndYear: effectiveYear, quarterlyEndPeriod: 4, quarterlyOpenDay: 1, quarterlyDeadlineDay: 25,
      quarterlyMonths: [3, 6, 9, 12], recordVersion: 0 }); }
    setConfiguring(true);
  };
  const openRulePanel = () => {
    if (!topics[0]) return;
    const effectiveYear = new Date().getFullYear();
    setRuleYear(effectiveYear);
    void loadRuleStatuses(effectiveYear);
    void openRule(topics[0].id, effectiveYear);
  };
  const changeRuleYear = (effectiveYear: number | null) => {
    if (!effectiveYear) return;
    setRuleYear(effectiveYear);
    void loadRuleStatuses(effectiveYear);
    if (ruleTopic) void openRule(ruleTopic, effectiveYear);
  };
  const saveRule = async () => {
    if (!ruleTopic) return;
    const values = await ruleForm.validateFields();
    setBusy(true);
    try {
      const saved = await reportApi.saveRule(ruleTopic, values);
      ruleForm.setFieldsValue(saved);
      setRuleYear(saved.effectiveYear);
      setRuleStatuses((current) => ({ ...current, [ruleTopic]: true }));
      message.success('规则已保存');
      setConfiguring(false);
      await refresh();
    } catch (error) { message.error(error instanceof Error ? error.message : '规则保存失败'); }
    finally { setBusy(false); }
  };
  const ruleStatusText = (topicId?: string) => topicId && ruleStatuses[topicId] === true ? '已配置' : topicId && ruleStatuses[topicId] === false ? '未配置' : '检查中';
  const ruleStatusColor = (topicId?: string) => topicId && ruleStatuses[topicId] === true ? 'success' : topicId && ruleStatuses[topicId] === false ? 'default' : 'processing';
  const progressStatusColor = (status: ReportProgressPeriod['status']) => status === 'APPROVED' ? 'green'
    : status === 'NOT_CREATED' ? 'red' : status === 'DRAFT' || status === 'RETURNED' ? 'orange'
      : status === 'INITIAL_REVIEW' ? 'purple' : status === 'FINAL_REVIEW' ? 'blue' : 'default';
  const renderProgressPeriods = (topic: ReportProgressTopic, type: ReportProgressPeriod['reportType']) => {
    const periods = topic.periods.filter((period) => period.reportType === type);
    if (!periods.length) return <Alert type="info" showIcon message={`当前年度未配置${type === 'MONTHLY' ? '月报' : '季报'}填报范围`} />;
    return <Table<ReportProgressPeriod> size="small" rowKey={(row) => `${row.reportType}-${row.year}-${row.period}`}
      dataSource={periods} pagination={false} columns={[
        { title: '报告期次', width: 190, render: (_: unknown, row) => row.reportType === 'MONTHLY' ? `${row.year} 年 ${row.period} 月` : `${row.year} 年第 ${row.period} 季度` },
        { title: '开放日期', dataIndex: 'openDate', width: 130 },
        { title: '截止日期', dataIndex: 'deadline', width: 130 },
        { title: '当前状态', width: 150, render: (_: unknown, row) => <Tag color={progressStatusColor(row.status)}>{progressStatusNames[row.status]}</Tag> },
        { title: '时效', width: 110, render: (_: unknown, row) => row.timing === 'UPCOMING' ? '—' : row.timing === 'OVERDUE' ? <Tag color="red">已逾期</Tag> : <Tag color="green">正常</Tag> },
      ]} />;
  };

  return <>
    <Card className="report-progress-card" title={<div><div>月季报进度</div><Text type="secondary" style={{ fontSize: 13, fontWeight: 400 }}>汇总统计独立于下方报告列表筛选，点击课题查看缺少的具体期次</Text></div>}
      extra={<Space wrap>
        <Text>课题</Text><Select allowClear placeholder="全部课题" value={progressFilters.topicId} style={{ width: 280 }}
          onChange={(value) => setProgressFilters((current) => ({ ...current, topicId: value }))}
          options={topics.map((topic) => ({ value: topic.id, label: `${topic.code} ${topic.name}` }))} />
        <Text>年度</Text><InputNumber min={2000} max={2100} value={progressFilters.year} style={{ width: 110 }}
          onChange={(value) => value && setProgressFilters((current) => ({ ...current, year: value }))} />
        <Text>类型</Text><Select allowClear placeholder="全部" value={progressFilters.reportType} style={{ width: 110 }}
          onChange={(value) => setProgressFilters((current) => ({ ...current, reportType: value }))}
          options={[{ value: 'MONTHLY', label: '月报' }, { value: 'QUARTERLY', label: '季报' }]} />
        <Button icon={<ReloadOutlined />} loading={progressLoading} onClick={() => void refreshProgress()}>刷新</Button>
      </Space>} style={{ marginBottom: 16 }}>
      <Table<ReportProgressTopic> loading={progressLoading} rowKey="topicId" size="small" dataSource={progress.topics} pagination={false}
        locale={{ emptyText: '当前筛选范围内暂无月季报规则' }}
        expandable={{ expandRowByClick: true, expandedRowRender: (topic) => <Tabs defaultActiveKey={progressFilters.reportType === 'QUARTERLY' ? 'quarterly' : 'monthly'} items={[
          { key: 'monthly', label: `月报（${topic.periods.filter((row) => row.reportType === 'MONTHLY' && row.submittedVersion > 0).length}/${topic.periods.filter((row) => row.reportType === 'MONTHLY').length}）`, children: renderProgressPeriods(topic, 'MONTHLY') },
          { key: 'quarterly', label: `季报（${topic.periods.filter((row) => row.reportType === 'QUARTERLY' && row.submittedVersion > 0).length}/${topic.periods.filter((row) => row.reportType === 'QUARTERLY').length}）`, children: renderProgressPeriods(topic, 'QUARTERLY') },
        ].filter((item) => !progressFilters.reportType || item.key === (progressFilters.reportType === 'MONTHLY' ? 'monthly' : 'quarterly'))} /> }}
        columns={[
          { title: '课题汇总', render: (_: unknown, row) => <Space><Text strong>{row.topicName}</Text><Tag color="blue">{row.topicCode}</Tag></Space> },
          { title: '应填期数', dataIndex: 'expected', width: 100 },
          { title: '已提交', dataIndex: 'submitted', width: 90 },
          { title: '已通过', dataIndex: 'approved', width: 90 },
          { title: '提交进度', width: 210, render: (_: unknown, row) => { const rate = row.expected ? Math.round(row.submitted * 100 / row.expected) : 0; return <Space><Text strong>{rate}%</Text><Progress className="report-progress-rate" size="small" percent={rate} showInfo={false} status={rate >= 100 ? 'success' : 'active'} /></Space>; } },
          { title: '尚未提交 / 逾期', width: 190, render: (_: unknown, row) => <Space>{row.missing > 0 ? <Tag color="orange">{row.missing} 期未提交</Tag> : <Tag color="green">全部提交</Tag>}{row.overdue > 0 && <Tag color="red">{row.overdue} 期逾期</Tag>}</Space> },
        ]} />
    </Card>
    <Card title={`月季报列表（${filteredReports.length}）`} extra={<Space>
      <Button icon={<ReloadOutlined />} onClick={() => void Promise.all([refresh(), refreshProgress()])}>刷新</Button>
      {canConfigure && <Button disabled={topics.length === 0} onClick={openRulePanel}>配置填报规则</Button>}
      {canSubmit && <Button type="primary" icon={<FileAddOutlined />} disabled={leadTopics.length === 0} onClick={prepareNew}>新建月季报</Button>}
    </Space>}>
      <div className={`report-filter-grid${expanded ? ' is-expanded' : ''}${canReview ? ' has-review-scope' : ''}`}>
        {canReview && <Space className="report-filter-field" size={8}><Text>处理范围</Text><Select value={filters.pendingOnly} onChange={(value) => setFilters({ ...filters, pendingOnly: value })} options={[{ label: '待我处理', value: true }, { label: '全部报告', value: false }]} /></Space>}
        <Space className="report-filter-field" size={8}><Text>所属课题</Text><Select allowClear placeholder="全部课题" value={filters.topicId} onChange={(value) => setFilters({ ...filters, topicId: value })} options={topics.map((topic) => ({ label: `${topic.code} ${topic.name}`, value: topic.id }))} /></Space>
        <Space className="report-filter-field" size={8}><Text>报告状态</Text><Select allowClear placeholder="全部状态" value={filters.status} onChange={(value) => setFilters({ ...filters, status: value })} options={Object.entries(statusNames).filter(([value]) => visibleStatusCodes.has(value as ApiReport['status'])).map(([value, label]) => ({ value, label }))} /></Space>
        {expanded && <>
          <Space className="report-filter-field" size={8}><Text>报告类型</Text><Select allowClear placeholder="全部类型" value={filters.reportType} onChange={(value) => setFilters({ ...filters, reportType: value })} options={[{ label: '月报', value: 'MONTHLY' }, { label: '季报', value: 'QUARTERLY' }]} /></Space>
          <Space className="report-filter-field" size={8}><Text>年度</Text><InputNumber placeholder="全部年度" value={filters.year} onChange={(value) => setFilters({ ...filters, year: value ?? undefined })} /></Space>
          <Space className="report-filter-field" size={8}><Text>期次</Text><InputNumber placeholder="全部期次" value={filters.period} onChange={(value) => setFilters({ ...filters, period: value ?? undefined })} /></Space>
        </>}
        <Space className="report-filter-actions" size={10}><Button onClick={() => setFilters({ pendingOnly: canReview })}>重置</Button><Button type="link" icon={expanded ? <UpOutlined /> : <DownOutlined />} onClick={() => setExpanded((value) => !value)}>{expanded ? '收起' : '展开'}</Button></Space>
      </div>
      <Divider style={{ margin: '20px 0' }} />
      <Table rowKey="id" dataSource={filteredReports} pagination={{ defaultPageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50], showTotal: (total) => `共 ${total} 条` }} columns={[
        { title: '课题', render: (_, item) => topics.find(t => t.id === item.topicId)?.name ?? item.topicId },
        { title: '报告类型', width: 100, render: (_, item) => <Tag color={item.reportType === 'MONTHLY' ? 'blue' : 'purple'}>{item.reportType === 'MONTHLY' ? '月报' : '季报'}</Tag> },
        { title: '报告期次', render: (_, item) => `${item.year} 年${item.reportType === 'MONTHLY' ? `${item.period} 月` : `第 ${item.period} 季度`}` },
        { title: '截止日期', dataIndex: 'deadline' },
        { title: '状态', render: (_, item) => <Tag>{statusNames[item.status]}</Tag> },
        { title: '当前环节', render: (_, item) => item.status === 'INITIAL_REVIEW' ? '科研助理初审' : item.status === 'FINAL_REVIEW' ? '技术负责人终审' : item.status === 'APPROVED' ? '审批完成' : '课题单位填报' },
        { title: '提交时间', dataIndex: 'submittedAt', render: (value) => value ?? '—' },
        { title: '时效', width: 80, render: (_, item) => item.overdue ? <Tag color="red">逾期</Tag> : <Tag color="green">正常</Tag> },
        { title: '操作', width: 120, render: (_, item) => <Button type="link" icon={['DRAFT', 'RETURNED'].includes(item.status) ? <EditOutlined /> : <EyeOutlined />} onClick={() => open(item)}>{(item.status === 'INITIAL_REVIEW' && user?.roleCode === 'RESEARCH_ASSISTANT') || (item.status === 'FINAL_REVIEW' && user?.roleCode === 'PROJECT_TECH_LEADER') ? '审批' : ['DRAFT', 'RETURNED'].includes(item.status) && leadTopics.some((topic) => topic.id === item.topicId) ? '编辑' : '详情'}</Button> },
      ]} />
    </Card>
    <Modal title="新建月季报" open={creating} onCancel={() => setCreating(false)} onOk={() => void create()} okText="开始填报" confirmLoading={busy}>
      <Form form={createForm} layout="vertical">
        <Form.Item name="topicId" label="所属课题" rules={[{ required: true }]}><Select onChange={(value) => void loadCreateRule(value)} options={leadTopics.map(t => ({ value: t.id, label: `${t.code} ${t.name}` }))} /></Form.Item>
        <Row gutter={12}>
          <Col span={8}><Form.Item name="reportType" label="报告类型" rules={[{ required: true }]}><Select options={[{ value: 'MONTHLY', label: '月报' }, { value: 'QUARTERLY', label: '季报' }]} /></Form.Item></Col>
          <Col span={8}><Form.Item name="year" label="年度" rules={[{ required: true }]}><InputNumber min={2020} max={2100} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item name="period" label="期次" rules={[{ required: true }]}><InputNumber min={1} max={createValues?.reportType === 'QUARTERLY' ? createRule?.quarterlyMonths.length ?? 4 : 12} style={{ width: '100%' }} /></Form.Item></Col>
        </Row>
        <Row gutter={12}>
          <Col span={12}><Form.Item label="开放日期"><Input value={createWindow?.openDate} placeholder="选择完整信息后自动计算" readOnly /></Form.Item></Col>
          <Col span={12}><Form.Item label="截止日期"><Input value={createWindow?.deadline} placeholder="选择完整信息后自动计算" readOnly /></Form.Item></Col>
        </Row>
      </Form>
    </Modal>
    <Drawer width={780} open={Boolean(selected)} title={selected?.reportType === 'MONTHLY' ? '课题月报' : '课题季报'}
      onClose={() => setSelected(undefined)} extra={<Space>{editable && <><Button loading={busy} onClick={() => void save()}>保存草稿</Button><Button type="primary" icon={<SendOutlined />} loading={busy} onClick={() => void submit()}>提交初审</Button></>}
        {reviewable && <><Button loading={busy} danger icon={<RollbackOutlined />} onClick={() => setDecision('RETURN')}>退回修改</Button><Button type="primary" icon={<CheckOutlined />} loading={busy} onClick={() => setDecision('APPROVE')}>审批通过</Button></>}</Space>}>
      {selected && <>
        <Space wrap style={{ marginBottom: 16 }}>
          <Text strong>{topics.find((topic) => topic.id === selected.topicId)?.name ?? selected.topicId}</Text>
          <Tag>{selected.deadline} 截止</Tag><Tag>{statusNames[selected.status]}</Tag>
          <Tag>记录 V{selected.recordVersion}</Tag><Tag color="blue">提交 V{selected.submittedVersion}</Tag>
        </Space>
        <ReportForm form={contentForm} disabled={!editable} />
        <Card title="审批记录" size="small" style={{ marginTop: 16 }}><ApprovalTimeline records={approvalRecords} users={[]} /></Card>
      </>}
    </Drawer>
    <Modal title={decision === 'APPROVE' ? '确认审批通过' : '退回修改'} open={Boolean(decision)} onCancel={() => { setDecision(undefined); setOpinion(''); }} onOk={() => void confirmReview()} confirmLoading={busy}>
      <Input.TextArea rows={4} value={opinion} onChange={(event) => setOpinion(event.target.value)} placeholder={decision === 'RETURN' ? '请填写明确的退回原因' : '审批意见（选填）'} />
    </Modal>
    <Modal width={1040} title="课题填报规则" open={configuring} onCancel={() => setConfiguring(false)} onOk={() => void saveRule()} confirmLoading={busy} okText="保存规则">
      <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
        <Select style={{ width: '100%' }} value={ruleTopic} onChange={value => void openRule(value, ruleYear)} options={topics.map(t => ({ value: t.id, label: <Space><span>{t.name}</span><Tag color={ruleStatusColor(t.id)}>{ruleStatusText(t.id)}</Tag></Space> }))} />
        <Tag color={ruleStatusColor(ruleTopic)} style={{ display: 'flex', alignItems: 'center', marginInlineEnd: 0, paddingInline: 12 }}>{ruleStatusText(ruleTopic)}</Tag>
      </Space.Compact>
      <Alert type="info" showIcon style={{ marginBottom: 16 }} message="分别设置月报、季报需要提交的起止期次；月季报进度将据此自动计算应填、未提交和逾期期数。" />
      <Form form={ruleForm} layout="vertical">
        <Form.Item name="effectiveYear" label="规则生效年度" rules={[{ required: true, message: '请选择规则生效年度' }]} extra="用于查找和区分规则，应与已启用范围中最早的开始年度一致。">
          <InputNumber min={2000} max={2100} style={{ width: 220 }} onChange={changeRuleYear} />
        </Form.Item>
        <Row gutter={16} align="stretch">
          <Col xs={24} lg={12}>
            <Card className="report-rule-section" size="small" title="月报规则" extra={<Form.Item name="monthlyEnabled" noStyle><Select style={{ width: 100 }} options={[{ value: true, label: '已启用' }, { value: false, label: '已停用' }]} /></Form.Item>}>
              <Text type="secondary">按自然月连续生成应填期次。</Text>
              <Divider />
              <Text strong>提交范围</Text>
              <Row gutter={8} style={{ marginTop: 10 }}>
                <Col span={6}><Form.Item name="monthlyStartYear" label="开始年度" rules={monthlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><InputNumber disabled={!monthlyRuleEnabled} min={2000} max={2100} style={{ width: '100%' }} /></Form.Item></Col>
                <Col span={6}><Form.Item name="monthlyStartPeriod" label="开始月份" rules={monthlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><Select disabled={!monthlyRuleEnabled} options={Array.from({ length: 12 }, (_, index) => ({ value: index + 1, label: `${index + 1} 月` }))} /></Form.Item></Col>
                <Col span={6}><Form.Item name="monthlyEndYear" label="结束年度" rules={monthlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><InputNumber disabled={!monthlyRuleEnabled} min={2000} max={2100} style={{ width: '100%' }} /></Form.Item></Col>
                <Col span={6}><Form.Item name="monthlyEndPeriod" label="结束月份" rules={monthlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><Select disabled={!monthlyRuleEnabled} options={Array.from({ length: 12 }, (_, index) => ({ value: index + 1, label: `${index + 1} 月` }))} /></Form.Item></Col>
              </Row>
              <Text strong>每期时间窗口</Text>
              <Row gutter={8} style={{ marginTop: 10 }}>
                <Col span={12}><Form.Item name="monthlyOpenDay" label="当月开放日" rules={monthlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><InputNumber disabled={!monthlyRuleEnabled} min={1} max={31} addonAfter="日" style={{ width: '100%' }} /></Form.Item></Col>
                <Col span={12}><Form.Item name="monthlyDeadlineDay" label="当月截止日" rules={monthlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><InputNumber disabled={!monthlyRuleEnabled} min={1} max={31} addonAfter="日" style={{ width: '100%' }} /></Form.Item></Col>
              </Row>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card className="report-rule-section" size="small" title="季报规则" extra={<Form.Item name="quarterlyEnabled" noStyle><Select style={{ width: 100 }} options={[{ value: true, label: '已启用' }, { value: false, label: '已停用' }]} /></Form.Item>}>
              <Text type="secondary">按季度连续生成应填期次。</Text>
              <Divider />
              <Text strong>提交范围</Text>
              <Row gutter={8} style={{ marginTop: 10 }}>
                <Col span={6}><Form.Item name="quarterlyStartYear" label="开始年度" rules={quarterlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><InputNumber disabled={!quarterlyRuleEnabled} min={2000} max={2100} style={{ width: '100%' }} /></Form.Item></Col>
                <Col span={6}><Form.Item name="quarterlyStartPeriod" label="开始季度" rules={quarterlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><Select disabled={!quarterlyRuleEnabled} options={Array.from({ length: 4 }, (_, index) => ({ value: index + 1, label: `第 ${index + 1} 季度` }))} /></Form.Item></Col>
                <Col span={6}><Form.Item name="quarterlyEndYear" label="结束年度" rules={quarterlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><InputNumber disabled={!quarterlyRuleEnabled} min={2000} max={2100} style={{ width: '100%' }} /></Form.Item></Col>
                <Col span={6}><Form.Item name="quarterlyEndPeriod" label="结束季度" rules={quarterlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><Select disabled={!quarterlyRuleEnabled} options={Array.from({ length: 4 }, (_, index) => ({ value: index + 1, label: `第 ${index + 1} 季度` }))} /></Form.Item></Col>
              </Row>
              <Text strong>每期时间窗口</Text>
              <Row gutter={8} style={{ marginTop: 10 }}>
                <Col span={12}><Form.Item name="quarterlyOpenDay" label="季末月开放日" rules={quarterlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><InputNumber disabled={!quarterlyRuleEnabled} min={1} max={31} addonAfter="日" style={{ width: '100%' }} /></Form.Item></Col>
                <Col span={12}><Form.Item name="quarterlyDeadlineDay" label="季末月截止日" rules={quarterlyRuleEnabled ? [{ required: true, message: '必填' }] : []}><InputNumber disabled={!quarterlyRuleEnabled} min={1} max={31} addonAfter="日" style={{ width: '100%' }} /></Form.Item></Col>
              </Row>
              <Form.Item name="quarterlyMonths" label="各季度对应月份" rules={quarterlyRuleEnabled ? [{ required: true, message: '请选择 4 个对应月份' }] : []}>
                <Select disabled={!quarterlyRuleEnabled} mode="multiple" maxCount={4} options={Array.from({ length: 12 }, (_, index) => ({ value: index + 1, label: `${index + 1} 月` }))} />
              </Form.Item>
            </Card>
          </Col>
        </Row>
      </Form>
    </Modal>
  </>;
}
