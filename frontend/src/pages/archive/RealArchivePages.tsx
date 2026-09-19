import { useCallback, useEffect, useState, type Key } from 'react';
import { Alert, Button, Card, Col, Drawer, Empty, Form, Input, InputNumber, message, Modal, Progress, Row, Select, Space, Table, Tag, Upload } from 'antd';
import { DeleteOutlined, DownloadOutlined, EyeOutlined, FileAddOutlined, FileOutlined, FolderOpenOutlined, ReloadOutlined, TeamOutlined, UploadOutlined } from '@ant-design/icons';
import { authApi, type ApiCurrentUser } from '../../api/auth-api';
import { archiveApi, type ApiArchiveDirectory, type ApiArchiveFolder, type ApiSelfFundedProject, type SelfFundedWrite } from '../../api/archive-api';
import { canPreviewFile, fileApi, type ApiFile } from '../../api/file-api';
import { isBusinessTopic, topicApi, type ApiTopic } from '../../api/topic-api';

const GLOBAL_ROLES = new Set(['SYSTEM_ADMIN', 'PROJECT_TECH_LEADER', 'RESEARCH_ASSISTANT']);
const PROJECT_TYPE_LABELS: Record<ApiSelfFundedProject['projectType'], string> = {
  TECHNOLOGY: '科技项目', RENOVATION: '技改项目', INFRASTRUCTURE: '基建项目',
};

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function fileExtension(name: string) {
  return name.includes('.') ? name.split('.').pop()?.toUpperCase() ?? '文件' : '文件';
}

