import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Drawer, Form, Input, InputNumber, message, Modal, Progress, Select, Space, Table, Tag } from 'antd';
import { authApi, type ApiCurrentUser } from '../../api/auth-api';
import { archiveApi, type ApiArchiveDirectory, type ApiArchiveFolder, type ApiArchiveProgress, type ApiSelfFundedProject, type SelfFundedWrite } from '../../api/archive-api';
import { fileApi, type ApiFile } from '../../api/file-api';
import { apiRequest } from '../../api/http-client';

interface Topic { id: string; code: string; name: string; enabled: boolean; status: string }
interface TopicPage { items: Topic[] }

function FolderFiles({ folder, editable, onChanged }: { folder: ApiArchiveFolder; editable: boolean; onChanged: () => void }) {
  const [files, setFiles] = useState<ApiFile[]>([]);
  const [busy, setBusy] = useState(false);
  const refresh = useCallback(async () => {
    try { setFiles(await archiveApi.files(folder.id)); }
    catch (error) { message.error(error instanceof Error ? error.message : '文件加载失败'); }
  }, [folder.id]);
  useEffect(() => { void refresh(); }, [refresh]);
  const upload = async (file: File) => {
    setBusy(true);
    try {
      const uploaded = await fileApi.upload(file);
      await archiveApi.attach(folder.id, uploaded.id);
      message.success('材料已上传并归档'); await refresh(); onChanged();
    } catch (error) { message.error(error instanceof Error ? error.message : '上传失败'); }
    finally { setBusy(false); }
  };
  const remove = async (file: ApiFile) => {
    try { await archiveApi.remove(folder.id, file.id); message.success('已移除关联'); await refresh(); onChanged(); }
    catch (error) { message.error(error instanceof Error ? error.message : '移除失败'); }
  };
  return <Card title={folder.name} extra={editable && <label className="ant-btn ant-btn-primary">
    {busy ? '上传中…' : '上传文件'}<input type="file" hidden disabled={busy} onChange={event => {
      const file = event.target.files?.[0]; if (file) void upload(file); event.target.value = '';
    }} /></label>}>
    <p>{folder.required ? '必存' : '有则必存'} · 至少 {folder.requiredQuantity} 份 · 当前 {files.length} 份</p>
    <Table rowKey="id" dataSource={files} pagination={false} columns={[
      { title: '文件名', dataIndex: 'originalName' }, { title: '大小', render: (_, file) => `${Math.ceil(file.size / 1024)} KB` },
      { title: '操作', render: (_, file) => <Space><Button type="link" onClick={() => void fileApi.download(file, true)}>预览</Button>
        <Button type="link" onClick={() => void fileApi.download(file)}>下载</Button>
        {editable && <Button type="link" danger onClick={() => void remove(file)}>移除</Button>}</Space> },
    ]} />
  </Card>;
}

