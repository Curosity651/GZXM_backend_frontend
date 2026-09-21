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
  const selectedDefinitionId = Form.useWatch('indicatorDefinitionId', form);
  const selectedNodeId = Form.useWatch('nodeId', form);
  const unit = units.find((item) => item.id === currentUnitId);
  const typeLabels: Record<string, string> = { PAPER: '学术论文', PATENT: '发明专利', COPYRIGHT: '软件著作权', STANDARD: '标准规范', TALENT: '人才培养' };
  const baseDefinitions = ['PAPER', 'PATENT', 'COPYRIGHT', 'STANDARD', 'TALENT']
    .map((type) => definitions.find((item) => item.enabled && item.category === 'BASE' && item.achievementType === type))
    .filter((item): item is IndicatorDefinition => Boolean(item));
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
                onChange={() => form.setFieldsValue({ indicatorDefinitionId: undefined, achievementType: undefined })}
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
            <Form.Item label="对应成果指标" name="indicatorDefinitionId" rules={[{ required: true, message: '请选择成果指标' }]}>
              <Select
                placeholder={topicId ? '选择成果指标' : '请先选择课题'}
                disabled={!topicId || lockOwnership}
                onChange={(definitionId: string) => {
                  const definition = definitions.find((item) => item.id === definitionId);
                  form.setFieldsValue({
                    achievementType: definition?.achievementType,
                  });
                }}
                options={baseDefinitions.map((definition) => ({ label: typeLabels[definition.achievementType] ?? definition.name, value: definition.id }))}
              />
            </Form.Item>
            <Form.Item name="achievementType" hidden><Input /></Form.Item>
            <Form.Item name="nodeId" hidden rules={[{ required: true, message: '未配置有效考核节点' }]}><Input /></Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="考核节点">
              <Input value={selectedNode ? `${selectedNode.name}（${selectedNode.deadline}，系统自动归属）` : '未配置有效考核节点'} disabled />
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