function RealFolderFileList({ folder, editable, onChanged }: { folder: ApiArchiveFolder; editable: boolean; onChanged: () => void }) {
  const [files, setFiles] = useState<ApiFile[]>([]);
  const [selectedFileIds, setSelectedFileIds] = useState<Key[]>([]);
  const [busy, setBusy] = useState(false);
  const refresh = useCallback(async () => {
    try { setFiles(await archiveApi.files(folder.id)); setSelectedFileIds([]); }
    catch (error) { message.error(error instanceof Error ? error.message : '文件加载失败'); }
  }, [folder.id]);
  useEffect(() => { void refresh(); }, [refresh]);

  const upload = async (file: File) => {
    setBusy(true);
    try {
      const uploaded = await fileApi.upload(file);
      await archiveApi.attach(folder.id, uploaded.id);
      message.success(`已上传：${file.name}`);
      await refresh(); onChanged();
    } catch (error) { message.error(error instanceof Error ? error.message : '上传失败'); }
    finally { setBusy(false); }
  };
  const removeFiles = async (ids: Key[]) => {
    try {
      await Promise.all(ids.map((id) => archiveApi.remove(folder.id, String(id))));
      message.success(`已删除 ${ids.length} 个文件`);
      await refresh(); onChanged();
    } catch (error) { message.error(error instanceof Error ? error.message : '删除失败'); }
  };
  const confirmRemove = (ids: Key[]) => Modal.confirm({
    title: ids.length > 1 ? '批量删除文件' : '删除文件', content: `确定删除已选择的 ${ids.length} 个文件吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消', onOk: () => removeFiles(ids),
  });

  return <div className="archive-file-manager">
    <div className="archive-file-toolbar">
      <div className="archive-material-tip"><Alert type="info" showIcon
        title={<Space wrap><b>材料提交提示</b><Tag color={folder.required ? 'red' : 'gold'}>{folder.required ? '必存' : '有则必存'}</Tag></Space>}
        description={folder.required ? `“${folder.name}”为必须归档保存的材料，请至少上传 ${folder.requiredQuantity} 份文件。` : `“${folder.name}”如在项目执行过程中形成，则必须上传归档；未形成时无需提交。`} /></div>
      <Space className="archive-file-actions">
        <Upload disabled={!editable || busy} showUploadList={false} beforeUpload={(file) => { void upload(file); return false; }}>
          <Button type="primary" disabled={!editable || busy} loading={busy} icon={<UploadOutlined />}>上传文件</Button>
        </Upload>
        <Button danger disabled={!editable || !selectedFileIds.length} icon={<DeleteOutlined />} onClick={() => confirmRemove(selectedFileIds)}>批量删除</Button>
      </Space>
    </div>
    <Table<ApiFile> className="archive-file-table" rowKey="id" pagination={false} dataSource={files}
      locale={{ emptyText: '当前文件夹暂无文件，请点击右上角“上传文件”添加材料' }}
      rowSelection={{ selectedRowKeys: selectedFileIds, onChange: setSelectedFileIds, getCheckboxProps: () => ({ disabled: !editable }) }}
      columns={[
        { title: '文件名', dataIndex: 'originalName', render: (value: string) => <Space><FileOutlined className="archive-list-file-icon" /><span className="archive-list-file-name">{value}</span></Space> },
        { title: '类型', dataIndex: 'originalName', width: 100, render: fileExtension },
        { title: '大小', dataIndex: 'size', width: 120, render: formatFileSize },
        { title: '上传人', dataIndex: 'uploaderId', width: 140 },
        { title: '上传时间', dataIndex: 'createdAt', width: 190 },
        { title: '操作', width: 220, render: (_, file) => <Space size={0}>
          <Button type="link" size="small" disabled={!canPreviewFile(file.contentType)} icon={<EyeOutlined />} onClick={() => void fileApi.download(file, true)}>查看</Button>
          <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => void fileApi.download(file)}>下载</Button>
          {editable && <Button type="link" danger size="small" icon={<DeleteOutlined />} onClick={() => confirmRemove([file.id])}>删除</Button>}
        </Space> },
      ]} />
  </div>;
}

function membershipLabel(topic: ApiTopic | undefined, unitId: string) {
  return topic?.members.find((item) => item.unitId === unitId && item.enabled)?.membershipType === 'LEAD' ? '牵头单位' : '承担单位';
}

export function RealNationalArchivePage() {
  const [user, setUser] = useState<ApiCurrentUser>();
  const [topics, setTopics] = useState<ApiTopic[]>([]);
  const [directories, setDirectories] = useState<ApiArchiveDirectory[]>([]);
  const [topicFilter, setTopicFilter] = useState<string>();
  const [unitFilter] = useState<string>();
  const [selectedTopicId, setSelectedTopicId] = useState<string>();
  const [selectedDirectory, setSelectedDirectory] = useState<ApiArchiveDirectory>();
  const [folders, setFolders] = useState<ApiArchiveFolder[]>([]);
  const [selectedFolder, setSelectedFolder] = useState<ApiArchiveFolder>();
  const [addFolderOpen, setAddFolderOpen] = useState(false);
  const [folderForm] = Form.useForm<{ name: string; required: boolean }>();
  const refresh = useCallback(async () => {
    try {
      const [who, topicPage, directoryList] = await Promise.all([authApi.me(), topicApi.list(), archiveApi.directories()]);
      setUser(who); setTopics(topicPage.items.filter(isBusinessTopic)); setDirectories(directoryList);
    } catch (error) { message.error(error instanceof Error ? error.message : '归档目录加载失败'); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);
  const refreshFolders = useCallback(async (directory: ApiArchiveDirectory) => {
    try { setFolders(await archiveApi.nationalFolders(directory.topicId, directory.unitId)); await refresh(); }
    catch (error) { message.error(error instanceof Error ? error.message : '文件夹加载失败'); }
  }, [refresh]);
  const openDirectory = (directory: ApiArchiveDirectory) => { setSelectedDirectory(directory); setSelectedFolder(undefined); void refreshFolders(directory); };
  const resetDrillDown = () => { setSelectedTopicId(undefined); setSelectedDirectory(undefined); setSelectedFolder(undefined); };
  const visibleTopics = topics.filter((topic) => {
    const rows = directories.filter((item) => item.topicId === topic.id);
    return rows.length > 0 && (!topicFilter || topic.id === topicFilter) && (!unitFilter || rows.some((item) => item.unitId === unitFilter));
  });
  const selectedTopic = topics.find((item) => item.id === selectedTopicId);
  const selectedTopicDirectories = directories.filter((item) => item.topicId === selectedTopicId && (!unitFilter || item.unitId === unitFilter));
  const topicOperational = selectedTopic?.enabled && selectedTopic.status === 'ACTIVE';
  const canSubmitSelectedUnit = Boolean(user && selectedDirectory && topicOperational && user.unitId === selectedDirectory.unitId && user.actionPermissions.includes('archive.topic.submit'));
  const canManageSelectedUnit = Boolean(user && selectedDirectory && topicOperational && (GLOBAL_ROLES.has(user.roleCode) || canSubmitSelectedUnit));
  const submitFolder = async () => {
    if (!selectedDirectory || !canManageSelectedUnit) return;
    try {
      const values = await folderForm.validateFields();
      await archiveApi.addNationalFolder(selectedDirectory.topicId, selectedDirectory.unitId, values.name.trim(), values.required);
      setAddFolderOpen(false); folderForm.resetFields(); await refreshFolders(selectedDirectory); message.success('自定义材料文件夹已创建');
    } catch (error) { if (error instanceof Error) message.error(error.message); }
  };
  const removeFolder = (folder: ApiArchiveFolder) => folder.canDelete && Modal.confirm({
    title: '删除自定义文件夹', content: `确定删除“${folder.name}”吗？`, okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      if (!selectedDirectory) return;
      try { await archiveApi.deleteFolder(folder.id); setSelectedFolder(undefined); await refreshFolders(selectedDirectory); message.success('文件夹已删除'); }
      catch (error) { message.error(error instanceof Error ? error.message : '删除失败'); }
    },
  });

  return <>
    <Card className="archive-content-card" title={`课题国家材料（${visibleTopics.length}）`} extra={<Button icon={<ReloadOutlined />} onClick={() => void refresh()}>刷新列表</Button>}>
      <div className="archive-list-filter"><Space wrap size={16}>
        <b>课题</b><Select allowClear value={topicFilter} onChange={(value) => { setTopicFilter(value); resetDrillDown(); }} style={{ width: 360 }} placeholder="全部课题"
          options={topics.filter((topic) => directories.some((item) => item.topicId === topic.id)).map((item) => ({ label: `${item.code} ${item.name}`, value: item.id }))} />
      </Space></div>
      <Row gutter={[16, 16]}>
      {visibleTopics.map((topic) => {
        const rows = directories.filter((item) => item.topicId === topic.id && (!unitFilter || item.unitId === unitFilter));
        const required = rows.reduce((sum, item) => sum + item.requiredCount, 0);
        const completed = rows.reduce((sum, item) => sum + item.completedCount, 0);
        const rate = required ? Math.round(completed * 100 / required) : 100;
        const folderCount = Math.max(0, ...rows.map((item) => item.folderCount));
        return <Col xs={24} xl={12} key={topic.id}><Card hoverable title={<Space><FolderOpenOutlined /><span>{topic.name}</span></Space>} extra={<Tag color="blue">国家材料</Tag>}
          actions={[<Button type="link" key="open" onClick={() => { setSelectedTopicId(topic.id); setSelectedDirectory(undefined); setSelectedFolder(undefined); }}>进入课题材料</Button>]}>
          <Space direction="vertical" style={{ width: '100%' }}><Space wrap><Tag color="blue">{topic.code}</Tag><Tag>参与单位 {rows.length} 家</Tag></Space>
            <div>国家清单文件夹：每单位 {folderCount} 个</div><Progress percent={rate} status={rate < 50 ? 'exception' : 'active'} /><div>{completed}/{required} 项必存材料已提交</div>
          </Space></Card></Col>;
      })}
      {!visibleTopics.length && <Col span={24}><Empty description="暂无可查看的课题国家材料" /></Col>}
      </Row>
    </Card>
    <Drawer width="78%" title={selectedTopic ? `${selectedTopic.name} · 课题参与单位` : ''} open={Boolean(selectedTopicId)} onClose={resetDrillDown}>
      <div className="archive-directory-hint">请先进入对应的课题参与单位，再查看或填报该单位的国家材料。</div>
      <Row gutter={[16, 16]}>{selectedTopicDirectories.map((directory) => {
        const editable = user?.unitId === directory.unitId && user.actionPermissions.includes('archive.topic.submit');
        const label = membershipLabel(selectedTopic, directory.unitId);
        return <Col xs={24} md={12} xl={8} key={`${directory.topicId}:${directory.unitId}`}><Card hoverable className="archive-unit-directory-card"
          title={<Space><span className="archive-unit-directory-icon"><TeamOutlined /></span><span>{directory.unitName}</span></Space>}
          extra={<Tag color={label === '牵头单位' ? 'blue' : 'default'}>{label}</Tag>}
          actions={[<Button type="link" key="open" onClick={() => openDirectory(directory)}>进入单位材料</Button>]}>
          <Space direction="vertical" style={{ width: '100%' }}><Space wrap><Tag color={editable ? 'green' : 'default'}>{editable ? '可填报' : '仅查看'}</Tag><span>材料文件夹：{directory.folderCount} 个</span></Space>
            <Progress percent={directory.completionRate} status={directory.completionRate < 50 ? 'exception' : 'active'} /><div>{directory.completedCount}/{directory.requiredCount} 项必存材料已提交</div>
          </Space></Card></Col>;
      })}</Row>
    </Drawer>
    <Drawer width="78%" title={selectedDirectory && selectedTopic ? `${selectedTopic.code} · ${selectedDirectory.unitName} · 国家材料文件夹` : ''}
      open={Boolean(selectedDirectory)} onClose={() => { setSelectedDirectory(undefined); setSelectedFolder(undefined); }}
      extra={canManageSelectedUnit && <Button type="primary" icon={<FileAddOutlined />} onClick={() => { folderForm.setFieldsValue({ required: true }); setAddFolderOpen(true); }}>新增文件夹</Button>}>
      {selectedDirectory && <div className="archive-directory-hint"><Space wrap>
        <Tag color={membershipLabel(selectedTopic, selectedDirectory.unitId) === '牵头单位' ? 'blue' : 'default'}>{membershipLabel(selectedTopic, selectedDirectory.unitId)}</Tag>
        <span>{canManageSelectedUnit ? '您可新增或删除自定义文件夹，并上传、补充和删除材料。' : '当前为查看权限，不可修改该单位材料。'}</span>
      </Space></div>}
      <Row gutter={[16, 16]}>{folders.map((folder) => <Col xs={24} sm={12} lg={8} xl={6} key={folder.id}>
        <Card size="small" hoverable className="archive-folder-card" onClick={() => setSelectedFolder(folder)}><Space align="start"><FolderOpenOutlined className="archive-folder-icon" /><div>
          <div className="archive-folder-name">{folder.name}</div><div className="archive-folder-meta">{folder.custom ? '自定义材料文件夹' : '清单材料'} · {folder.required ? '必存' : '有则必存'}</div>
          <Progress percent={folder.completed ? 100 : 0} size="small" showInfo={false} /><span className="archive-folder-count">{folder.fileCount ? `${folder.fileCount} 个文件 · 已提交` : '暂无文件'}</span>
        </div></Space>{folder.canDelete && <Button type="text" danger size="small" className="archive-folder-delete" icon={<DeleteOutlined />} onClick={(event) => { event.stopPropagation(); removeFolder(folder); }} />}</Card>
      </Col>)}{!folders.length && <Col span={24}><Empty description="当前单位暂无材料文件夹" /></Col>}</Row>
    </Drawer>
    <Drawer width="78%" title={selectedFolder && selectedDirectory ? `${selectedDirectory.unitName} · ${selectedFolder.name} · 文件管理` : ''} open={Boolean(selectedFolder)} onClose={() => setSelectedFolder(undefined)}>
      {selectedFolder && <RealFolderFileList folder={selectedFolder} editable={canManageSelectedUnit} onChanged={() => selectedDirectory && refreshFolders(selectedDirectory)} />}
    </Drawer>
    <Modal title="新增自定义材料文件夹" open={addFolderOpen} onCancel={() => { setAddFolderOpen(false); folderForm.resetFields(); }} onOk={() => void submitFolder()} okText="创建" cancelText="取消">
      <Form form={folderForm} layout="vertical" initialValues={{ required: true }}><Form.Item name="name" label="文件夹名称" rules={[{ required: true, message: '请输入文件夹名称' }]}><Input placeholder="例如：补充说明材料" maxLength={200} /></Form.Item>
        <Form.Item name="required" label="材料要求" rules={[{ required: true, message: '请选择材料要求' }]}><Select options={[{ label: '必存', value: true }, { label: '有则必存', value: false }]} /></Form.Item>
      </Form>
    </Modal>
  </>;
}

export function RealSelfFundedPage() {
  const [user, setUser] = useState<ApiCurrentUser>();
  const [topics, setTopics] = useState<ApiTopic[]>([]);
  const [directories, setDirectories] = useState<ApiArchiveDirectory[]>([]);
  const [projects, setProjects] = useState<ApiSelfFundedProject[]>([]);
  const [topicFilter, setTopicFilter] = useState<string>();
  const [unitFilter, setUnitFilter] = useState<string>();
  const [selected, setSelected] = useState<ApiSelfFundedProject>();
  const [folders, setFolders] = useState<ApiArchiveFolder[]>([]);
  const [selectedFolder, setSelectedFolder] = useState<ApiArchiveFolder>();
  const [editing, setEditing] = useState(false);
  const [addFolderOpen, setAddFolderOpen] = useState(false);
  const [form] = Form.useForm<SelfFundedWrite>();
  const [folderForm] = Form.useForm<{ name: string; required: boolean }>();
  const refresh = useCallback(async () => {
    try {
      const [who, topicPage, directoryList, projectList] = await Promise.all([authApi.me(), topicApi.list(), archiveApi.directories(), archiveApi.projects()]);
      setUser(who); setTopics(topicPage.items.filter(isBusinessTopic)); setDirectories(directoryList); setProjects(projectList);
    } catch (error) { message.error(error instanceof Error ? error.message : '自筹项目加载失败'); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);
  const refreshProjectFolders = async (project: ApiSelfFundedProject) => {
    try {
      const list = await archiveApi.projectFolders(project.id);
      setFolders(list);
      setSelectedFolder((current) => current ? list.find((item) => item.id === current.id) : current);
      await refresh();
    }
    catch (error) { message.error(error instanceof Error ? error.message : '材料清单加载失败'); }
  };
  const openProject = async (project: ApiSelfFundedProject) => {
    setSelected(project); setSelectedFolder(undefined);
    await refreshProjectFolders(project);
  };
  const save = async () => {
    try {
      const values = await form.validateFields();
      if (values.endDate < values.startDate) return message.warning('结束日期不能早于开始日期');
      if (selected && editing) await archiveApi.updateProject(selected.id, { ...values, recordVersion: selected.recordVersion }); else await archiveApi.createProject(values);
      const updated = Boolean(selected); setEditing(false); setSelected(undefined); form.resetFields(); await refresh();
      message.success(updated ? '项目基础信息已更新' : '配套自筹项目已创建，并已生成对应类型的归档清单');
    } catch (error) { if (error instanceof Error) message.error(error.message); }
  };
  const editable = Boolean(user?.roleCode === 'INTERNAL_TOPIC_UNIT' && user.actionPermissions.includes('self-funded.manage'));
  const selectedProjectTopic = topics.find((topic) => topic.id === selected?.topicId);
  const selectedProjectOperational = Boolean(selectedProjectTopic?.enabled && selectedProjectTopic.status === 'ACTIVE');
  const canManageSelectedProject = Boolean(user && selected && selectedProjectOperational
    && (GLOBAL_ROLES.has(user.roleCode) || editable && user.unitId === selected.ownerUnitId));
  const editableTopics = topics.filter((topic) => topic.enabled && topic.status === 'ACTIVE' && user?.memberships.some((member) => member.topicId === topic.id && member.enabled));
  const filteredProjects = projects.filter((project) => (!topicFilter || project.topicId === topicFilter) && (!unitFilter || project.ownerUnitId === unitFilter));
  const unitNames = new Map(directories.map((item) => [item.unitId, item.unitName]));
  const unitOptions = [...new Set(projects.map((item) => item.ownerUnitId))].map((unitId) => ({ value: unitId, label: unitNames.get(unitId) ?? unitId }));
  const submitFolder = async () => {
    if (!selected || !canManageSelectedProject) return;
    try {
      const values = await folderForm.validateFields();
      await archiveApi.addProjectFolder(selected.id, values.name.trim(), values.required);
      setAddFolderOpen(false); folderForm.resetFields(); await refreshProjectFolders(selected); message.success('自定义材料文件夹已创建');
    } catch (error) { if (error instanceof Error) message.error(error.message); }
  };
  const removeFolder = (folder: ApiArchiveFolder) => folder.canDelete && Modal.confirm({
    title: '删除自定义文件夹', content: `确定删除“${folder.name}”吗？`, okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      if (!selected) return;
      try { await archiveApi.deleteFolder(folder.id); setSelectedFolder(undefined); await refreshProjectFolders(selected); message.success('文件夹已删除'); }
      catch (error) { message.error(error instanceof Error ? error.message : '删除失败'); }
    },
  });

  return <>
    <Card className="archive-content-card" title={`配套自筹项目（${filteredProjects.length}）`} extra={<Space>
      <Button icon={<ReloadOutlined />} onClick={() => void refresh()}>刷新列表</Button>
      {editable && editableTopics.length > 0 && <Button type="primary" icon={<FileAddOutlined />} onClick={() => {
        setSelected(undefined); form.resetFields(); form.setFieldsValue({ topicId: editableTopics[0]?.id, projectType: 'TECHNOLOGY', status: '筹备中' }); setEditing(true);
      }}>新建自筹项目</Button>}
    </Space>}>
      <div className="archive-list-filter"><Space wrap size={16}>
        <b>课题</b><Select allowClear value={topicFilter} onChange={setTopicFilter} style={{ width: 360 }} placeholder="全部课题" options={topics.map((item) => ({ label: `${item.code} ${item.name}`, value: item.id }))} />
        <b>提交单位</b><Select allowClear value={unitFilter} onChange={setUnitFilter} style={{ width: 320 }} placeholder="全部单位" options={unitOptions} />
      </Space></div>
      <Row gutter={[16, 16]}>
      {filteredProjects.map((project) => <Col xs={24} xl={12} key={project.id}><Card hoverable title={<Space><FolderOpenOutlined /><span>{project.name}</span></Space>}
        extra={<Tag color={project.status === '已完成' ? 'green' : 'blue'}>{project.status}</Tag>}
        actions={[<Button key={`open-${project.id}`} type="link" onClick={() => void openProject(project)}>进入归档</Button>,
          ...(editable && user?.unitId === project.ownerUnitId ? [<Button key={`edit-${project.id}`} type="link" onClick={() => { setSelected(project); form.setFieldsValue({ ...project, startDate: project.startDate ?? '', endDate: project.endDate ?? '' }); setEditing(true); }}>编辑基础信息</Button>] : [])]}>
        <Space direction="vertical" style={{ width: '100%' }}><Space wrap><Tag color="blue">{PROJECT_TYPE_LABELS[project.projectType]}</Tag><Tag>{project.code}</Tag><Tag>{unitNames.get(project.ownerUnitId) ?? project.ownerUnitId}</Tag></Space>
          <div>项目负责人：{project.principalName}　实施单位：{unitNames.get(project.ownerUnitId) ?? project.ownerUnitId}</div>
          <Progress percent={project.completionRate} status={project.completionRate < 50 ? 'exception' : 'active'} /><div>必存材料完成率：{project.completionRate}%</div>
        </Space></Card></Col>)}
      {!filteredProjects.length && <Col span={24}><Empty description="当前筛选条件下暂无配套自筹项目" /></Col>}
      </Row>
    </Card>
    <Modal title={selected ? '编辑配套自筹项目' : '新建配套自筹项目'} open={editing} onCancel={() => { setEditing(false); setSelected(undefined); form.resetFields(); }} onOk={() => void save()} width={680}>
      <Form form={form} layout="vertical"><Form.Item name="topicId" label="所属课题" rules={[{ required: true }]}><Select disabled={Boolean(selected)} options={editableTopics.map((item) => ({ value: item.id, label: `${item.code} ${item.name}` }))} /></Form.Item>
        <Row gutter={16}><Col span={12}><Form.Item name="code" label="项目编号" rules={[{ required: true }]}><Input /></Form.Item></Col><Col span={12}><Form.Item name="projectType" label="项目类型" rules={[{ required: true }]}><Select disabled={Boolean(selected)} options={Object.entries(PROJECT_TYPE_LABELS).map(([value, label]) => ({ value, label }))} /></Form.Item></Col></Row>
        <Form.Item name="name" label="项目名称" rules={[{ required: true }]}><Input /></Form.Item>
        <Row gutter={16}><Col span={12}><Form.Item name="principalName" label="项目负责人" rules={[{ required: true }]}><Input /></Form.Item></Col><Col span={12}><Form.Item label="实施单位"><Input value={selected ? unitNames.get(selected.ownerUnitId) : unitNames.get(user?.unitId ?? '')} disabled /></Form.Item></Col></Row>
        <Row gutter={16}><Col span={8}><Form.Item name="startDate" label="开始日期" rules={[{ required: true, message: '请选择开始日期' }]}><Input type="date" /></Form.Item></Col><Col span={8}><Form.Item name="endDate" label="结束日期" rules={[{ required: true, message: '请选择结束日期' }]}><Input type="date" /></Form.Item></Col><Col span={8}><Form.Item name="budget" label="预算（万元）"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item></Col></Row>
        <Form.Item name="status" label="状态"><Select options={['筹备中', '实施中', '验收中', '已完成'].map((value) => ({ value, label: value }))} /></Form.Item>
      </Form>
    </Modal>
    <Drawer width="78%" title={selected && !editing ? `${selected.name} · 自筹材料文件夹` : ''} open={Boolean(selected && !editing)} onClose={() => { setSelected(undefined); setSelectedFolder(undefined); }}
      extra={canManageSelectedProject && <Button type="primary" icon={<FileAddOutlined />} onClick={() => { folderForm.setFieldsValue({ required: true }); setAddFolderOpen(true); }}>新增文件夹</Button>}>
      <Row gutter={[16, 16]}>{folders.map((folder) => <Col xs={24} sm={12} lg={8} xl={6} key={folder.id}><Card size="small" hoverable className="archive-folder-card" onClick={() => setSelectedFolder(folder)}>
        <Space align="start"><FolderOpenOutlined className="archive-folder-icon" /><div><div className="archive-folder-name">{folder.name}</div><div className="archive-folder-meta">{folder.custom ? '自定义材料文件夹' : '清单材料'} · {folder.required ? '必存材料' : '有则必存'} · 至少 {folder.requiredQuantity} 份</div>
          <Progress percent={folder.completed ? 100 : 0} size="small" showInfo={false} /><span className="archive-folder-count">{folder.fileCount ? `${folder.fileCount} 个文件 · 已提交` : '暂无文件'}</span>
        </div></Space>{folder.canDelete && <Button type="text" danger size="small" className="archive-folder-delete" icon={<DeleteOutlined />} onClick={(event) => { event.stopPropagation(); removeFolder(folder); }} />}</Card></Col>)}{!folders.length && <Col span={24}><Empty description="当前项目暂无材料文件夹" /></Col>}</Row>
    </Drawer>
    <Drawer width="78%" title={selectedFolder ? `${selectedFolder.name} · 文件管理` : ''} open={Boolean(selectedFolder)} onClose={() => setSelectedFolder(undefined)}>
      {selectedFolder && <RealFolderFileList folder={selectedFolder} editable={canManageSelectedProject} onChanged={() => selected && refreshProjectFolders(selected)} />}
    </Drawer>
    <Modal title="新增自定义材料文件夹" open={addFolderOpen} onCancel={() => { setAddFolderOpen(false); folderForm.resetFields(); }} onOk={() => void submitFolder()} okText="创建" cancelText="取消">
      <Form form={folderForm} layout="vertical" initialValues={{ required: true }}><Form.Item name="name" label="文件夹名称" rules={[{ required: true, message: '请输入文件夹名称' }]}><Input placeholder="例如：补充说明材料" maxLength={200} /></Form.Item>
        <Form.Item name="required" label="材料要求" rules={[{ required: true, message: '请选择材料要求' }]}><Select options={[{ label: '必存', value: true }, { label: '有则必存', value: false }]} /></Form.Item>
      </Form>
    </Modal>
  </>;
}