export function RealNationalArchivePage() {
  const [user, setUser] = useState<ApiCurrentUser>();
  const [directories, setDirectories] = useState<ApiArchiveDirectory[]>([]);
  const [selected, setSelected] = useState<ApiArchiveDirectory>();
  const [folders, setFolders] = useState<ApiArchiveFolder[]>([]);
  const [folder, setFolder] = useState<ApiArchiveFolder>();
  const [name, setName] = useState('');
  const [required, setRequired] = useState(true);
  const [adding, setAdding] = useState(false);
  const refresh = useCallback(async () => {
    try { const [who, list] = await Promise.all([authApi.me(), archiveApi.directories()]); setUser(who); setDirectories(list); }
    catch (error) { message.error(error instanceof Error ? error.message : '归档目录加载失败'); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);
  const refreshFolders = useCallback(async (directory: ApiArchiveDirectory) => {
    try { setFolders(await archiveApi.nationalFolders(directory.topicId, directory.unitId)); await refresh(); }
    catch (error) { message.error(error instanceof Error ? error.message : '文件夹加载失败'); }
  }, [refresh]);
  const editable = Boolean(selected && user?.unitId === selected.unitId && user.actionPermissions.includes('archive.topic.submit'));
  const canManageFolders = Boolean(selected && user && (['SYSTEM_ADMIN', 'PROJECT_TECH_LEADER', 'RESEARCH_ASSISTANT'].includes(user.roleCode)
    || user.unitId === selected.unitId && user.actionPermissions.includes('archive.topic.submit')));
  return <>
    <Card title="课题国家材料"><Table rowKey={row => `${row.topicId}:${row.unitId}`} dataSource={directories} columns={[
      { title: '课题', dataIndex: 'topicName' }, { title: '单位', dataIndex: 'unitName' },
      { title: '已完成', render: (_, row) => `${row.completedCount}/${row.folderCount}` },
      { title: '完成率', render: (_, row) => <Progress percent={row.completionRate} /> },
      { title: '操作', render: (_, row) => <Button type="link" onClick={() => { setSelected(row); void refreshFolders(row); }}>查看材料</Button> },
    ]} /></Card>
    <Drawer width={850} title={selected ? `${selected.topicName} · ${selected.unitName}` : ''} open={Boolean(selected)} onClose={() => { setSelected(undefined); setFolder(undefined); }}
      extra={canManageFolders && <Button type="primary" onClick={() => setAdding(true)}>新建文件夹</Button>}>
      <Table rowKey="id" dataSource={folders} columns={[
        { title: '材料目录', dataIndex: 'name' },
        { title: '要求', render: (_, row) => <Tag color={row.required ? 'red' : 'gold'}>{row.required ? '必存' : '有则必存'}</Tag> },
        { title: '文件', render: (_, row) => `${row.fileCount}/${row.requiredQuantity}` },
        { title: '状态', render: (_, row) => <Tag color={row.completed ? 'green' : 'default'}>{row.completed ? '完成' : '待补充'}</Tag> },
        { title: '操作', render: (_, row) => <Space><Button type="link" onClick={() => setFolder(row)}>文件</Button>
          {row.canDelete && <Button type="link" danger onClick={() => void archiveApi.deleteFolder(row.id)
            .then(() => selected && refreshFolders(selected)).catch(error => message.error(error.message))}>删除</Button>}</Space> },
      ]} />
    </Drawer>
    <Drawer width={800} title={folder?.name} open={Boolean(folder)} onClose={() => setFolder(undefined)}>
      {folder && <FolderFiles folder={folder} editable={editable} onChanged={() => { if (selected) void refreshFolders(selected); }} />}
    </Drawer>
    <Modal title="新建归档文件夹" open={adding} onCancel={() => setAdding(false)} onOk={() => {
      if (!selected) return;
      void archiveApi.addNationalFolder(selected.topicId, selected.unitId, name, required).then(async () => {
        setAdding(false); setName(''); setRequired(true); await refreshFolders(selected); message.success('文件夹已创建');
      }).catch(error => message.error(error.message));
    }}><Space direction="vertical" style={{ width: '100%' }}>
      <Input value={name} onChange={event => setName(event.target.value)} maxLength={200} placeholder="文件夹名称" />
      <Select value={required} onChange={setRequired} options={[{ label: '必存', value: true }, { label: '有则必存', value: false }]} />
    </Space></Modal>
  </>;
}

export function RealSelfFundedPage() {
  const [user, setUser] = useState<ApiCurrentUser>();
  const [topics, setTopics] = useState<Topic[]>([]);
  const [projects, setProjects] = useState<ApiSelfFundedProject[]>([]);
  const [selected, setSelected] = useState<ApiSelfFundedProject>();
  const [folders, setFolders] = useState<ApiArchiveFolder[]>([]);
  const [folder, setFolder] = useState<ApiArchiveFolder>();
  const [editing, setEditing] = useState(false);
  const [form] = Form.useForm<SelfFundedWrite>();
  const refresh = useCallback(async () => {
    try {
      const [who, topicPage, list] = await Promise.all([authApi.me(), apiRequest<TopicPage>('/topics?page=1&size=200'), archiveApi.projects()]);
      setUser(who); setTopics(topicPage.items); setProjects(list);
    } catch (error) { message.error(error instanceof Error ? error.message : '自筹项目加载失败'); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);
  const open = async (project: ApiSelfFundedProject) => {
    setSelected(project);
    try { setFolders(await archiveApi.projectFolders(project.id)); }
    catch (error) { message.error(error instanceof Error ? error.message : '材料清单加载失败'); }
  };
  const save = async () => {
    try {
      const values = await form.validateFields();
      if (values.endDate < values.startDate) { message.warning('结束日期不能早于开始日期'); return; }
      if (selected && editing) await archiveApi.updateProject(selected.id, { ...values, recordVersion: selected.recordVersion });
      else await archiveApi.createProject(values);
      setEditing(false); setSelected(undefined); await refresh(); message.success('项目已保存');
    } catch (error) { message.error(error instanceof Error ? error.message : '保存失败'); }
  };
  const editable = Boolean(user?.roleCode === 'INTERNAL_TOPIC_UNIT' && user.actionPermissions.includes('self-funded.manage'));
  const editableTopics = topics.filter(t => t.enabled && t.status === 'ACTIVE' && user?.memberships.some(m => m.topicId === t.id && m.enabled));
  return <>
    <Card title="配套自筹项目" extra={editable && <Button type="primary" onClick={() => {
      setSelected(undefined); form.resetFields(); form.setFieldsValue({ topicId: editableTopics[0]?.id, projectType: 'TECHNOLOGY', status: '筹备中' }); setEditing(true);
    }}>新建项目</Button>}><Table rowKey="id" dataSource={projects} columns={[
      { title: '课题', render: (_, row) => topics.find(t => t.id === row.topicId)?.name ?? row.topicId },
      { title: '项目', dataIndex: 'name' }, { title: '编号', dataIndex: 'code' },
      { title: '类型', dataIndex: 'projectType' }, { title: '完成率', render: (_, row) => <Progress percent={row.completionRate} /> },
      { title: '操作', render: (_, row) => <Space><Button type="link" onClick={() => void open(row)}>材料</Button>
        {editable && user?.unitId === row.ownerUnitId && <Button type="link" onClick={() => { setSelected(row); form.setFieldsValue(row); setEditing(true); }}>编辑</Button>}</Space> },
    ]} /></Card>
    <Drawer width={850} title={selected?.name} open={Boolean(selected && !editing)} onClose={() => { setSelected(undefined); setFolder(undefined); }}>
      <Table rowKey="id" dataSource={folders} columns={[
        { title: '材料', dataIndex: 'name' }, { title: '要求', render: (_, row) => row.required ? '必存' : '有则必存' },
        { title: '文件数', dataIndex: 'fileCount' }, { title: '完成', render: (_, row) => row.completed ? '是' : '否' },
        { title: '操作', render: (_, row) => <Button type="link" onClick={() => setFolder(row)}>文件</Button> },
      ]} />
    </Drawer>
    <Drawer width={800} title={folder?.name} open={Boolean(folder)} onClose={() => setFolder(undefined)}>
      {folder && <FolderFiles folder={folder} editable={editable && user?.unitId === folder.unitId}
        onChanged={() => { if (selected) void open(selected); }} />}
    </Drawer>
    <Modal title={selected ? '编辑自筹项目' : '新建自筹项目'} open={editing} onCancel={() => setEditing(false)} onOk={() => void save()}>
      <Form form={form} layout="vertical"><Form.Item name="topicId" label="课题" rules={[{ required: true }]}><Select disabled={Boolean(selected)} options={editableTopics.map(t => ({ value: t.id, label: t.name }))} /></Form.Item>
        <Form.Item name="code" label="项目编号" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="name" label="项目名称" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="projectType" label="类型" rules={[{ required: true }]}><Select disabled={Boolean(selected)} options={[
          { value: 'TECHNOLOGY', label: '科技项目' }, { value: 'RENOVATION', label: '技改项目' }, { value: 'INFRASTRUCTURE', label: '基建项目' },
        ]} /></Form.Item>
        <Form.Item name="principalName" label="负责人" rules={[{ required: true }]}><Input /></Form.Item>
        <Space><Form.Item name="startDate" label="开始日期" rules={[{ required: true, message: '请选择开始日期' }]}><Input type="date" /></Form.Item><Form.Item name="endDate" label="结束日期" rules={[{ required: true, message: '请选择结束日期' }]}><Input type="date" /></Form.Item></Space>
        <Form.Item name="budget" label="预算（万元）"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
        <Form.Item name="status" label="状态"><Select options={['筹备中', '实施中', '验收中', '已完成'].map(value => ({ value, label: value }))} /></Form.Item>
      </Form>
    </Modal>
  </>;
}

export function RealArchiveMonitoringPage() {
  const [rows, setRows] = useState<ApiArchiveProgress[]>([]);
  useEffect(() => { void archiveApi.progress().then(setRows).catch(error => message.error(error.message)); }, []);
  return <Card title="归档进度监控"><Table rowKey={row => `${row.topicId}:${row.unitId}:${row.ownerType}`} dataSource={rows} columns={[
    { title: '课题 ID', dataIndex: 'topicId' }, { title: '单位 ID', dataIndex: 'unitId' },
    { title: '类型', render: (_, row) => row.ownerType === 'TOPIC_NATIONAL' ? '国家材料' : '自筹材料' },
    { title: '必存材料', render: (_, row) => `${row.completedCount}/${row.requiredCount}` },
    { title: '完成率', render: (_, row) => <Progress percent={row.completionRate} /> },
  ]} /></Card>;
}
