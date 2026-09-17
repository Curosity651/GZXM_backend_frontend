import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Form, Input, Modal, Popconfirm, Row, Select, Space, Switch, Table, Tag, Tooltip, Typography, message } from 'antd';
import { DownOutlined, EditOutlined, KeyOutlined, PlusOutlined, ReloadOutlined, SearchOutlined, UpOutlined } from '@ant-design/icons';
import { systemApi, type ApiRole, type ApiUser } from '../../api/system-api';
import { useSessionStore } from '../../store/session';

interface UserFilters { username?: string; roleId?: string; contactName?: string; enabled?: boolean; phone?: string; email?: string }
interface UserForm { username: string; roleId: string; name: string; phone?: string; email?: string; enabled?: boolean }

export function UserManagementPage() {
  const currentUser = useSessionStore((state) => state.user);
  const [editForm] = Form.useForm<UserForm>();
  const [filterForm] = Form.useForm<UserFilters>();
  const [filters, setFilters] = useState<UserFilters>({});
  const [users, setUsers] = useState<ApiUser[]>([]);
  const [roles, setRoles] = useState<ApiRole[]>([]);
  const [expanded, setExpanded] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ApiUser | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async (next: UserFilters) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: '1', size: '200' });
      if (next.username?.trim()) params.set('keyword', next.username.trim());
      if (next.roleId) params.set('roleId', next.roleId);
      if (next.enabled !== undefined) params.set('enabled', String(next.enabled));
      const [page, roleRows] = await Promise.all([systemApi.users(params), systemApi.roles()]);
      setUsers(page.items); setRoles(roleRows);
    } catch (error) { message.error(error instanceof Error ? error.message : '用户列表加载失败'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load({}); }, [load]);

  const visibleUsers = useMemo(() => users.filter((user) => {
    const contact = filters.contactName?.trim().toLowerCase();
    const phone = filters.phone?.trim();
    const email = filters.email?.trim().toLowerCase();
    return (!contact || user.name.toLowerCase().includes(contact))
      && (!phone || user.phone?.includes(phone))
      && (!email || user.email?.toLowerCase().includes(email));
  }), [filters, users]);
  const activeRoles = roles.filter((role) => role.enabled || role.id === editing?.roleId);

  const openForm = (user?: ApiUser) => {
    setEditing(user ?? null);
    editForm.setFieldsValue(user ? { ...user, roleId: user.roleId! } : { enabled: true, roleId: activeRoles[0]?.id });
    setOpen(true);
  };
  const showPassword = (title: string, password: string) => Modal.success({
    title,
    content: <><Typography.Paragraph>临时密码仅显示本次，请安全告知用户。</Typography.Paragraph><Typography.Text code copyable>{password}</Typography.Text></>,
  });
  const save = async () => {
    const values = await editForm.validateFields(); setSaving(true);
    try {
      if (editing) {
        await systemApi.updateUser(editing.id, { username: values.username.trim(), name: values.name.trim(), phone: values.phone, email: values.email });
        if (values.enabled !== undefined && values.enabled !== editing.enabled) await systemApi.setUserStatus(editing.id, values.enabled);
        message.success('账号信息已更新');
      } else {
        const result = await systemApi.createUser({ username: values.username.trim(), roleId: values.roleId, name: values.name.trim(), phone: values.phone, email: values.email, enabled: true });
        showPassword('账号已创建', result.temporaryPassword);
      }
      setOpen(false); editForm.resetFields(); await load(filters);
    } catch (error) { message.error(error instanceof Error ? error.message : '保存失败'); }
    finally { setSaving(false); }
  };
  const changeStatus = async (user: ApiUser, enabled: boolean) => {
    try { await systemApi.setUserStatus(user.id, enabled); message.success(enabled ? '账号已启用' : '账号已停用'); await load(filters); }
    catch (error) { message.error(error instanceof Error ? error.message : '状态修改失败'); }
  };
  const resetPassword = async (user: ApiUser) => {
    try { const result = await systemApi.resetPassword(user.id); showPassword(`已重置 ${user.username} 的密码`, result.temporaryPassword); }
    catch (error) { message.error(error instanceof Error ? error.message : '密码重置失败'); }
  };
  const search = (values: UserFilters) => { setFilters(values); void load(values); };
  const resetFilters = () => { filterForm.resetFields(); setFilters({}); void load({}); };

  return <div className="user-management-page">
    <Card className="user-filter-card"><Form form={filterForm} colon={false} onFinish={search}><div className="user-filter-grid">
      <Form.Item label="用户名" name="username"><Input allowClear placeholder="请输入用户名或姓名" /></Form.Item>
      <Form.Item label="角色" name="roleId"><Select allowClear placeholder="请选择角色" options={roles.map((role) => ({ label: role.name, value: role.id }))} /></Form.Item>
      <Form.Item label="联系人" name="contactName"><Input allowClear placeholder="请输入联系人姓名" /></Form.Item>
      <Form.Item label="状态" name="enabled"><Select allowClear placeholder="请选择状态" options={[{ label: '启用', value: true }, { label: '停用', value: false }]} /></Form.Item>
      {expanded && <><Form.Item label="手机号" name="phone"><Input allowClear /></Form.Item><Form.Item label="邮箱" name="email"><Input allowClear /></Form.Item></>}
      <div className="user-filter-actions"><Space><Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查询</Button><Button onClick={resetFilters}>重置</Button><Button type="link" onClick={() => setExpanded((value) => !value)} icon={expanded ? <UpOutlined /> : <DownOutlined />} iconPosition="end">{expanded ? '收起' : '展开'}</Button></Space></div>
    </div></Form></Card>
    <Card className="user-list-card"><div className="user-list-toolbar"><div><Typography.Title level={4}>用户列表</Typography.Title><Typography.Text type="secondary">共 {visibleUsers.length} 个用户</Typography.Text></div><Space><Button type="primary" icon={<PlusOutlined />} onClick={() => openForm()}>新建用户</Button><Tooltip title="刷新列表"><Button icon={<ReloadOutlined />} onClick={() => void load(filters)} /></Tooltip></Space></div>
      <Table loading={loading} rowKey="id" dataSource={visibleUsers} pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }} scroll={{ x: 1020 }} columns={[
        { title: '用户名', dataIndex: 'username', width: 180, fixed: 'left' },
        { title: '角色', dataIndex: 'roleName', width: 160, render: (value) => <Tag color="blue">{value ?? '未分配'}</Tag> },
        { title: '单位联系人姓名', dataIndex: 'name', width: 160 },
        { title: '手机号', dataIndex: 'phone', width: 130, render: (value) => value || '—' },
        { title: '邮箱', dataIndex: 'email', width: 210, render: (value) => value || '—' },
        { title: '状态', dataIndex: 'enabled', width: 90, render: (value, user) => <Switch checked={value} disabled={user.id === currentUser?.id} onChange={(checked) => void changeStatus(user, checked)} /> },
        { title: '操作', width: 220, fixed: 'right', render: (_, user) => <Space><Button type="link" size="small" icon={<EditOutlined />} onClick={() => openForm(user)}>编辑</Button><Popconfirm title="确认重置该账号密码？" onConfirm={() => void resetPassword(user)}><Button type="link" size="small" icon={<KeyOutlined />}>重置密码</Button></Popconfirm></Space> },
      ]} />
    </Card>
    <Modal title={editing ? '编辑用户' : '新增用户'} open={open} onCancel={() => { setOpen(false); editForm.resetFields(); }} onOk={() => void save()} confirmLoading={saving} width={720}>
      <Form form={editForm} layout="vertical"><Row gutter={16}><Col span={12}><Form.Item label="用户名（课题单位账号同时作为单位名称）" name="username" rules={[{ required: true, message: '请输入用户名' }]}><Input /></Form.Item></Col><Col span={12}><Form.Item label="角色" name="roleId" rules={[{ required: true, message: '请选择角色' }]}><Select disabled={Boolean(editing)} options={activeRoles.map((role) => ({ label: role.name, value: role.id }))} /></Form.Item></Col></Row>
        <Form.Item label="单位联系人姓名" name="name" rules={[{ required: true, message: '请输入单位联系人姓名' }]}><Input /></Form.Item>
        <Row gutter={16}><Col span={12}><Form.Item label="手机号" name="phone"><Input /></Form.Item></Col><Col span={12}><Form.Item label="邮箱" name="email" rules={[{ type: 'email', message: '请输入正确的邮箱地址' }]}><Input /></Form.Item></Col></Row>
        {editing && <Form.Item label="账号状态" name="enabled" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用" disabled={editing.id === currentUser?.id} /></Form.Item>}
      </Form>
    </Modal>
  </div>;
}
