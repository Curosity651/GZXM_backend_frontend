import { Card, Col, Form, Input, Row, Select } from 'antd';
import type { FormInstance } from 'antd';
import type { ApiTopic } from '../../api/topic-api';
import type { ApiUnit } from '../../api/system-api';
import type { IndicatorDefinition, TimeNode, UnitAllocation } from '../../api/indicator-api';
import { PaperFields } from './PaperFields';
import { PatentFields } from './PatentFields';
import { CopyrightFields } from './CopyrightFields';
import { StandardFields } from './StandardFields';
import { TalentFields } from './TalentFields';

const { Option } = Select;
const { TextArea } = Input;

interface AchievementFormProps {
  form: FormInstance;
  topics: ApiTopic[];
  units: ApiUnit[];
  lockOwnership?: boolean;
  definitions?: IndicatorDefinition[];
  project: { name: string; code: string };
  allocations: UnitAllocation[];
  nodes: TimeNode[];
  currentUnitId?: string;
  workflowStage?: 'PRE' | 'FORMAL' | 'SUPPLEMENT';
}

export function AchievementForm({ form, topics, units, lockOwnership = false, definitions = [], project, allocations, nodes, currentUnitId, workflowStage = 'PRE' }: AchievementFormProps) {
  const achievementType = Form.useWatch('achievementType', form);
  const topicId = Form.useWatch('topicId', form);
  const allocationId = Form.useWatch('unitIndicatorAllocationId', form);
  const unit = units.find((item) => item.id === currentUnitId);
  const activeNodes = nodes.filter((item) => item.enabled).sort((a, b) => a.sortOrder - b.sortOrder);
  const baseDefinitions = definitions.filter((item) => item.enabled && item.category === 'BASE');
  const [selectedNodeId, selectedDefinitionId] = String(allocationId ?? '').split(':');
  const selectedNode = nodes.find((item) => item.id === selectedNodeId);
  const selectedDefinition = definitions.find((item) => item.id === selectedDefinitionId);
  const selectedAllocation = allocations.find((item) => item.status === 'PUBLISHED' && item.unitId === currentUnitId
    && item.topicId === topicId && item.nodeId === selectedNodeId && item.indicatorDefinitionId === selectedDefinitionId);

  return (
    <div>
      <Card title="基本信息" size="small" style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item label="所属项目">
              <Input value={`${project.name}（${project.code}）`} disabled />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="所属课题" name="topicId" rules={[{ required: true, message: '请选择课题' }]}>
              <Select
                placeholder="选择课题"
                disabled={lockOwnership}
                onChange={() => form.setFieldsValue({ unitIndicatorAllocationId: undefined, indicatorDefinitionId: undefined, achievementType: undefined, nodeId: undefined })}
              >
                {topics.map((t) => (
                  <Option key={t.id} value={t.id}>{t.name}</Option>
                ))}
              </Select>
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="填报单位">
              <Input value={unit?.name ?? '未配置所属单位'} disabled />
            </Form.Item>
            <Form.Item name="unitId" hidden rules={[{ required: true, message: '缺少当前账号所属单位' }]}><Input /></Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="对应成果指标" name="unitIndicatorAllocationId" rules={[{ required: true, message: '请选择成果指标与考核节点' }]}>
              <Select
                placeholder={topicId ? '选择成果指标与考核节点' : '请先选择课题'}
                disabled={!topicId || lockOwnership}
                onChange={(id: string) => {
                  const [nodeId, definitionId] = id.split(':');
                  const definition = definitions.find((item) => item.id === definitionId);
                  form.setFieldsValue({
                    indicatorDefinitionId: definitionId,
                    achievementType: definition?.achievementType,
                    nodeId,
                  });
                }}
                options={activeNodes.flatMap((node) => baseDefinitions.map((definition) => {
                  const allocation = allocations.find((item) => item.status === 'PUBLISHED' && item.unitId === currentUnitId
                    && item.topicId === topicId && item.nodeId === node.id && item.indicatorDefinitionId === definition.id);
                  return { label: `${definition.name} · ${node.name}（目标 ${allocation?.targetQuantity ?? 0}）`, value: `${node.id}:${definition.id}` };
                }))}
              />
            </Form.Item>
            <Form.Item name="indicatorDefinitionId" hidden><Input /></Form.Item>
            <Form.Item name="achievementType" hidden><Input /></Form.Item>
            <Form.Item name="nodeId" hidden><Input /></Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="考核节点">
              <Input value={selectedNode ? `${selectedNode.name}（${selectedNode.deadline}）` : '请先选择成果指标'} disabled />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="本单位下发目标">
              <Input value={selectedDefinition ? `${selectedAllocation?.targetQuantity ?? 0} ${selectedDefinition.unit}` : '—'} disabled />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="成果名称/题目" name="title" rules={[{ required: true, message: '请输入成果名称' }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="填报负责人" name="responsiblePerson" rules={[{ required: true, message: '请填写填报负责人' }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item label="备注" name="remarks">
              <TextArea rows={2} />
            </Form.Item>
          </Col>
        </Row>
      </Card>

      {achievementType && (
        <Card title={achievementType === 'PAPER' ? '论文信息与作者' : achievementType === 'PATENT' ? '提案信息、发明人与申请人' : achievementType === 'COPYRIGHT' ? '软件信息、著作权人与技术特点' : achievementType === 'STANDARD' ? '标准规范详细信息' : '人才培养详细信息'} size="small" style={{ marginBottom: 16 }}>
          {achievementType === 'PAPER' && <PaperFields workflowStage={workflowStage} />}
          {achievementType === 'PATENT' && <PatentFields workflowStage={workflowStage} />}
          {achievementType === 'COPYRIGHT' && <CopyrightFields workflowStage={workflowStage} />}
          {achievementType === 'STANDARD' && <StandardFields formalStage={workflowStage === 'FORMAL'} />}
          {achievementType === 'TALENT' && <TalentFields formalStage={workflowStage === 'FORMAL'} />}
        </Card>
      )}
    </div>
  );
}
