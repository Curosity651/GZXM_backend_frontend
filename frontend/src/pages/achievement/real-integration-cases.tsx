// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { act, cleanup, configure, fireEvent, render as renderUi, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { ConfigProvider, message } from 'antd';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApiCurrentUser } from '../../api/auth-api';
import type { Achievement, AchievementProgressRow, Topic } from '../../api/research/contracts';
import { configureResearchMetadataRequest } from '../../api/research/client';
import { IndicatorConfigPage } from '../indicator/IndicatorConfigPage';
import { TopicIndicatorConfigPage } from '../indicator/TopicIndicatorConfigPage';
import { AchievementEntryPage } from './AchievementEntryPage';
import { RealAchievementForm } from './RealAchievementForm';
import { RealAchievementProgress } from './RealAchievementProgress';

vi.mock('../../api/api-mode', () => ({ isRealApi: () => true, API_BASE_URL: '/api/v1' }));
configure({ asyncUtilTimeout: 5000 });
Object.defineProperty(window, 'matchMedia', { writable: true, value: () => ({ matches: false, addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {} }) });
class ResizeObserverStub { observe() {} unobserve() {} disconnect() {} }
globalThis.ResizeObserver = ResizeObserverStub;
const computedStyle = window.getComputedStyle.bind(window);
window.getComputedStyle = element => computedStyle(element);

const nodes = [{ id: '1', name: '第一节点', deadline: '2026-12-31', sortOrder: 1, enabled: true }];
const definitions = [{ id: '1', code: 'PAPER', name: '论文', achievementType: 'PAPER' as const, unit: '篇', category: 'BASE' as const, enabled: true }];
const units = [{ id: '2', code: 'U2', name: '测试单位', internal: true, enabled: true }];
let actor: ApiCurrentUser;
let topic: Topic;
let record: Achievement;
let failSave: boolean;
const calls: Array<{ path: string; init?: RequestInit }> = [];
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
beforeEach(() => {
  actor = { id: '5', username: '测试账号', roleCode: 'RESEARCH_ASSISTANT', pagePermissions: ['topic-indicator', 'achievement-entry'], actionPermissions: ['topic.manage', 'indicator.manage', 'topic-indicator.publish', 'achievement.initial.approve'], memberships: [] };
  topic = { id: '1', code: 'TEST-TOPIC', name: '数据库课题', leadUnitId: '2', status: 'ACTIVE', enabled: true, recordVersion: 4,
    members: [{ id: '1', topicId: '1', unitId: '2', unitName: '测试单位', membershipType: 'LEAD', enabled: true }] };
  record = { id: '1', topicId: '1', unitId: '2', nodeId: '1', indicatorDefinitionId: '1', achievementType: 'PAPER', title: '真实成果草稿', responsiblePerson: '测试负责人', status: 'DRAFT', recordVersion: 7, submittedVersion: 0, materials: [], detail: { isChineseCoreJournal: false } };
  failSave = false; calls.length = 0;
  vi.stubGlobal('fetch', vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
    const path = String(url).replace('/api/v1', ''); calls.push({ path, init });
    if (path === '/auth/me') return json(actor);
    if (path.startsWith('/topics?')) return json({ items: [topic], page: 1, size: 20, total: 1 });
    if (path === '/topics/1' && init?.method === 'PUT') {
      if (failSave) return json({ status: 409, code: 'TOPIC_VERSION_CONFLICT', detail: '请携带最新recordVersion后重试' }, 409);
      topic = { ...topic, ...JSON.parse(String(init.body)), recordVersion: topic.recordVersion + 1 }; return json(topic);
    }
    if (path === '/topics/1') return json(topic);
    if (path === '/units') return json(units);
    if (path === '/time-nodes') return json(nodes);
    if (path === '/indicator-definitions') return json(definitions);
    if (path.startsWith('/topics/1/indicator-targets')) return json([{ id: '1', topicId: '1', nodeId: '1', indicatorDefinitionId: '1', targetQuantity: 12, version: 3, status: 'PUBLISHED' }]);
    if (path.startsWith('/topics/1/unit-allocations')) return json([{ id: '1', topicId: '1', unitId: '2', nodeId: '1', indicatorDefinitionId: '1', targetQuantity: 0, status: 'PUBLISHED', version: 3 }]);
    if (path.startsWith('/achievements?')) return json({ items: [record], total: 1, page: 1, size: 20 });
    if (path === '/achievements/1' && init?.method === 'PUT') { record = { ...record, ...JSON.parse(String(init.body)), recordVersion: 8 }; return json(record); }
    if (path === '/achievements' && init?.method === 'POST') { record = { ...record, ...JSON.parse(String(init.body)) }; return json(record, 201); }
    if (path === '/achievements/1') return json(record);
    if (path === '/achievements/1/actions') { record = { ...record, status: 'PRE_INITIAL', recordVersion: 8, submittedVersion: 1 }; return json(record); }
    if (path === '/achievements/1/reviews') {
      const body = JSON.parse(String(init?.body));
      record = { ...record, status: body.decision === 'RETURN' ? 'PRE_RETURNED' : 'PRE_FINAL', recordVersion: record.recordVersion + 1 };
      return json({ id: '1', businessType: 'ACHIEVEMENT', businessId: '1', stage: 'PRE_REVIEW', level: 'INITIAL', decision: body.decision === 'RETURN' ? 'RETURNED' : 'APPROVED', opinion: body.opinion, submittedVersion: body.submittedVersion, operatorId: actor.id }, 201);
    }
    if (path.endsWith('/snapshots')) return json([]);
    if (path.startsWith('/achievement-progress')) return json({ nodeId: '1', countingBasis: 'CUMULATIVE_NODE_CURRENT_FACTS', baseTotals: { PAPER: 0, PATENT: 0, COPYRIGHT: 0, STANDARD: 0, TALENT: 0 }, baseStages: {}, rows: [], specialIndicators: [] });
    throw new Error(`Unexpected request: ${init?.method ?? 'GET'} ${path}`);
  }));
});
afterEach(() => { cleanup(); act(() => message.destroy()); configureResearchMetadataRequest(undefined); vi.unstubAllGlobals(); });
// jsdom does not finish CSS animations; disable motion only in this B test provider.
function render(ui: ReactNode) { return renderUi(<ConfigProvider theme={{ token: { motion: false } }}>{ui}</ConfigProvider>); }
function editor() { return render(<MemoryRouter initialEntries={['/indicator/topic/1']}><Routes><Route path="/indicator/topic/:topicId" element={<TopicIndicatorConfigPage />} /></Routes></MemoryRouter>); }
async function select(label: string, option: string) {
  fireEvent.mouseDown(await screen.findByLabelText(label));
  fireEvent.click(await screen.findByText(option, { selector: '.ant-select-item-option-content' }));
}

