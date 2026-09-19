import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Checkbox, Col, Form, Input, Modal, Row, Select, Space, Switch, Table, Tag, Tooltip, Typography, message } from 'antd';
import { DownOutlined, EditOutlined, LockOutlined, ReloadOutlined, SearchOutlined, UpOutlined } from '@ant-design/icons';
import { systemApi, type ApiPermission, type ApiRole } from '../../api/system-api';

interface RoleForm { pagePermissions: string[]; actionPermissions: string[]; enabled: boolean }
interface RoleFilters { name?: string; enabled?: boolean; description?: string }

const groupOptions = (items: ApiPermission[]) => items.reduce<Record<string, ApiPermission[]>>((groups, item) => {
  (groups[item.group] ??= []).push(item); return groups;
}, {});
const FIXED_ROLE_CODES = ['SYSTEM_ADMIN', 'PROJECT_TECH_LEADER', 'RESEARCH_ASSISTANT', 'INTERNAL_TOPIC_UNIT', 'EXTERNAL_TOPIC_UNIT'];

export function RolePermissionPage() {
  const [editForm] = Form.useForm<RoleForm>();
  const [filterForm] = Form.useForm<RoleFilters>();
  const [roles, setRoles] = useState<ApiRole[]>([]);
  const [permissions, setPermissions] = useState<ApiPermission[]>([]);
  const [filters, setFilters] = useState<RoleFilters>({});
  const [expanded, setExpanded] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ApiRole | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try { const [roleRows, permissionRows] = await Promise.all([systemApi.roles(), systemApi.permissions()]); setRoles(roleRows); setPermissions(permissionRows); }
    catch (error) { message.error(error instanceof Error ? error.message : '角色权限加载失败'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const filteredRoles = useMemo(() => roles.filter((role) => FIXED_ROLE_CODES.includes(role.code)).filter((role) => {
    const name = filters.name?.trim().toLowerCase(); const description = filters.description?.trim().toLowerCase();
    return (!name || role.name.toLowerCase().includes(name))
      && (filters.enabled === undefined || role.enabled === filters.enabled)
      && (!description || (role.description ?? '').toLowerCase().includes(description));
  }), [filters, roles]);
  const groupedPages = groupOptions(permissions.filter((item) => item.type === 'PAGE'));
  const groupedActions = groupOptions(permissions.filter((item) => item.type === 'ACTION'));

  const openForm = (role: ApiRole) => {
    if (role.code === 'SYSTEM_ADMIN') return;
    setEditing(role);
    editForm.setFieldsValue({ pagePermissions: role.pagePermissions, actionPermissions: role.actionPermissions, enabled: role.enabled });
    setOpen(true);
  };
  const save = async () => {
    if (!editing) return;
    const values = await editForm.validateFields(); setSaving(true);
    try {
      await systemApi.updateRole(editing.id, values);
      message.success('角色权限已更新'); setOpen(false); editForm.resetFields(); await load();
    } catch (error) { message.error(error instanceof Error ? error.message : '角色权限保存失败'); }
    finally { setSaving(false); }
  };
  const resetFilters = () => { filterForm.resetFields(); setFilters({}); };
  const permissionLocked = (permission: ApiPermission) => editing?.code === 'EXTERNAL_TOPIC_UNIT' && permission.lockedForExternal;

  return <div className="role-management-page">
    <Card className="role-filter-card"><Form form={filterForm} colon={false} onFinish={setFilters}><div className="role-filter-grid">
      <Form.Item label="角色名称" name="name"><Input allowClear placeholder="请输入角色名称" /></Form.Item>
      <Form.Item label="角色状态" name="enabled"><Select allowClear placeholder="请选择状态" options={[{ label: '启用', value: true }, { label: '停用', value: false }]} /></Form.Item>
      {expanded && <Form.Item label="角色说明" name="description"><Input allowClear placeholder="请输入说明关键词" /></Form.Item>}
      <div className="role-filter-actions"><Space><Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查询</Button><Button onClick={resetFilters}>重置</Button><Button type="link" onClick={() => setExpanded((value) => !value)} icon={expanded ? <UpOutlined /> : <DownOutlined />} iconPosition="end">{expanded ? '收起' : '展开'}</Button></Space></div>
    </div></Form></Card>
    <Card className="role-list-card"><div className="role-list-toolbar"><div><Typography.Title level={4}>角色列表</Typography.Title><Typography.Text type="secondary">共 {filteredRoles.length} 个角色</Typography.Text></div><Tooltip title="刷新列表"><Button icon={<ReloadOutlined />} onClick={() => void load()} /></Tooltip></div>
      <Table loading={loading} rowKey="id" dataSource={filteredRoles} pagination={false} scroll={{ x: 940 }} columns={[
        { title: '角色名称', dataIndex: 'name', width: 190, fixed: 'left', render: (value, role) => <Space><Tag color={role.code === 'SYSTEM_ADMIN' ? 'purple' : 'blue'}>{value}</Tag>{role.builtIn && <Tag>预置</Tag>}</Space> },
        { title: '角色说明', dataIndex: 'description', width: 360 },
        { title: '页面权限', dataIndex: 'pagePermissions', width: 120, render: (value: string[]) => `${value.length} 项` },
        { title: '操作权限', dataIndex: 'actionPermissions', width: 120, render: (value: string[]) => `${value.length} 项` },
        { title: '角色状态', dataIndex: 'enabled', width: 100, render: (enabled) => <Tag color={enabled ? 'green' : 'default'}>{enabled ? '启用' : '停用'}</Tag> },
        { title: '操作', width: 150, fixed: 'right', render: (_, role) => role.code === 'SYSTEM_ADMIN' ? <Space><LockOutlined /><Typography.Text type="secondary">系统角色</Typography.Text></Space> : <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openForm(role)}>编辑权限</Button> },
      ]} />
    </Card>
    <Modal title={`编辑角色权限${editing ? ` · ${editing.name}` : ''}`} open={open} width={900} onCancel={() => { setOpen(false); editForm.resetFields(); }} onOk={() => void save()} confirmLoading={saving}>
      <Form form={editForm} layout="vertical">
        <Row gutter={16}><Col span={10}><Form.Item label="角色名称"><Input disabled value={editing?.name} /></Form.Item></Col><Col span={10}><Form.Item label="角色说明"><Input disabled value={editing?.description} /></Form.Item></Col><Col span={4}><Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item></Col></Row>
        <Form.Item name="pagePermissions" label="页面权限" rules={[{ required: true, message: '请至少选择一个页面' }]}><Checkbox.Group className="permission-checkbox-group"><div className="permission-group-grid">{Object.entries(groupedPages).map(([group, items]) => <Card className="permission-group-card" size="small" title={group} key={group}><div className="permission-option-list">{items.map((item) => <Checkbox key={item.code} value={item.code} disabled={permissionLocked(item)}>{item.name}{permissionLocked(item) ? '（外部单位禁止）' : ''}</Checkbox>)}</div></Card>)}</div></Checkbox.Group></Form.Item>
        <Form.Item name="actionPermissions" label="操作权限"><Checkbox.Group className="permission-checkbox-group"><div className="permission-group-grid">{Object.entries(groupedActions).map(([group, items]) => <Card className="permission-group-card" size="small" title={group} key={group}><div className="permission-option-list">{items.map((item) => <Checkbox key={item.code} value={item.code} disabled={permissionLocked(item)}>{item.name}{permissionLocked(item) ? '（外部单位禁止）' : ''}</Checkbox>)}</div></Card>)}</div></Checkbox.Group></Form.Item>
      </Form>
    </Modal>
  </div>;
}
