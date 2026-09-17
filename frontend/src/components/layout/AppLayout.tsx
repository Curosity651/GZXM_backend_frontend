import { useState, type ReactNode } from 'react';
import { Avatar, Button, Dropdown, Layout, Menu, Space, Tag, Typography, message } from 'antd';
import type { MenuProps } from 'antd';
import {
  DashboardOutlined, FileDoneOutlined, FileTextOutlined,
  HomeOutlined, InboxOutlined, LogoutOutlined, SafetyCertificateOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined, SettingOutlined, TeamOutlined, UserOutlined,
} from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { type PageKey } from '../../domain/permissions';
import { useSessionStore } from '../../store/session';

const { Sider, Content, Header } = Layout;
const { Text } = Typography;

interface MenuNode {
  key: string;
  label: ReactNode;
  icon?: ReactNode;
  page?: PageKey;
  children?: MenuNode[];
}

const menuTree: MenuNode[] = [
  { key: '/', label: <Link to="/">工作台</Link>, icon: <HomeOutlined />, page: 'home' },
  {
    key: 'indicator-group', label: '科研指标管理', icon: <DashboardOutlined />, children: [
      { key: '/indicator', label: <Link to="/indicator">科研指标配置</Link>, page: 'topic-indicator' },
    ],
  },
  { key: '/achievement-entry', label: <Link to="/achievement-entry">成果管理</Link>, icon: <FileDoneOutlined />, page: 'achievement-entry' },
  { key: '/reports', label: <Link to="/reports">进度管理</Link>, icon: <FileTextOutlined />, page: 'report-management' },
  {
    key: 'archive-group', label: '归档材料', icon: <InboxOutlined />, children: [
      { key: '/archive/topics', label: <Link to="/archive/topics">课题国家材料</Link>, page: 'topic-archive' },
      { key: '/archive/self-funded', label: <Link to="/archive/self-funded">配套自筹材料</Link>, page: 'self-funded-archive' },
      { key: '/archive/monitoring', label: <Link to="/archive/monitoring">归档进度监控</Link>, page: 'archive-monitoring' },
    ],
  },
  {
    key: 'admin-group', label: '系统管理', icon: <SettingOutlined />, children: [
      { key: '/admin/users', label: <Link to="/admin/users">用户管理</Link>, page: 'user-management' },
      { key: '/admin/roles', label: <Link to="/admin/roles">角色权限管理</Link>, page: 'role-permission' },
    ],
  },
];

function visibleMenu(nodes: MenuNode[], pagePermissions: string[]): MenuNode[] {
  const result: MenuNode[] = [];
  nodes.forEach((node) => {
    const children = node.children ? visibleMenu(node.children, pagePermissions) : undefined;
    if (children && children.length > 0) result.push({ ...node, children });
    else if (node.page && pagePermissions.includes(node.page)) result.push({ ...node });
  });
  return result;
}

export function AppLayout() {
  const user = useSessionStore((state) => state.user);
  const logout = useSessionStore((state) => state.logout);
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  if (!user) return null;

  const roleNames: Record<string, string> = { SYSTEM_ADMIN: '系统管理员', PROJECT_TECH_LEADER: '项目技术负责人', RESEARCH_ASSISTANT: '科研助理', INTERNAL_TOPIC_UNIT: '内部课题单位', EXTERNAL_TOPIC_UNIT: '外部课题单位' };
  const signOut = async () => {
    try { await logout(); }
    catch (error) { message.error(error instanceof Error ? error.message : '退出失败'); }
    finally { navigate('/login', { replace: true }); }
  };

  return (
    <Layout className="app-shell">
      <Sider width={244} collapsedWidth={72} collapsed={collapsed} trigger={null} theme="dark" className="app-sider">
        <div className={`brand-block${collapsed ? ' is-collapsed' : ''}`}>
          <div className="brand-mark"><SafetyCertificateOutlined /></div>
          {!collapsed && <div><div className="brand-title">GZXM 科研管理</div><div className="brand-subtitle">重点项目协同工作台</div></div>}
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[location.pathname]} defaultOpenKeys={['indicator-group', 'achievement-group', 'archive-group', 'admin-group']} items={visibleMenu(menuTree, user.pagePermissions) as MenuProps['items']} />
        <Button
          type="text"
          className="sider-collapse-button"
          icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          aria-label={collapsed ? '展开侧边栏' : '收起侧边栏'}
          onClick={() => setCollapsed((value) => !value)}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <div><Text strong>{import.meta.env.VITE_PROJECT_NAME ?? '国家科技重大专项示范'}</Text><Tag color="blue" style={{ marginLeft: 10 }}>{import.meta.env.VITE_PROJECT_CODE ?? 'GZ-2025-001'}</Tag></div>
          <Space size={12}>
            <Dropdown menu={{ items: [
              { key: 'role', icon: <TeamOutlined />, label: roleNames[user.roleCode] ?? user.roleCode, disabled: true },
              { type: 'divider' },
              { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: () => { void signOut(); } },
            ] }}>
              <Button type="text"><Space><Avatar size="small" icon={<UserOutlined />} />{user.username}</Space></Button>
            </Dropdown>
          </Space>
        </Header>
        <Content className="app-content"><Outlet /></Content>
      </Layout>
    </Layout>
  );
}
