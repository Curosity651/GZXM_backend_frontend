import { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic, Alert } from 'antd';
import { apiRequest } from '../api/http-client';

interface Summary {
  topicCount: number; reportTotal: number; reportApproved: number; reportOverdue: number;
  archiveRequired: number; archiveCompleted: number; pendingReports: number;
}

export function RealHomePage() {
  const [data, setData] = useState<Summary>();
  const [error, setError] = useState<string>();
  useEffect(() => {
    void apiRequest<Summary>('/dashboard/summary').then(setData)
      .catch(reason => setError(reason instanceof Error ? reason.message : '工作台加载失败'));
  }, []);
  return <>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    <Row gutter={[16, 16]}>{[
      ['课题数量', data?.topicCount], ['报告总数', data?.reportTotal], ['已通过报告', data?.reportApproved],
      ['逾期报告', data?.reportOverdue], ['待审批报告', data?.pendingReports],
      ['必存材料项', data?.archiveRequired], ['已完成材料项', data?.archiveCompleted],
    ].map(([title, value]) => <Col xs={12} md={8} xl={6} key={String(title)}><Card><Statistic title={title} value={value ?? '—'} /></Card></Col>)}</Row>
  </>;
}