describe('B real pages (HTTP test doubles; not full browser E2E)', () => {
  it('real mode refuses to substitute Mock data for a missing real session', async () => {
    vi.mocked(fetch).mockResolvedValue(json({ status: 403, detail: '没有真实会话' }, 403));
    render(<MemoryRouter><IndicatorConfigPage /></MemoryRouter>);
    expect(await screen.findByText('真实业务暂不可用')).toBeVisible();
    expect(screen.queryByText('新建课题')).not.toBeInTheDocument();
    expect(screen.queryByText('数据库课题')).not.toBeInTheDocument();
  });
  it('requires real page permissions and clears content on session expiry', async () => {
    actor.pagePermissions = [];
    const first = render(<MemoryRouter><IndicatorConfigPage /></MemoryRouter>);
    expect(await screen.findByText('无权访问此页面')).toBeVisible();
    first.unmount(); actor.pagePermissions = ['topic-indicator'];
    render(<MemoryRouter><IndicatorConfigPage /></MemoryRouter>);
    expect(await screen.findByText('数据库课题')).toBeVisible();
    act(() => window.dispatchEvent(new CustomEvent('gzxm:session-expired')));
    expect(screen.queryByText('数据库课题')).not.toBeInTheDocument();
    expect(screen.getByText('真实业务暂不可用')).toBeVisible();
  });
  it('saves the fetched topic version and re-reads persisted values after remount', async () => {
    const first = editor();
    const name = await screen.findByLabelText('课题名称');
    fireEvent.change(name, { target: { value: '保存到服务器的课题' } });
    fireEvent.change(screen.getByLabelText('开始日期'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '保存课题' }));
    await waitFor(() => expect(topic.name).toBe('保存到服务器的课题'));
    const put = calls.find(call => call.path === '/topics/1' && call.init?.method === 'PUT');
    expect(JSON.parse(String(put?.init?.body)).recordVersion).toBe(4);
    expect(JSON.parse(String(put?.init?.body))).not.toHaveProperty('startDate');
    first.unmount(); editor();
    expect(await screen.findByLabelText('课题名称')).toHaveValue('保存到服务器的课题');
    expect(calls.filter(call => call.path === '/topics/1' && !call.init?.method).length).toBeGreaterThanOrEqual(2);
  });
  it('shows conflict without changing server data or silently overwriting the new version', async () => {
    failSave = true; editor();
    fireEvent.change(await screen.findByLabelText('课题名称'), { target: { value: '冲突修改' } });
    fireEvent.click(screen.getByRole('button', { name: '保存课题' }));
    expect(await screen.findByText('请携带最新recordVersion后重试')).toBeVisible();
    expect(topic.name).toBe('数据库课题');
    expect(calls.filter(call => call.init?.method === 'PUT')).toHaveLength(1);
  });
  it('shows effective targets and blocks the missing metadata dependency', async () => {
    editor();
    expect(await screen.findByText('草稿编辑待接入')).toBeVisible();
    expect(screen.getByRole('button', { name: '读取草稿 / 放弃本地修改' })).toBeDisabled();
    expect(await screen.findByText('12')).toBeVisible();
    expect(calls.some(call => call.path.includes('view=draft'))).toBe(false);
  });
  it('uses supplied metadata for draft save and publishes only after the edited draft is saved', async () => {
    const adapter = vi.fn().mockImplementation(async (_path: string, init?: RequestInit) => ({
      data: [{ id: '1', topicId: '1', nodeId: '1', indicatorDefinitionId: '1', targetQuantity: init ? JSON.parse(String(init.body)).targets[0].targetQuantity : 12, status: 'DRAFT', version: 3 }],
      headers: new Headers({ 'X-Draft-Version': init ? '8' : '7' }),
    }));
    configureResearchMetadataRequest(adapter);
    editor();
    fireEvent.click(await screen.findByRole('button', { name: '读取草稿 / 放弃本地修改' }));
    const quantity = await screen.findByRole('spinbutton');
    fireEvent.change(quantity, { target: { value: '13' } });
    expect(screen.getByRole('button', { name: '下发已保存草稿' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '保存完整草稿' }));
    await waitFor(() => expect(adapter).toHaveBeenCalledTimes(2));
    expect(JSON.parse(String(adapter.mock.calls[1][1].body))).toEqual({ nodeId: '1', draftVersion: 7, targets: [{ indicatorDefinitionId: '1', targetQuantity: 13 }] });
    await waitFor(() => expect(screen.getByRole('button', { name: '下发已保存草稿' })).not.toBeDisabled());
    fireEvent.click(screen.getByRole('button', { name: '下发已保存草稿' }));
    await waitFor(() => expect(calls.some(call => call.path === '/topics/1/indicator-targets:publish')).toBe(true));
    const publish = calls.find(call => call.path === '/topics/1/indicator-targets:publish')!;
    expect(JSON.parse(String(publish.init?.body))).toEqual({ nodeId: '1', draftVersion: 8 });
  });
  it.each(['SYSTEM_ADMIN', 'PROJECT_TECH_LEADER', 'RESEARCH_ASSISTANT', 'INTERNAL_TOPIC_UNIT', 'EXTERNAL_TOPIC_UNIT'])('%s sees server data and only the permitted topic creation action', async roleCode => {
    actor = { ...actor, roleCode };
    render(<MemoryRouter><IndicatorConfigPage /></MemoryRouter>);
    expect(await screen.findByText('数据库课题')).toBeVisible();
    expect(Boolean(screen.queryByRole('button', { name: '新建课题' }))).toBe(roleCode === 'RESEARCH_ASSISTANT');
  });
  it('edits details with a record version while preserving boolean false and existing material links', async () => {
    const saved = vi.fn();
    render(<RealAchievementForm user={{ ...actor, roleCode: 'INTERNAL_TOPIC_UNIT', unitId: '2' }} item={record} topics={[topic]} nodes={nodes} definitions={definitions} saved={saved} />);
    fireEvent.change(screen.getByLabelText('成果标题'), { target: { value: '修改后的标题' } });
    fireEvent.click(screen.getByRole('button', { name: '保存成果草稿' }));
    await waitFor(() => expect(saved).toHaveBeenCalledOnce());
    const call = calls.find(value => value.path === '/achievements/1' && value.init?.method === 'PUT');
    const body = JSON.parse(String(call?.init?.body));
    expect(body).toMatchObject({ title: '修改后的标题', topicId: '1', nodeId: '1', indicatorDefinitionId: '1', recordVersion: 7, detail: { isChineseCoreJournal: false } });
    expect(body).not.toHaveProperty('materialAttachments');
    expect(body).not.toHaveProperty('unitId');
  });
  it('allows creating a draft against a published zero target and does not require formal fields', async () => {
    const saved = vi.fn();
    render(<RealAchievementForm user={{ ...actor, roleCode: 'INTERNAL_TOPIC_UNIT', unitId: '2' }} topics={[topic]} nodes={nodes} definitions={definitions} saved={saved} />);
    await select('所属课题', '数据库课题'); await select('考核节点', '第一节点');
    await waitFor(() => expect(calls.some(call => call.path === '/topics/1/unit-allocations?nodeId=1')).toBe(true));
    await waitFor(() => expect(screen.getByLabelText('本单位已下发基础指标（含零目标）').closest('.ant-select')).not.toHaveClass('ant-select-disabled'));
    await select('本单位已下发基础指标（含零目标）', '论文');
    fireEvent.change(screen.getByLabelText('成果标题'), { target: { value: '零目标成果' } });
    fireEvent.change(screen.getByLabelText('负责人'), { target: { value: '测试负责人' } });
    fireEvent.click(screen.getByRole('button', { name: '保存成果草稿' }));
    await waitFor(() => expect(saved).toHaveBeenCalledOnce());
    expect(record.title).toBe('零目标成果');
  });
  it('submits pre-review with the latest server version and reloads the list', async () => {
    actor = { ...actor, roleCode: 'INTERNAL_TOPIC_UNIT', unitId: '2', actionPermissions: ['achievement.submit'] };
    render(<AchievementEntryPage />);
    fireEvent.click(await screen.findByRole('button', { name: '提交预审' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认操作' }));
    await waitFor(() => expect(record.status).toBe('PRE_INITIAL'));
    expect(await screen.findByText('预审待初审', { selector: '.ant-tag' })).toBeVisible();
    const sent = calls.find(value => value.path === '/achievements/1/actions');
    expect(JSON.parse(String(sent?.init?.body))).toEqual({ action: 'SUBMIT_PRE_REVIEW', recordVersion: 7 });
    expect(new Headers(sent?.init?.headers).get('Idempotency-Key')).toMatch(/^[\w-]{36}$/);
  });
  it('requires a return opinion and sends both review versions before updating the UI', async () => {
    record = { ...record, status: 'PRE_INITIAL', submittedVersion: 3 };
    render(<AchievementEntryPage />);
    fireEvent.click(await screen.findByRole('button', { name: /审\s*批/ }));
    await select('审批结论', '退回');
    fireEvent.click(screen.getByRole('button', { name: '确认操作' }));
    expect(await screen.findByText('退回必须填写意见')).toBeVisible();
    expect(calls.some(call => call.path.endsWith('/reviews'))).toBe(false);
    fireEvent.change(screen.getByLabelText('审批意见'), { target: { value: '请补充研究方向' } });
    fireEvent.click(screen.getByRole('button', { name: '确认操作' }));
    await waitFor(() => expect(record.status).toBe('PRE_RETURNED'));
    const review = calls.find(call => call.path.endsWith('/reviews'))!;
    expect(JSON.parse(String(review.init?.body))).toEqual({ decision: 'RETURN', opinion: '请补充研究方向', recordVersion: 7, submittedVersion: 3 });
  });
  it('keeps the same pre-review idempotency key when retrying a lost response', async () => {
    actor = { ...actor, roleCode: 'INTERNAL_TOPIC_UNIT', unitId: '2', actionPermissions: ['achievement.submit'] };
    const baseFetch = vi.mocked(fetch).getMockImplementation()!;
    let first = true;
    const keys: string[] = [];
    vi.mocked(fetch).mockImplementation(async (url, init) => {
      if (String(url).endsWith('/actions')) {
        keys.push(new Headers(init?.headers).get('Idempotency-Key')!);
        if (first) { first = false; throw new TypeError('网络响应中断'); }
      }
      return baseFetch(url, init);
    });
    render(<AchievementEntryPage />);
    fireEvent.click(await screen.findByRole('button', { name: '提交预审' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认操作' }));
    await waitFor(() => expect(screen.getByText('网络响应中断')).toBeVisible());
    expect(record.status).toBe('DRAFT');
    fireEvent.click(screen.getByRole('button', { name: '确认操作' }));
    await waitFor(() => expect(record.status).toBe('PRE_INITIAL'));
    expect(keys).toHaveLength(2); expect(keys[0]).toBe(keys[1]);
  });
  it('does not report formal success when the file foundation returns 503', async () => {
    actor = { ...actor, roleCode: 'INTERNAL_TOPIC_UNIT', unitId: '2', actionPermissions: ['achievement.submit'] };
    record = { ...record, status: 'FORMAL_DRAFT' };
    const baseFetch = vi.mocked(fetch).getMockImplementation()!;
    vi.mocked(fetch).mockImplementation(async (url, init) => String(url).endsWith('/actions')
      ? json({ status: 503, code: 'FILE_FOUNDATION_UNAVAILABLE', detail: '成果文件能力尚未接入' }, 503) : baseFetch(url, init));
    render(<AchievementEntryPage />);
    fireEvent.click(await screen.findByRole('button', { name: '提交正式审核' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认操作' }));
    await waitFor(() => expect(screen.getByText('成果文件能力尚未接入')).toBeVisible());
    expect(record.status).toBe('FORMAL_DRAFT');
    expect(screen.getByRole('button', { name: '确认操作' })).toBeVisible();
  });
  it('separates historic totals, renders null targets honestly and allows rates above 100%', async () => {
    const stages = { initiated: 2, preApproved: 2, external: 0, formal: 0, supplement: 0, effective: 2 };
    const row: AchievementProgressRow = { scope: 'UNIT', topicId: '1', unitId: '2', nodeId: '1', indicatorDefinitionId: '1', achievementType: 'PAPER', targetQuantity: 1, targetVersion: 1, targetPublished: true, hasTarget: true, completionRate: 200, historical: false, stages };
    vi.mocked(fetch).mockResolvedValue(json({ nodeId: '1', countingBasis: 'CUMULATIVE_NODE_CURRENT_FACTS', baseTotals: { PAPER: 2, PATENT: 0, COPYRIGHT: 0, STANDARD: 0, TALENT: 0 }, baseStages: stages,
      rows: [row, { ...row, scope: 'TOPIC', unitId: null, targetQuantity: 0, hasTarget: false, completionRate: null }, { ...row, unitId: '9', historical: true, stages: { ...stages, effective: 99 } }], specialIndicators: [] }));
    render(<RealAchievementProgress nodeId="1" topics={[topic]} units={units} definitions={definitions} revision={0} />);
    expect(await screen.findByText('200.00%')).toBeVisible();
    expect(screen.getByText('无正目标')).toBeVisible();
    expect(screen.queryByText('99')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: '停用历史（不计当前汇总）' }));
    expect(await screen.findByText('99')).toBeVisible();
    const statistic = screen.getByText('论文生效').closest('.ant-statistic')!;
    expect(within(statistic as HTMLElement).getByText('2')).toBeVisible();
  });
});
