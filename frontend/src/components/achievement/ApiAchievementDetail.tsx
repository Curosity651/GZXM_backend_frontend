import { Button, Card, Descriptions, Empty, Space, Steps, Table, Tag, Timeline, Typography } from 'antd';
import type { ApiAchievement } from '../../api/achievement-api';
import type { ApiTopic } from '../../api/topic-api';
import type { ApiUnit } from '../../api/system-api';
import { fileApi } from '../../api/file-api';

const { Text } = Typography;
const typeNames: Record<ApiAchievement['achievementType'], string> = {
  PAPER: '学术论文', PATENT: '发明专利', COPYRIGHT: '软件著作权', STANDARD: '标准规范', TALENT: '人才培养',
};
const statusNames: Record<string, string> = {
  DRAFT: '预审草稿', PRE_INITIAL: '预审初审中', PRE_FINAL: '预审终审中', PRE_RETURNED: '预审退回', PRE_APPROVED: '允许投稿/申请',
  EXTERNAL_SUBMITTED: '已投稿/已申请', FORMAL_DRAFT: '正式成果草稿', FORMAL_INITIAL: '正式初审中', FORMAL_FINAL: '正式终审中', FORMAL_RETURNED: '正式退回',
  WAIT_PUBLICATION: '待见刊补充', WAIT_GRANT: '待授权补充', SUPPLEMENT_INITIAL: '补充初审中', SUPPLEMENT_FINAL: '补充终审中', SUPPLEMENT_RETURNED: '补充退回', EFFECTIVE: '已生效',
};

function stage(achievement: ApiAchievement) {
  const supplement = achievement.achievementType === 'PAPER' || achievement.achievementType === 'PATENT';
  const stages = supplement ? ['成果填报', '预审', '投稿/申请', '正式材料', '见刊/授权补充', '成果生效'] : ['成果填报', '预审', '正式材料', '成果生效'];
  const status = achievement.status;
  let current = 0;
  if (status === 'EFFECTIVE') current = stages.length - 1;
  else if (status.startsWith('SUPPLEMENT_') || status === 'WAIT_PUBLICATION' || status === 'WAIT_GRANT') current = 4;
  else if (status.startsWith('FORMAL_')) current = supplement ? 3 : 2;
  else if (status === 'EXTERNAL_SUBMITTED') current = 2;
  else if (status.startsWith('PRE_')) current = 1;
  return <Steps size="small" current={current} status={status.includes('RETURNED') ? 'error' : status === 'EFFECTIVE' ? 'finish' : 'process'} items={stages.map((title) => ({ title }))} />;
}

const value = (detail: Record<string, unknown>, key: string) => {
  const current = detail[key];
  if (typeof current === 'boolean') return current ? '是' : '否';
  return current === undefined || current === null || current === '' ? '—' : String(current);
};

