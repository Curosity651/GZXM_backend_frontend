// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../http-client';
import { allTopics, configureResearchMetadataRequest, query, ResearchOperationKeys, researchApi, type MetadataRequest } from './client';

const request = vi.fn<typeof fetch>();
beforeEach(() => { vi.stubGlobal('fetch', request); request.mockReset(); request.mockResolvedValue(new Response('[]', { status: 200 })); });
afterEach(() => { configureResearchMetadataRequest(undefined); vi.unstubAllGlobals(); });

describe('B real API client', () => {
  it('keeps string IDs exact and encodes filters without dropping zero or false', () => {
    expect(query({ nodeId: '9223372036854775806', page: 0, pendingForMe: false, keyword: 'A&B', absent: undefined })).toBe('?nodeId=9223372036854775806&page=0&pendingForMe=false&keyword=A%26B');
  });
  it('maps topic and membership operations to the contract', async () => {
    const body = { code: 'T', name: '测试课题', leadUnitId: '9223372036854775806', recordVersion: 3 };
    await researchApi.topics({ page: 2, size: 20 });
    request.mockResolvedValue(new Response('{}'));
    await researchApi.topic('123');
    request.mockResolvedValue(new Response('{}'));
    await researchApi.createTopic(body);
    request.mockResolvedValue(new Response('{}'));
    await researchApi.updateTopic('123', body);
    request.mockResolvedValue(new Response('{}'));
    await researchApi.topicStatus('123', { enabled: false, status: 'PAUSED' });
    request.mockResolvedValue(new Response('[]'));
    await researchApi.members('123');
    request.mockResolvedValue(new Response('{}'));
    await researchApi.addMember('123', '456');
    request.mockResolvedValue(new Response('{}'));
    await researchApi.memberStatus('123', '789', true);
    expect(request.mock.calls.map(([url, init]) => [url, init?.method ?? 'GET'])).toEqual([
      ['/api/v1/topics?page=2&size=20', 'GET'], ['/api/v1/topics/123', 'GET'], ['/api/v1/topics', 'POST'],
      ['/api/v1/topics/123', 'PUT'], ['/api/v1/topics/123/status', 'PUT'], ['/api/v1/topics/123/members', 'GET'],
      ['/api/v1/topics/123/members', 'POST'], ['/api/v1/topics/123/members/789/status', 'PUT'],
    ]);
    expect(JSON.parse(String(request.mock.calls[3][1]?.body))).toEqual(body);
  });
  it('sends versions and identical retry keys for actions and reviews', async () => {
    const keys = new ResearchOperationKeys();
    const body = { action: 'SUBMIT_PRE_REVIEW' as const, recordVersion: 8 };
    const key = keys.forRequest('action:1', body);
    await researchApi.action('1', body, key);
    request.mockResolvedValue(new Response('{}'));
    await researchApi.action('1', body, keys.forRequest('action:1', { ...body }));
    expect(new Headers(request.mock.calls[0][1]?.headers).get('Idempotency-Key')).toBe(new Headers(request.mock.calls[1][1]?.headers).get('Idempotency-Key'));
    expect(keys.forRequest('action:1', { ...body, recordVersion: 9 })).not.toBe(key);
    request.mockResolvedValue(new Response('{}'));
    await researchApi.review('1', { decision: 'RETURN', opinion: '请补充', recordVersion: 8, submittedVersion: 2 }, 'review-test-key');
    expect(JSON.parse(String(request.mock.calls[2][1]?.body))).toEqual({ decision: 'RETURN', opinion: '请补充', recordVersion: 8, submittedVersion: 2 });
    expect(request.mock.calls[2][0]).toBe('/api/v1/achievements/1/reviews');
  });
  it('uses achievement CRUD/history/progress endpoints and never invents unit ownership', async () => {
    const body = { topicId: '1', nodeId: '2', indicatorDefinitionId: '3', title: '草稿', responsiblePerson: '测试负责人', detail: { isChineseCoreJournal: false } };
    const calls = [() => researchApi.achievements({ pendingForMe: true }), () => researchApi.achievement('9'), () => researchApi.createAchievement(body),
      () => researchApi.updateAchievement('9', { ...body, recordVersion: 4 }), () => researchApi.snapshots('9'), () => researchApi.progress('2', '1', '8')];
    for (const call of calls) { request.mockResolvedValueOnce(new Response('{}')); await call(); }
    expect(request.mock.calls.map(([url]) => url)).toEqual(['/api/v1/achievements?pendingForMe=true', '/api/v1/achievements/9', '/api/v1/achievements', '/api/v1/achievements/9', '/api/v1/achievements/9/snapshots', '/api/v1/achievement-progress?nodeId=2&topicId=1&unitId=8']);
    expect(JSON.parse(String(request.mock.calls[2][1]?.body))).toEqual(body);
    expect(JSON.parse(String(request.mock.calls[3][1]?.body))).not.toHaveProperty('materialAttachments');
  });
  it.each([403, 409, 422, 503])('preserves HTTP %s errors without pretending a save succeeded', async status => {
    request.mockResolvedValueOnce(new Response(JSON.stringify({ status, code: 'BUSINESS_TEST', detail: '业务拒绝' }), { status }));
    await expect(researchApi.achievement('1')).rejects.toMatchObject({ problem: { status, code: 'BUSINESS_TEST' }, message: '业务拒绝' });
    expect(request).toHaveBeenCalledTimes(1);
  });
  it('refuses draft edits before A provides metadata; does not guess version zero', async () => {
    await expect(researchApi.targetDraft('1', '2')).rejects.toThrow('公共 HTTP');
    await expect(researchApi.saveAllocations('1', { nodeId: '2', allocations: [] })).rejects.toThrow('公共 HTTP');
    expect(request).not.toHaveBeenCalled();
  });
  it('retains empty draft versions and allocation target revision from the adapter', async () => {
    const adapter: MetadataRequest = async <T>() => ({ data: [] as T, headers: new Headers({ 'X-Draft-Version': '7', 'X-Topic-Indicator-Version': '3' }) });
    configureResearchMetadataRequest(adapter);
    expect(await researchApi.targetDraft('1', '2')).toEqual({ items: [], draftVersion: 7 });
    expect(await researchApi.allocationDraft('1', '2')).toEqual({ items: [], draftVersion: 7, topicIndicatorVersion: 3 });
  });
  it.each([null, 'bad', '-1', '2147483648', '9007199254740993'])('rejects missing/invalid metadata: %s', async value => {
    configureResearchMetadataRequest(async <T>() => ({ data: [] as T, headers: new Headers(value === null ? {} : { 'X-Draft-Version': value }) }));
    await expect(researchApi.targetDraft('1', '2')).rejects.toThrow('X-Draft-Version');
  });
  it('saves a full empty replacement with its version and preserves publication keys', async () => {
    const adapter = vi.fn().mockResolvedValue({ data: [], headers: new Headers({ 'X-Draft-Version': '9', 'X-Topic-Indicator-Version': '3' }) });
    configureResearchMetadataRequest(adapter);
    await researchApi.saveTargets('1', { nodeId: '2', draftVersion: 8, targets: [] });
    await researchApi.saveAllocations('1', { nodeId: '2', draftVersion: 8, allocations: [] });
    expect(adapter.mock.calls.map(call => [call[0], JSON.parse(call[1].body)])).toEqual([
      ['/topics/1/indicator-targets', { nodeId: '2', draftVersion: 8, targets: [] }],
      ['/topics/1/unit-allocations', { nodeId: '2', draftVersion: 8, allocations: [] }],
    ]);
    request.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await researchApi.publishTargets('1', { nodeId: '2', draftVersion: 9 }, 'target-publish-key');
    request.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await researchApi.publishAllocations('1', { nodeId: '2', draftVersion: 9 }, 'allocation-publish-key');
    expect(new Headers(request.mock.calls[0][1]?.headers).get('Idempotency-Key')).toBe('target-publish-key');
    expect(request.mock.calls[1][0]).toBe('/api/v1/topics/1/unit-allocations:publish');
  });
  it('loads all authorized topic pages instead of silently truncating the selector', async () => {
    request.mockResolvedValueOnce(new Response(JSON.stringify({ items: [{ id: '1' }], total: 2, page: 1, size: 100 })));
    request.mockResolvedValueOnce(new Response(JSON.stringify({ items: [{ id: '2' }], total: 2, page: 2, size: 100 })));
    expect((await allTopics()).map(item => item.id)).toEqual(['1', '2']);
    expect(request.mock.calls[1][0]).toBe('/api/v1/topics?page=2&size=100');
  });
  it('uses public catalogue services and keeps a denied catalogue request denied', async () => {
    for (const call of [researchApi.nodes, researchApi.definitions, researchApi.units, () => researchApi.targets('1', '2'), () => researchApi.allocations('1', '2')]) {
      request.mockResolvedValueOnce(new Response('[]')); await call();
    }
    expect(request.mock.calls.map(([url]) => url)).toEqual(['/api/v1/time-nodes', '/api/v1/indicator-definitions', '/api/v1/units', '/api/v1/topics/1/indicator-targets?nodeId=2', '/api/v1/topics/1/unit-allocations?nodeId=2']);
    request.mockResolvedValueOnce(new Response(JSON.stringify({ status: 403 }), { status: 403 }));
    await expect(researchApi.nodes()).rejects.toBeInstanceOf(ApiError);
  });
});
