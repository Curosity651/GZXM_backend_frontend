import { useState } from 'react';
import { Card, Input, Select, Space, Table } from 'antd';
import { useAppStore } from '../../store';

const { Option } = Select;

export function MaterialQueryPage() {
  const { archiveCategories, archiveMaterials, achievements, units } = useAppStore();
  const [filter, setFilter] = useState({ categoryId: '', keyword: '', uploader: '' });

  const categoryMap = Object.fromEntries(archiveCategories.map((c) => [c.id, c]));
  const achievementMap = Object.fromEntries(achievements.map((a) => [a.id, a]));
  const unitMap = Object.fromEntries(units.map((u) => [u.id, u.name]));

  const filtered = archiveMaterials.filter((m) => {
    return (
      (!filter.categoryId || m.categoryId === filter.categoryId) &&
      (!filter.keyword || m.name.includes(filter.keyword) || m.fileName.includes(filter.keyword)) &&
      (!filter.uploader || m.uploader.includes(filter.uploader))
    );
  });

  const columns = [
    { title: '材料名称', dataIndex: 'name', key: 'name' },
    {
      title: '归档目录',
      dataIndex: 'categoryId',
      key: 'categoryId',
      render: (v: string) => categoryMap[v]?.name || v,
    },
    { title: '文件名', dataIndex: 'fileName', key: 'fileName' },
    {
      title: '要求ID',
      dataIndex: 'requirementId',
      key: 'requirementId',
      render: (v?: string) => v || '-',
    },
    {
      title: '关联成果',
      dataIndex: 'sourceAchievementId',
      key: 'sourceAchievementId',
      render: (v?: string) => {
        if (!v) return '-';
        const ach = achievementMap[v];
        if (!ach) return v;
        return `${ach.title}（${unitMap[ach.unitId] || ach.unitId}）`;
      },
    },
    { title: '上传人', dataIndex: 'uploader', key: 'uploader' },
    { title: '上传时间', dataIndex: 'uploadedAt', key: 'uploadedAt' },
  ];

  return (
    <Card title="材料查询">
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="归档目录"
          allowClear
          style={{ width: 180 }}
          onChange={(v) => setFilter({ ...filter, categoryId: v })}
        >
          {archiveCategories.map((c) => (
            <Option key={c.id} value={c.id}>{c.name}</Option>
          ))}
        </Select>
        <Input
          placeholder="材料名称/文件名"
          style={{ width: 200 }}
          value={filter.keyword}
          onChange={(e) => setFilter({ ...filter, keyword: e.target.value })}
        />
        <Input
          placeholder="上传人"
          style={{ width: 140 }}
          value={filter.uploader}
          onChange={(e) => setFilter({ ...filter, uploader: e.target.value })}
        />
      </Space>

      <Table rowKey="id" columns={columns} dataSource={filtered} pagination={{ pageSize: 10 }} />
    </Card>
  );
}
