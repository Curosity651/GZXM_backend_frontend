import { useCallback } from 'react';
import { Alert, Button, Card, Col, Row, Statistic, Table, Tabs } from 'antd';
import { researchApi } from '../../api/research/client';
import type { AchievementProgressRow, IndicatorDefinition, Topic, Unit } from '../../api/research/contracts';
import { useResearchLoad } from '../indicator/research-hooks';

const achievementTypes: Record<string, string> = { PAPER: '论文', PATENT: '专利', COPYRIGHT: '软著', STANDARD: '标准', TALENT: '人才' };
export function RealAchievementProgress({ nodeId, topicId, unitId, topics, units, definitions, revision }: { nodeId: string; topicId?: string; unitId?: string; topics: Topic[]; units: Unit[]; definitions: IndicatorDefinition[]; revision: number }) {
  const load = useResearchLoad(useCallback(() => researchApi.progress(nodeId, topicId, unitId), [nodeId, topicId, unitId]), revision);
  const columns = [
    { title: '课题', render: (_: unknown, row: AchievementProgressRow) => topics.find(topic => topic.id === row.topicId)?.name ?? row.topicId },
    { title: '范围', render: (_: unknown, row: AchievementProgressRow) => row.scope === 'TOPIC' ? '课题总体' : units.find(unit => unit.id === row.unitId)?.name ?? row.unitId },
    { title: '指标', render: (_: unknown, row: AchievementProgressRow) => definitions.find(definition => definition.id === row.indicatorDefinitionId)?.name ?? row.indicatorDefinitionId },
    { title: '目标', render: (_: unknown, row: AchievementProgressRow) => row.targetPublished ? row.targetQuantity : '未下发' },
    ...Object.entries({ initiated: '发起', preApproved: '预审通过', external: '已投递', formal: '正式提交', supplement: '补充提交', effective: '已生效' }).map(([key, title]) => ({ title, render: (_: unknown, row: AchievementProgressRow) => row.stages[key as keyof typeof row.stages] })),
    { title: '完成率', render: (_: unknown, row: AchievementProgressRow) => row.completionRate === null ? (row.targetPublished ? '无正目标' : '未下发') : `${row.completionRate.toFixed(2)}%` },
  ];
  const table = (rows: AchievementProgressRow[]) => <Table rowKey={row => `${row.scope}:${row.topicId}:${row.unitId}:${row.indicatorDefinitionId}`} dataSource={rows} columns={columns} scroll={{ x: 1100 }} pagination={{ pageSize: 10 }} />;
  return <Card title="成果累计进度" loading={load.loading} extra={<Button onClick={load.refresh}>刷新统计</Button>}>
    {load.error && <Alert type="error" title={load.error} />}
    {load.data && <>
      <Alert type="info" title="按所选节点及此前节点累计当前事实；不是截至节点日期的历史报表。课题、单位、专项各为不同视角，不能相加。" />
      <Row gutter={16} style={{ margin: '16px 0' }}>{Object.entries(load.data.baseTotals).map(([type, value]) => <Col key={type}><Statistic title={`${achievementTypes[type]}生效`} value={value} /></Col>)}</Row>
      <Tabs items={[
        { key: 'current', label: '当前有效成员', children: table(load.data.rows.filter(row => !row.historical)) },
        { key: 'special', label: '专项子集', children: table(load.data.specialIndicators.filter(row => !row.historical)) },
        ...(load.data.rows.some(row => row.historical) || load.data.specialIndicators.some(row => row.historical) ? [{ key: 'history', label: '停用历史（不计当前汇总）', children: table([...load.data.rows, ...load.data.specialIndicators].filter(row => row.historical)) }] : []),
      ]} />
    </>}
  </Card>;
}
