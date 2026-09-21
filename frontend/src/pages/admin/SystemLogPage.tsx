import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Descriptions, Form, Input, Row, Select, Space, Table, Tag, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { systemLogApi, type ApiSystemErrorLog } from '../../api/system-log-api';

interface Filters { severity?: string; statusCode?: string; username?: string; traceId?: string; from?: string; to?: string }
const { Text } = Typography;

export function SystemLogPage() {
  const [form] = Form.useForm<Filters>();
  const [rows, setRows] = useState<ApiSystemErrorLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState<Filters>({});

  const load = useCallback(async (nextPage: number, nextSize: number, next: Filters) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(nextPage), size: String(nextSize) });
      Object.entries(next).forEach(([key, value]) => { if (value?.trim()) params.set(key, value.trim()); });
      const result = await systemLogApi.list(params);
      setRows(result.items); setTotal(result.total);
    } catch (error) { message.error(error instanceof Error ? error.message : '日志加载失败'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(page, size, filters); }, [filters, load, page, size]);

  const search = () => { setPage(1); setFilters(form.getFieldsValue()); };
  const reset = () => { form.resetFields(); setPage(1); setFilters({}); };
  return <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Card title="系统错误日志" extra={<Button icon={<ReloadOutlined />} onClick={() => void load(page, size, filters)}>刷新</Button>}>
      <Form form={form} layout="vertical" onFinish={search}>
        <Row gutter={16}>
          <Col xs={24} md={6}><Form.Item label="级别" name="severity"><Select allowClear options={[{ value: 'ERROR', label: '错误' }, { value: 'WARN', label: '警告' }]} /></Form.Item></Col>
          <Col xs={24} md={6}><Form.Item label="HTTP 状态码" name="statusCode"><Input inputMode="numeric" placeholder="例如 500" /></Form.Item></Col>
          <Col xs={24} md={6}><Form.Item label="用户名" name="username"><Input allowClear /></Form.Item></Col>
          <Col xs={24} md={6}><Form.Item label="追踪号" name="traceId"><Input allowClear /></Form.Item></Col>
          <Col xs={24} md={6}><Form.Item label="开始时间" name="from"><Input type="datetime-local" /></Form.Item></Col>
          <Col xs={24} md={6}><Form.Item label="结束时间" name="to"><Input type="datetime-local" /></Form.Item></Col>
          <Col xs={24} md={12} style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end' }}><Space><Button onClick={reset}>重置</Button><Button type="primary" htmlType="submit">筛选</Button></Space></Col>
        </Row>
      </Form>
      <Table<ApiSystemErrorLog> rowKey="id" loading={loading} dataSource={rows} size="middle"
        pagination={{ current: page, pageSize: size, total, showSizeChanger: true, pageSizeOptions: [10, 20, 50], onChange: (p, s) => { setPage(p); setSize(s); } }}
        expandable={{ expandedRowRender: (row) => <Descriptions bordered size="small" column={1} items={[
          { key: 'message', label: '错误信息', children: row.errorMessage || '—' },
          { key: 'exception', label: '异常类型', children: row.exceptionClass || '—' },
          { key: 'stack', label: '堆栈摘要', children: <Text code style={{ whiteSpace: 'pre-wrap' }}>{row.stackSummary || '—'}</Text> },
          { key: 'agent', label: '客户端', children: row.userAgent || '—' },
        ]} /> }}
        columns={[
          { title: '时间', dataIndex: 'createdAt', width: 190, render: (value: string) => value.replace('T', ' ') },
          { title: '级别', dataIndex: 'severity', width: 90, render: (value: string) => <Tag color={value === 'ERROR' ? 'red' : 'orange'}>{value}</Tag> },
          { title: '用户', dataIndex: 'username', width: 140, render: (value?: string) => value || '匿名' },
          { title: '请求', key: 'request', ellipsis: true, render: (_, row) => `${row.httpMethod} ${row.requestPath}` },
          { title: '状态', dataIndex: 'statusCode', width: 80 },
          { title: '错误码', dataIndex: 'errorCode', width: 190, render: (value?: string) => value || '—' },
          { title: '耗时', dataIndex: 'durationMs', width: 100, render: (value: number) => `${value} ms` },
          { title: '追踪号', dataIndex: 'traceId', width: 220, ellipsis: true },
        ]} />
    </Card>
  </Space>;
}
