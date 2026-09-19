import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Drawer, Form, Input, InputNumber, Modal, Progress, Row, Select, Space, Statistic, Table, Tag, Typography, message } from 'antd';
import { CheckOutlined, DownOutlined, EditOutlined, EyeOutlined, FileAddOutlined, ReloadOutlined, RollbackOutlined, SearchOutlined, SendOutlined, UpOutlined } from '@ant-design/icons';
import { apiRequest } from '../../api/http-client';
import { authApi, type ApiCurrentUser } from '../../api/auth-api';
import { reportApi, type ApiApproval, type ApiReport, type ReportContent, type ReportRule } from '../../api/report-api';
import { validDemonstrationProgress } from './report-validation';
import { ReportForm, type ReportFormValues } from '../../components/report/ReportForm';
import { ApprovalTimeline } from '../../components/common/ApprovalTimeline';
import type { ApprovalRecord } from '../../types';

interface Topic { id: string; code: string; name: string; enabled: boolean; status: string; leadUnitId: string }
interface TopicPage { items: Topic[] }
const { Text } = Typography;
const statusNames: Record<ApiReport['status'], string> = {
  DRAFT: '草稿', INITIAL_REVIEW: '初审中', FINAL_REVIEW: '终审中', APPROVED: '已通过', RETURNED: '退回修改',
};

