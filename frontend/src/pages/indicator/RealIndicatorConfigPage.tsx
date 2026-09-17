import { useCallback, useState } from 'react';
import { Alert, Button, Card, Input, Space, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { ApiCurrentUser } from '../../api/auth-api';
import { researchApi } from '../../api/research/client';
import type { Topic } from '../../api/research/contracts';
import { managesTopics } from '../achievement/real-permissions';
import { ResearchSession } from './ResearchSession';
import { topicStatuses, useResearchLoad } from './research-hooks';

export function RealIndicatorConfigPage() {
  return <ResearchSession page="topic-indicator">{user => <TopicList user={user} />}</ResearchSession>;
}
function TopicList({ user }: { user: ApiCurrentUser }) {
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const load = useResearchLoad(useCallback(() => researchApi.topics({ page, size: 20, keyword }), [page, keyword]));
  return <Card title="课题列表" extra={<Space><Button onClick={load.refresh}>刷新</Button>{managesTopics(user) && <Button type="primary" onClick={() => navigate('/indicator/topic/new')}>新建课题</Button>}</Space>}>
    <Input.Search aria-label="查询课题" placeholder="按课题名称或编码查询" allowClear onSearch={value => { setPage(1); setKeyword(value); }} style={{ maxWidth: 360, marginBottom: 16 }} />
    {load.error && <Alert type="error" showIcon title={load.error} />}
    <Table<Topic> rowKey="id" loading={load.loading} dataSource={load.data?.items ?? []} pagination={{ current: page, pageSize: 20, total: load.data?.total ?? 0, showSizeChanger: false, onChange: setPage }} columns={[
      { title: '编码', dataIndex: 'code' }, { title: '课题名称', dataIndex: 'name' },
      { title: '牵头单位', render: (_, row) => row.members?.find(member => member.unitId === row.leadUnitId)?.unitName ?? row.leadUnitId },
      { title: '状态', render: (_, row) => <Tag>{row.enabled ? topicStatuses[row.status] : '已停用'}</Tag> },
      { title: '操作', render: (_, row) => <Button onClick={() => navigate(`/indicator/topic/${encodeURIComponent(row.id)}`)}>查看 / 配置</Button> },
    ]} />
  </Card>;
}
