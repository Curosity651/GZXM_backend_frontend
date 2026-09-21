import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Card, Col, Progress, Row, Space, Spin, Statistic, Table, Tag, Typography } from 'antd';
import {
  CheckCircleOutlined, ClockCircleOutlined, FileDoneOutlined,
  FolderOpenOutlined, RiseOutlined, WarningOutlined,
} from '@ant-design/icons';
import { apiRequest } from '../api/http-client';
import { isBusinessTopic, topicApi, type ApiTopic } from '../api/topic-api';
import { achievementApi, type ApiAchievement } from '../api/achievement-api';
import { reportApi, type ApiReport } from '../api/report-api';
import { indicatorApi } from '../api/indicator-api';
import { useSessionStore } from '../store/session';
import { StatusTag } from '../components/common/StatusTag';
import { collectAllPages, latestEnabledNode } from '../domain/dashboard';

const { Text } = Typography;

interface Summary { topicCount: number; reportApproved: number; archiveCompleted: number }
interface TopicSummary { topicId: string; topicCode: string; topicName: string; planned: number; completed: number; gap: number; rate: number }
interface TaskRow { id: string; title: string; status: string; type: string }

const achievementStatusLabels: Record<string, string> = {
  DRAFT: '预审草稿', PRE_INITIAL: '预审初审中', PRE_FINAL: '预审终审中', PRE_RETURNED: '预审退回',
  FORMAL_DRAFT: '第二轮材料草稿', FORMAL_INITIAL: '第二轮初审中', FORMAL_FINAL: '第二轮终审中', FORMAL_RETURNED: '第二轮退回',
  WAIT_PUBLICATION: '等待正式刊出材料', WAIT_GRANT: '等待授权材料', WAIT_CERTIFICATE: '等待予以发布材料', SUPPLEMENT_INITIAL: '第三轮初审中',
  SUPPLEMENT_FINAL: '补充材料终审中', SUPPLEMENT_RETURNED: '补充材料退回', EFFECTIVE: '已完成',
};
const reportStatusLabels: Record<string, string> = {
  DRAFT: '草稿', INITIAL_REVIEW: '初审中', FINAL_REVIEW: '终审中', APPROVED: '已通过', RETURNED: '退回修改',
};