export function ReportManagementPage() {
  const [user, setUser] = useState<ApiCurrentUser>();
  const [topics, setTopics] = useState<Topic[]>([]);
  const [reports, setReports] = useState<ApiReport[]>([]);
  const [selected, setSelected] = useState<ApiReport>();
  const [creating, setCreating] = useState(false);
  const [configuring, setConfiguring] = useState(false);
  const [ruleTopic, setRuleTopic] = useState<string>();
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

  const refresh = useCallback(async () => {
    try {
      const [who, topicPage, reportPage] = await Promise.all([
        authApi.me(), apiRequest<TopicPage>('/topics?page=1&size=200'), reportApi.list(new URLSearchParams({ size: '200' })),
      ]);
      setUser(who); setTopics(topicPage.items); setReports(reportPage.items);
    } catch (error) { message.error(error instanceof Error ? error.message : '报告加载失败'); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);
  const canSubmit = Boolean(user && ['INTERNAL_TOPIC_UNIT', 'EXTERNAL_TOPIC_UNIT'].includes(user.roleCode) && user.actionPermissions.includes('report.submit'));
  const canConfigure = user?.roleCode === 'RESEARCH_ASSISTANT' && user.actionPermissions.includes('report.rule.manage');
  const canReview = Boolean(user?.actionPermissions.includes('report.initial.approve') || user?.actionPermissions.includes('report.final.approve'));
  const leadTopics = topics.filter(t => t.enabled && t.status === 'ACTIVE' &&
    user?.unitId === t.leadUnitId && user.memberships.some(m => m.topicId === t.id && m.membershipType === 'LEAD' && m.enabled));
  const editable = Boolean(selected && canSubmit && leadTopics.some(t => t.id === selected.topicId) && ['DRAFT', 'RETURNED'].includes(selected.status));
  const reviewable = Boolean(selected && ((selected.status === 'INITIAL_REVIEW' && user?.roleCode === 'RESEARCH_ASSISTANT' && user.actionPermissions.includes('report.initial.approve')) ||
    (selected.status === 'FINAL_REVIEW' && user?.roleCode === 'PROJECT_TECH_LEADER' && user.actionPermissions.includes('report.final.approve'))));
  const filteredReports = reports.filter((report) => (!filters.topicId || report.topicId === filters.topicId)
    && (!filters.reportType || report.reportType === filters.reportType)
    && (!filters.year || report.year === filters.year)
    && (!filters.period || report.period === filters.period)
    && (!filters.status || report.status === filters.status)
    && (!filters.pendingOnly || (user?.roleCode === 'RESEARCH_ASSISTANT' && report.status === 'INITIAL_REVIEW') || (user?.roleCode === 'PROJECT_TECH_LEADER' && report.status === 'FINAL_REVIEW')));
  const stats = {
    total: filteredReports.length,
    draft: filteredReports.filter((item) => item.status === 'DRAFT').length,
    reviewing: filteredReports.filter((item) => ['INITIAL_REVIEW', 'FINAL_REVIEW'].includes(item.status)).length,
    approved: filteredReports.filter((item) => item.status === 'APPROVED').length,
    returned: filteredReports.filter((item) => item.status === 'RETURNED').length,
    overdue: filteredReports.filter((item) => item.overdue).length,
  };
  const submittedCount = filteredReports.filter((item) => item.status !== 'DRAFT').length;
  const passRate = submittedCount ? Math.round((stats.approved / submittedCount) * 100) : 0;

  const open = async (report: ApiReport) => {
    setSelected(report);
    contentForm.setFieldsValue(report);
    try { setApprovals(await reportApi.approvals(report.id)); }
    catch { setApprovals([]); }
  };
  const execute = async (action: () => Promise<unknown>, success: string) => {
    setBusy(true);
    try { await action(); message.success(success); setSelected(undefined); await refresh(); }
    catch (error) { message.error(error instanceof Error ? error.message : '操作失败'); }
    finally { setBusy(false); }
  };
  const create = async () => {
    const values = await createForm.validateFields();
    setBusy(true);
    try {
      const item = await reportApi.create(values);
      setCreating(false); await open(item); await refresh(); message.success('报告已创建');
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
  const openRule = async (topicId: string) => {
    setRuleTopic(topicId);
    try { ruleForm.setFieldsValue(await reportApi.rule(topicId)); }
    catch { ruleForm.setFieldsValue({ effectiveYear: new Date().getFullYear(), monthlyEnabled: true, monthlyOpenDay: 1,
      monthlyDeadlineDay: 25, quarterlyEnabled: true, quarterlyOpenDay: 1, quarterlyDeadlineDay: 25,
      quarterlyMonths: [3, 6, 9, 12], recordVersion: 0 }); }
    setConfiguring(true);
  };
  const saveRule = async () => {
    if (!ruleTopic) return;
    const values = await ruleForm.validateFields();
    await execute(async () => { await reportApi.saveRule(ruleTopic, values); setConfiguring(false); }, '规则已保存');
  };

  return <>
    <Card className="report-filter-card" style={{ marginBottom: 16 }}><div className={`report-filter-grid${expanded ? ' is-expanded' : ''}`}>
      {canReview && <Space className="report-filter-field" size={8}><Text>处理范围</Text><Select value={filters.pendingOnly} onChange={(value) => setFilters({ ...filters, pendingOnly: value })} options={[{ label: '待我处理', value: true }, { label: '全部报告', value: false }]} /></Space>}
      <Space className="report-filter-field" size={8}><Text>所属课题</Text><Select allowClear placeholder="全部课题" value={filters.topicId} onChange={(value) => setFilters({ ...filters, topicId: value })} options={topics.map((topic) => ({ label: `${topic.code} ${topic.name}`, value: topic.id }))} /></Space>
      <Space className="report-filter-field" size={8}><Text>报告状态</Text><Select allowClear placeholder="全部状态" value={filters.status} onChange={(value) => setFilters({ ...filters, status: value })} options={Object.entries(statusNames).map(([value, label]) => ({ value, label }))} /></Space>
      {expanded && <>
        <Space className="report-filter-field" size={8}><Text>报告类型</Text><Select allowClear placeholder="全部类型" value={filters.reportType} onChange={(value) => setFilters({ ...filters, reportType: value })} options={[{ label: '月报', value: 'MONTHLY' }, { label: '季报', value: 'QUARTERLY' }]} /></Space>
        <Space className="report-filter-field" size={8}><Text>年度</Text><InputNumber placeholder="全部年度" value={filters.year} onChange={(value) => setFilters({ ...filters, year: value ?? undefined })} /></Space>
        <Space className="report-filter-field" size={8}><Text>期次</Text><InputNumber placeholder="全部期次" value={filters.period} onChange={(value) => setFilters({ ...filters, period: value ?? undefined })} /></Space>
      </>}
      <Space className="report-filter-actions" size={10}><Button type="primary" icon={<SearchOutlined />}>查询</Button><Button onClick={() => setFilters({ pendingOnly: canReview })}>重置</Button><Button type="link" icon={expanded ? <UpOutlined /> : <DownOutlined />} onClick={() => setExpanded((value) => !value)}>{expanded ? '收起' : '展开'}</Button></Space>
    </div></Card>
    <Card title="月季报进度" style={{ marginBottom: 16 }}><Row gutter={[12, 12]}>
      {[["已发起报告", stats.total], ['草稿', stats.draft], ['审核中', stats.reviewing], ['已通过', stats.approved], ['退回修改', stats.returned], ['逾期', stats.overdue]].map(([label, value]) => <Col flex="1 1 140px" key={String(label)}><Statistic title={label} value={value} /></Col>)}
      <Col flex="1 1 220px"><Text type="secondary">审批通过率</Text><Progress percent={passRate} status={passRate >= 100 ? 'success' : 'active'} /></Col>
    </Row></Card>
    <Card title={`月季报列表（${filteredReports.length}）`} extra={<Space>
      <Button icon={<ReloadOutlined />} onClick={() => void refresh()}>刷新</Button>
      {canConfigure && <Button onClick={() => { if (topics[0]) void openRule(topics[0].id); }}>配置填报规则</Button>}
      {canSubmit && <Button type="primary" icon={<FileAddOutlined />} disabled={leadTopics.length === 0} onClick={prepareNew}>新建月季报</Button>}
    </Space>}>
      <Table rowKey="id" dataSource={filteredReports} columns={[
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
    <Modal title="课题填报规则" open={configuring} onCancel={() => setConfiguring(false)} onOk={() => void saveRule()} confirmLoading={busy}>
      <Select style={{ width: '100%', marginBottom: 16 }} value={ruleTopic} onChange={value => void openRule(value)} options={topics.map(t => ({ value: t.id, label: t.name }))} />
      <Form form={ruleForm} layout="vertical"><Form.Item name="effectiveYear" label="生效年度"><InputNumber min={2000} max={2100} /></Form.Item>
        <Form.Item name="monthlyEnabled" label="启用月报"><Select options={[{ value: true, label: '是' }, { value: false, label: '否' }]} /></Form.Item>
        <Space><Form.Item name="monthlyOpenDay" label="月报开放日"><InputNumber min={1} max={31} /></Form.Item><Form.Item name="monthlyDeadlineDay" label="月报截止日"><InputNumber min={1} max={31} /></Form.Item></Space>
        <Form.Item name="quarterlyEnabled" label="启用季报"><Select options={[{ value: true, label: '是' }, { value: false, label: '否' }]} /></Form.Item>
        <Space><Form.Item name="quarterlyOpenDay" label="季报开放日"><InputNumber min={1} max={31} /></Form.Item><Form.Item name="quarterlyDeadlineDay" label="季报截止日"><InputNumber min={1} max={31} /></Form.Item></Space>
        <Form.Item name="quarterlyMonths" label="四个季度对应月份"><Select mode="multiple" maxCount={4} options={Array.from({ length: 12 }, (_, i) => ({ value: i + 1, label: `${i + 1} 月` }))} /></Form.Item>
      </Form>
    </Modal>
  </>;
}
