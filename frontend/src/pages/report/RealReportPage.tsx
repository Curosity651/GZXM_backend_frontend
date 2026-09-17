import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Drawer, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, message } from 'antd';
import { apiRequest } from '../../api/http-client';
import { authApi, type ApiCurrentUser } from '../../api/auth-api';
import { reportApi, type ApiReport, type ReportContent, type ReportRule } from '../../api/report-api';
import { validDemonstrationProgress } from './report-validation';

interface Topic { id: string; code: string; name: string; enabled: boolean; status: string; leadUnitId: string }
interface TopicPage { items: Topic[] }
const statusNames: Record<ApiReport['status'], string> = {
  DRAFT: '草稿', INITIAL_REVIEW: '初审中', FINAL_REVIEW: '终审中', APPROVED: '已通过', RETURNED: '退回修改',
};
const fields: Array<{ key: keyof ReportContent; label: string }> = [
  { key: 'milestoneProgress', label: '里程碑进度' }, { key: 'overallProgress', label: '总体进展' },
  { key: 'demonstrationProgress', label: '示范应用进展' }, { key: 'fundUsage', label: '经费使用情况' },
  { key: 'nextPlan', label: '下一步计划' }, { key: 'problemsAndMeasures', label: '问题及措施' },
];

export function RealReportPage() {
  const [user, setUser] = useState<ApiCurrentUser>();
  const [topics, setTopics] = useState<Topic[]>([]);
  const [reports, setReports] = useState<ApiReport[]>([]);
  const [selected, setSelected] = useState<ApiReport>();
  const [creating, setCreating] = useState(false);
  const [configuring, setConfiguring] = useState(false);
  const [ruleTopic, setRuleTopic] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [createForm] = Form.useForm();
  const [contentForm] = Form.useForm<ReportContent>();
  const [ruleForm] = Form.useForm<ReportRule>();

  const refresh = useCallback(async () => {
    try {
      const [who, topicPage, reportPage] = await Promise.all([
        authApi.me(), apiRequest<TopicPage>('/topics?page=1&size=200'), reportApi.list(new URLSearchParams({ size: '200' })),
      ]);
      setUser(who); setTopics(topicPage.items); setReports(reportPage.items);
    } catch (error) { message.error(error instanceof Error ? error.message : '报告加载失败'); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);
  const canSubmit = user?.actionPermissions.includes('report.submit') ?? false;
  const canConfigure = user?.actionPermissions.includes('report.rule.manage') ?? false;
  const leadTopics = topics.filter(t => t.enabled && t.status === 'ACTIVE' &&
    user?.unitId === t.leadUnitId && user.memberships.some(m => m.topicId === t.id && m.membershipType === 'LEAD' && m.enabled));
  const editable = Boolean(selected && canSubmit && leadTopics.some(t => t.id === selected.topicId) && ['DRAFT', 'RETURNED'].includes(selected.status));
  const reviewable = Boolean(selected && ((selected.status === 'INITIAL_REVIEW' && user?.roleCode === 'RESEARCH_ASSISTANT' && user.actionPermissions.includes('report.initial.approve')) ||
    (selected.status === 'FINAL_REVIEW' && user?.roleCode === 'PROJECT_TECH_LEADER' && user.actionPermissions.includes('report.final.approve'))));

  const open = (report: ApiReport) => { setSelected(report); contentForm.setFieldsValue(report); };
  const execute = async (action: () => Promise<unknown>, success: string) => {
    setBusy(true);
    try { await action(); message.success(success); setSelected(undefined); await refresh(); }
    catch (error) { message.error(error instanceof Error ? error.message : '操作失败'); }
    finally { setBusy(false); }
  };
  const create = async () => {
    const values = await createForm.validateFields();
    await execute(async () => { const item = await reportApi.create(values); setCreating(false); open(item); }, '报告已创建');
  };
  const save = async () => {
    if (!selected) return;
    const values = await contentForm.validateFields();
    await execute(() => reportApi.save(selected.id, { ...values, recordVersion: selected.recordVersion }), '草稿已保存');
  };
  const submit = async () => {
    if (!selected) return;
    if (!validDemonstrationProgress(selected.demonstrationProgress)) {
      message.warning('请先保存示范工程进展：至少 300 字，确无进展填“无”');
      return;
    }
    await execute(() => reportApi.submit(selected.id), '已提交初审');
  };
  const review = (decision: 'APPROVE' | 'RETURN') => {
    if (!selected) return;
    Modal.confirm({ title: decision === 'APPROVE' ? '确认通过报告？' : '退回报告',
      content: <Input.TextArea id="report-review-opinion" placeholder={decision === 'RETURN' ? '请填写退回意见' : '审批意见（可选）'} />,
      onOk: async () => {
        const opinion = (document.getElementById('report-review-opinion') as HTMLTextAreaElement)?.value;
        await execute(() => reportApi.review(selected.id, { decision, opinion, submittedVersion: selected.submittedVersion }), '审批已完成');
      },
    });
  };
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
    <Card title="月季报管理" extra={<Space>
      {canConfigure && <Button onClick={() => { if (topics[0]) void openRule(topics[0].id); }}>配置填报规则</Button>}
      {canSubmit && <Button type="primary" disabled={leadTopics.length === 0} onClick={() => {
        createForm.setFieldsValue({ topicId: leadTopics[0]?.id, reportType: 'MONTHLY', year: new Date().getFullYear(), period: new Date().getMonth() + 1 }); setCreating(true);
      }}>新建报告</Button>}
    </Space>}>
      <Table rowKey="id" dataSource={reports} columns={[
        { title: '课题', render: (_, item) => topics.find(t => t.id === item.topicId)?.name ?? item.topicId },
        { title: '期次', render: (_, item) => `${item.year} 年${item.reportType === 'MONTHLY' ? `${item.period} 月` : `第 ${item.period} 季度`}` },
        { title: '截止日期', dataIndex: 'deadline' },
        { title: '状态', render: (_, item) => <Tag>{statusNames[item.status]}</Tag> },
        { title: '操作', render: (_, item) => <Button type="link" onClick={() => open(item)}>查看 / 办理</Button> },
      ]} />
    </Card>
    <Modal title="新建月季报" open={creating} onCancel={() => setCreating(false)} onOk={() => void create()} confirmLoading={busy}>
      <Form form={createForm} layout="vertical"><Form.Item name="topicId" label="课题" rules={[{ required: true }]}><Select options={leadTopics.map(t => ({ value: t.id, label: `${t.code} ${t.name}` }))} /></Form.Item>
        <Form.Item name="reportType" label="类型" rules={[{ required: true }]}><Select options={[{ value: 'MONTHLY', label: '月报' }, { value: 'QUARTERLY', label: '季报' }]} /></Form.Item>
        <Form.Item name="year" label="年度" rules={[{ required: true }]}><InputNumber min={2000} max={2100} /></Form.Item>
        <Form.Item name="period" label="期次" rules={[{ required: true }]}><InputNumber min={1} max={12} /></Form.Item></Form>
    </Modal>
    <Drawer width={760} open={Boolean(selected)} title={selected ? `${selected.year} 年${selected.reportType === 'MONTHLY' ? '月报' : '季报'} · ${statusNames[selected.status]}` : ''}
      onClose={() => setSelected(undefined)} extra={<Space>{editable && <><Button loading={busy} onClick={() => void save()}>保存草稿</Button><Button type="primary" loading={busy} onClick={() => void submit()}>提交初审</Button></>}
        {reviewable && <><Button loading={busy} danger onClick={() => review('RETURN')}>退回</Button><Button type="primary" loading={busy} onClick={() => review('APPROVE')}>通过</Button></>}</Space>}>
      {selected && <><p>开放：{selected.openDate}　截止：{selected.deadline}</p>
        <Form form={contentForm} layout="vertical" disabled={!editable}>{fields.map(field =>
          <Form.Item key={field.key} name={field.key} label={field.key === 'demonstrationProgress'
            ? '示范应用进展（正式提交至少 300 字；确无进展填“无”）' : field.label}>
            <Input.TextArea rows={4} showCount={field.key === 'demonstrationProgress'} />
          </Form.Item>)}</Form></>}
    </Drawer>
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