export function ApiAchievementDetail({ achievement, topics, units }: { achievement: ApiAchievement; topics: ApiTopic[]; units: ApiUnit[] }) {
  const topic = topics.find((item) => item.id === achievement.topicId);
  const unit = units.find((item) => item.id === achievement.unitId);
  const detail = achievement.detail;
  const people = value(detail, achievement.achievementType === 'PAPER' ? 'allAuthors' : achievement.achievementType === 'PATENT' ? 'inventorList'
    : achievement.achievementType === 'COPYRIGHT' ? 'copyrightOwnerList' : achievement.achievementType === 'STANDARD' ? 'drafters' : 'studentName');
  const organization = value(detail, achievement.achievementType === 'PAPER' ? 'signingUnitList' : achievement.achievementType === 'PATENT' ? 'applicantList'
    : achievement.achievementType === 'COPYRIGHT' ? 'firstCopyrightOwner' : achievement.achievementType === 'STANDARD' ? 'participatingUnits' : 'trainingUnit');
  const specialItems = achievement.achievementType === 'PAPER' ? [
    { key: 'first-person', label: '第一作者', children: value(detail, 'firstAuthor') },
    { key: 'grid-first', label: '广西电网第一作者', children: value(detail, 'isPowerGridFirstAuthor') },
    { key: 'core', label: '中文核心期刊', children: value(detail, 'isChineseCoreJournal') },
    { key: 'paper-type', label: '收录类别', children: value(detail, 'paperType') },
  ] : achievement.achievementType === 'PATENT' ? [
    { key: 'first-person', label: '第一申请人', children: value(detail, 'firstApplicant') },
    { key: 'grid-first', label: '广西电网第一申请人', children: value(detail, 'isPowerGridFirstApplicant') },
  ] : achievement.achievementType === 'COPYRIGHT' ? [
    { key: 'first-person', label: '第一完成人', children: value(detail, 'firstCompleter') },
    { key: 'grid-first', label: '广西电网第一完成人', children: value(detail, 'isPowerGridFirstCompleter') },
  ] : [];
  const materialRows = achievement.materialLinks.filter((item) => item.active).map((link) => ({
    ...link, file: achievement.materials.find((item) => item.id === link.fileId),
  }));

  return <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Card size="small">{stage(achievement)}</Card>
    <Card size="small" title="成果信息"><Descriptions bordered size="small" column={2} items={[
      { key: 'title', label: '成果名称', children: achievement.title, span: 2 },
      { key: 'topic', label: '所属课题', children: topic ? `${topic.code} ${topic.name}` : achievement.topicId },
      { key: 'unit', label: '上传单位', children: unit?.name ?? achievement.unitId },
      { key: 'type', label: '成果类型', children: <Tag color="blue">{typeNames[achievement.achievementType]}</Tag> },
      { key: 'status', label: '当前状态', children: <Tag color={achievement.status === 'EFFECTIVE' ? 'green' : achievement.status.includes('RETURNED') ? 'red' : 'blue'}>{statusNames[achievement.status] ?? achievement.status}</Tag> },
      { key: 'version', label: '版本', children: <Space><Tag>记录 V{achievement.recordVersion}</Tag><Tag color="blue">提交 V{achievement.submittedVersion}</Tag></Space> },
      { key: 'owner', label: '负责人', children: achievement.responsiblePerson },
      { key: 'number', label: '投稿/申请编号', children: value(detail, achievement.achievementType === 'PAPER' ? 'externalSubmissionNumber' : achievement.achievementType === 'PATENT' ? 'applicationNumber' : 'registrationNumber') },
      ...specialItems,
      { key: 'people', label: '作者/完成人及排序', children: people, span: 2 },
      { key: 'units', label: '署名/申请单位', children: organization, span: 2 },
      { key: 'remark', label: '备注', children: value(detail, 'remarks'), span: 2 },
    ]} /></Card>
    <Card size="small" title="成果材料">{materialRows.length ? <Table size="small" rowKey="id" pagination={false} dataSource={materialRows} columns={[
      { title: '材料类型', dataIndex: 'materialType', width: 180 },
      { title: '文件', render: (_, row) => row.file?.originalName ?? row.fileId },
      { title: '状态', dataIndex: 'status', width: 100, render: (current) => <Tag>{current}</Tag> },
      { title: '上传时间', width: 150, render: (_, row) => row.file?.createdAt?.replace('T', ' ').slice(0, 16) ?? '—' },
      { title: '操作', width: 130, render: (_, row) => row.file && <Space><Button type="link" onClick={() => void fileApi.download(row.file!, true)}>查看</Button><Button type="link" onClick={() => void fileApi.download(row.file!)}>下载</Button></Space> },
    ]} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无附件材料" />}</Card>
    <Card size="small" title="审批记录">{achievement.approvals.length ? <Timeline items={[...achievement.approvals].reverse().map((record) => ({
      color: record.decision === 'APPROVED' ? 'green' : 'red',
      children: <div><Text strong>{record.level === 'INITIAL' ? '初审' : '终审'} · {record.decision === 'APPROVED' ? '通过' : '退回修改'}</Text><div><Text type="secondary">操作人 {record.operatorId} · {record.operatedAt.replace('T', ' ').slice(0, 16)}</Text></div><div>{record.opinion || '无审批意见'}</div></div>,
    }))} /> : <Text type="secondary">暂无审批记录</Text>}</Card>
  </Space>;
}
