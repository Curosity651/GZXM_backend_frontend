import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Progress, Row, Select, Space, Table, Tag, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { archiveApi, type ApiArchiveDirectory, type ApiArchiveProgress, type ApiSelfFundedProject } from '../../api/archive-api';
import { isBusinessTopic, topicApi, type ApiTopic } from '../../api/topic-api';

export function ArchiveMonitoringPage() {
  const [topics, setTopics] = useState<ApiTopic[]>([]);
  const [directories, setDirectories] = useState<ApiArchiveDirectory[]>([]);
  const [projects, setProjects] = useState<ApiSelfFundedProject[]>([]);
  const [rows, setRows] = useState<ApiArchiveProgress[]>([]);
  const [topicId, setTopicId] = useState<string>();
  const [unitId, setUnitId] = useState<string>();
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [topicPage, directoryRows, projectRows, progressRows] = await Promise.all([
        topicApi.list(), archiveApi.directories(), archiveApi.projects(), archiveApi.progress(),
      ]);
      setTopics(topicPage.items.filter(isBusinessTopic)); setDirectories(directoryRows); setProjects(projectRows); setRows(progressRows);
    } catch (error) { message.error(error instanceof Error ? error.message : '归档进度加载失败'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const topicMap = useMemo(() => new Map(topics.map((topic) => [topic.id, topic])), [topics]);
  const unitMap = useMemo(() => new Map(directories.map((item) => [item.unitId, item.unitName])), [directories]);
  const unitOptions = useMemo(() => [...unitMap.entries()].map(([value, label]) => ({ value, label })), [unitMap]);
  const nationalRows = rows.filter((row) => row.ownerType === 'TOPIC_NATIONAL' && (!topicId || row.topicId === topicId) && (!unitId || row.unitId === unitId));
  const selfFundedProgress = rows.filter((row) => row.ownerType === 'SELF_FUNDED' && (!topicId || row.topicId === topicId) && (!unitId || row.unitId === unitId));
  const projectRows = projects.filter((project) => (!topicId || project.topicId === topicId) && (!unitId || project.ownerUnitId === unitId)).map((project) => {
    const progress = selfFundedProgress.find((item) => item.topicId === project.topicId && item.unitId === project.ownerUnitId);
    return { ...project, requiredCount: progress?.requiredCount ?? 0, completedCount: progress?.completedCount ?? 0, rate: project.completionRate ?? progress?.completionRate ?? 0 };
  });
  const nationalRequired = nationalRows.reduce((sum, row) => sum + row.requiredCount, 0);
  const nationalCompleted = nationalRows.reduce((sum, row) => sum + row.completedCount, 0);
  const projectRequired = projectRows.reduce((sum, row) => sum + row.requiredCount, 0);
  const projectCompleted = projectRows.reduce((sum, row) => sum + row.completedCount, 0);

  return <>
    <Card style={{ marginBottom: 16 }}><Space wrap size={18}>
      <b>课题</b><Select allowClear placeholder="全部课题" style={{ width: 360 }} value={topicId} onChange={setTopicId} options={topics.map((topic) => ({ value: topic.id, label: `${topic.code} ${topic.name}` }))} />
      <b>提交单位</b><Select allowClear placeholder="全部单位" style={{ width: 320 }} value={unitId} onChange={setUnitId} options={unitOptions} />
      <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>刷新</Button>
    </Space></Card>
    <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
      <Col xs={24} md={12}><Card title="课题国家材料"><h1>{nationalRows.length}</h1><span>个单位清单纳入监控　已完成 {nationalCompleted}/{nationalRequired} 项</span></Card></Col>
      <Col xs={24} md={12}><Card title="配套自筹项目"><h1>{projectRows.length}</h1><span>个项目纳入归档　已完成 {projectCompleted}/{projectRequired} 项</span></Card></Col>
    </Row>
    <Card title="课题国家材料" style={{ marginBottom: 16 }}><Table loading={loading} rowKey={(row) => `${row.topicId}:${row.unitId}`} pagination={false} dataSource={nationalRows} columns={[
      { title: '课题', dataIndex: 'topicId', render: (value: string) => { const topic = topicMap.get(value); return <Space><Tag>{topic?.code ?? value}</Tag>{topic?.name ?? value}</Space>; } },
      { title: '提交单位', dataIndex: 'unitId', render: (value: string) => unitMap.get(value) ?? value },
      { title: '必存材料', render: (_: unknown, row) => `${row.completedCount}/${row.requiredCount}` },
      { title: '完成率', render: (_: unknown, row) => <Progress percent={row.completionRate} status={row.completionRate < 50 ? 'exception' : 'active'} /> },
    ]} /></Card>
    <Card title="配套自筹项目归档"><Table loading={loading} rowKey="id" pagination={false} dataSource={projectRows} columns={[
      { title: '所属课题', dataIndex: 'topicId', render: (value: string) => topicMap.get(value)?.name ?? value },
      { title: '归属单位', dataIndex: 'ownerUnitId', render: (value: string) => unitMap.get(value) ?? value },
      { title: '项目名称', dataIndex: 'name' },
      { title: '类型', dataIndex: 'projectType', render: (value: string) => <Tag color="purple">{value === 'TECHNOLOGY' ? '科技项目' : value === 'RENOVATION' ? '技改项目' : '基建项目'}</Tag> },
      { title: '必存材料', render: (_: unknown, row) => `${row.completedCount}/${row.requiredCount}` },
      { title: '完成率', render: (_: unknown, row) => <Progress percent={row.rate} status={row.rate < 50 ? 'exception' : 'active'} /> },
    ]} /></Card>
  </>;
}