export function HomePage() {
  const user = useSessionStore((state) => state.user);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [summary, setSummary] = useState<Summary>();
  const [topics, setTopics] = useState<ApiTopic[]>([]);
  const [achievements, setAchievements] = useState<ApiAchievement[]>([]);
  const [reports, setReports] = useState<ApiReport[]>([]);
  const [topicSummaries, setTopicSummaries] = useState<TopicSummary[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const [summaryData, topicRows, nodes] = await Promise.all([
        apiRequest<Summary>('/dashboard/summary'),
        collectAllPages((page, size) => topicApi.list(new URLSearchParams({ page: String(page), size: String(size) }))),
        indicatorApi.nodes(),
      ]);
      const activeTopics = topicRows.filter(isBusinessTopic);
      const currentNode = latestEnabledNode(nodes);
      const [achievementRows, reportRows, progress] = await Promise.all([
        collectAllPages((page, size) => achievementApi.list({ page: String(page), size: String(size), pendingForMe: true })),
        collectAllPages((page, size) => reportApi.list(new URLSearchParams({ page: String(page), size: String(size), pendingForMe: 'true' }))),
        currentNode ? achievementApi.progress(currentNode.id) : Promise.resolve(undefined),
      ]);
      const rows = activeTopics.map((topic): TopicSummary => {
        const topicRows = progress?.rows.filter((row) => row.topicId === topic.id) ?? [];
        const topicScope = topicRows.some((row) => row.scope === 'TOPIC')
          ? topicRows.filter((row) => row.scope === 'TOPIC')
          : topicRows.filter((row) => row.scope === 'UNIT' && !row.historical);
        const planned = topicScope.reduce((sum, row) => sum + (row.targetQuantity ?? 0), 0);
        const completed = topicScope.reduce((sum, row) => sum + row.stages.effective, 0);
        const rate = planned > 0 ? Math.min(100, Math.round((completed / planned) * 100)) : 0;
        return { topicId: topic.id, topicCode: topic.code, topicName: topic.name, planned, completed, gap: Math.max(0, planned - completed), rate };
      });
      setSummary(summaryData);
      setTopics(activeTopics);
      setAchievements(achievementRows);
      setReports(reportRows);
      setTopicSummaries(rows);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '工作台加载失败');
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const topicNames = useMemo(() => new Map(topics.map((topic) => [topic.id, topic.name])), [topics]);
  const canInitialAchievement = user?.actionPermissions.includes('achievement.initial.approve');
  const canFinalAchievement = user?.actionPermissions.includes('achievement.final.approve');
  const canInitialReport = user?.actionPermissions.includes('report.initial.approve');
  const canFinalReport = user?.actionPermissions.includes('report.final.approve');
  const tasks: TaskRow[] = useMemo(() => [
    ...achievements
      .filter((item) => (canInitialAchievement && ['PRE_INITIAL', 'FORMAL_INITIAL', 'SUPPLEMENT_INITIAL'].includes(item.status))
        || (canFinalAchievement && ['PRE_FINAL', 'FORMAL_FINAL', 'SUPPLEMENT_FINAL'].includes(item.status)))
      .map((item) => ({ id: `achievement-${item.id}`, title: item.title, status: achievementStatusLabels[item.status] ?? item.status, type: '成果审批' })),
    ...reports
      .filter((item) => (canInitialReport && item.status === 'INITIAL_REVIEW') || (canFinalReport && item.status === 'FINAL_REVIEW'))
      .map((item) => ({ id: `report-${item.id}`, title: `${item.reportType === 'MONTHLY' ? '月报' : '季报'} · ${topicNames.get(item.topicId) ?? '未知课题'}`, status: reportStatusLabels[item.status], type: '进度报告' })),
  ], [achievements, reports, canInitialAchievement, canFinalAchievement, canInitialReport, canFinalReport, topicNames]);

  const effective = topicSummaries.reduce((sum, topic) => sum + topic.completed, 0);
  const metricCards = [
    { title: '课题数量', value: summary?.topicCount ?? topics.length, icon: <RiseOutlined />, color: '#1677ff' },
    { title: '已完成成果', value: effective, icon: <FileDoneOutlined />, color: '#00a870' },
    { title: '已通过报告', value: summary?.reportApproved ?? 0, icon: <CheckCircleOutlined />, color: '#7b61ff' },
    { title: '已提交材料项', value: summary?.archiveCompleted ?? 0, icon: <FolderOpenOutlined />, color: '#fa8c16' },
    { title: '当前待审批', value: tasks.length, icon: <ClockCircleOutlined />, color: '#eb2f96' },
  ];

  return <Spin spinning={loading}>
    {error && <Alert type="error" showIcon message={error} action={<a onClick={() => void load()}>重新加载</a>} style={{ marginBottom: 16 }} />}
    <Row gutter={[16, 16]}>
      {metricCards.map((item) => <Col flex="1 1 190px" key={item.title}><Card className="metric-card"><Space align="start"><div className="metric-icon" style={{ color: item.color, background: `${item.color}15` }}>{item.icon}</div><Statistic title={item.title} value={item.value} /></Space></Card></Col>)}
    </Row>
    <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
      <Col xs={24} xl={16} style={{ display: 'flex' }}><Card title="课题执行概览" extra={<Tag color="blue">真实业务数据</Tag>} style={{ width: '100%', height: '100%' }}>
        <Table rowKey="topicId" size="middle" pagination={false} dataSource={topicSummaries} columns={[
          { title: '课题', dataIndex: 'topicName', render: (value, row: TopicSummary) => <Space><Tag>{row.topicCode}</Tag><Text strong>{value}</Text></Space> },
          { title: '指标目标', dataIndex: 'planned', width: 100 }, { title: '已完成', dataIndex: 'completed', width: 90 },
          { title: '缺口', dataIndex: 'gap', width: 80, render: (value: number) => <Text type={value > 0 ? 'danger' : 'success'}>{value}</Text> },
          { title: '完成率', dataIndex: 'rate', width: 220, render: (value: number) => <Progress percent={value} size="small" status={value < 50 ? 'exception' : 'active'} /> },
        ]} />
      </Card></Col>
      <Col xs={24} xl={8} style={{ display: 'flex' }}><Card title="我的待办" extra={<WarningOutlined style={{ color: '#fa8c16' }} />} style={{ width: '100%', height: '100%' }}>
        <Table rowKey="id" size="small" pagination={false} dataSource={tasks} scroll={{ y: 280 }} locale={{ emptyText: <div className="empty-compact"><CheckCircleOutlined /><p>当前没有待办事项</p></div> }} columns={[
          { title: '待办事项', dataIndex: 'title', ellipsis: true }, { title: '业务类型', dataIndex: 'type', width: 90 },
          { title: '状态', dataIndex: 'status', width: 110, render: (value: string) => <StatusTag status={value} /> },
        ]} />
      </Card></Col>
    </Row>
  </Spin>;
}
