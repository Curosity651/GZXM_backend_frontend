import { Alert, Form, Input } from 'antd';
import type { FormInstance } from 'antd';
import type { ProgressReport } from '../../types';

export function ReportForm({ form, disabled = false }: { form: FormInstance<Partial<ProgressReport>>; disabled?: boolean }) {
  return <Form form={form} layout="vertical" disabled={disabled}>
    <Alert
      type="info"
      showIcon
      title="说明：本表整合月报与季报的填写模块与要求，一次填写即可同时满足两类报表需求。各模块撰写要求已融合月报与季报标准，请按统一要求填写。"
      style={{ marginBottom: 16 }}
    />
    <Form.Item
      label="0. 基本信息"
      name="basicInformation"
      extra="填写项目编号、项目名称、填报人姓名及联系电话。"
      rules={[{ required: true }]}
    >
      <Input.TextArea
        rows={4}
        placeholder={'项目编号：202YXXX0XX000\n项目名称：高比例新能源省域电网安全高效调控关键技术研究及示范\n填报人：张三\n联系电话：138XXXX1234'}
      />
    </Form.Item>
    <Form.Item
      label="1. 项目管理进展"
      name="overallProgress"
      extra="简述立项进展、团队组建、重大会议等组织管理工作，分项列举，语言凝练。"
      rules={[{ required: true }]}
    >
      <Input.TextArea
        rows={4}
        showCount
        maxLength={500}
        placeholder={'立项进展：XXX\n重大会议：XXX\n组织管理：XXX\nXXX'}
      />
    </Form.Item>
    <Form.Item
      label="2. 研究进展与里程碑"
      name="milestoneProgress"
      extra="包含研究进展和里程碑节点完成情况，分项列举，重点汇报关键技术指标及装备/系统研制情况。"
      rules={[{ required: true }]}
    >
      <Input.TextArea
        rows={4}
        showCount
        maxLength={800}
        placeholder={'研究进展：XXX\n里程碑节点1：XXX\n里程碑节点2：XXX\nXXX'}
      />
    </Form.Item>
    <Form.Item
      label="3. 科研成果"
      name="researchAchievements"
      extra="分项列举研制装备、技术指标、论文、专利、技术标准/规范等科研成果。"
      rules={[{ required: true }]}
    >
      <Input.TextArea
        rows={5}
        showCount
        maxLength={800}
        placeholder={'1.研制了XX装备/系统，达到指标XX，性能优于预期。\n2.发表论文XX篇，其中SCI/EI期刊论文XX篇。\n3.受理专利XX项，其中已授权XX项。\n4.形成技术标准/规范XX项。'}
      />
    </Form.Item>
    <Form.Item
      label="4. 示范工程进展"
      name="demonstrationProgress"
      extra={'300字以内，分项列举示范工程建设进展、各课题科研成果应用情况，重点汇报工程批复、建设方案过审情况等。如无则填"无"。'}
      rules={[{ required: true }]}
    >
      <Input.TextArea
        rows={5}
        showCount
        maxLength={300}
        placeholder={'项目完成XX个示范工程的初设/可研，进展如下：\n1.完成示范工程批复，开展工程设计及建设。\n2.完成工程纳规、核准并取得施工许可证，于XX日正式开工。\n3.工程土建进度XX%，设备到货XX%。'}
      />
    </Form.Item>
    <Form.Item
      label="5. 经费使用情况"
      name="fundUsage"
      extra="项目及各课题分段列举，含国拨经费拨付及使用情况、其他来源资金到位及使用情况。经费执行率按执行期内的预算总额计算。"
      rules={[{ required: true }]}
    >
      <Input.TextArea
        rows={5}
        placeholder="项目方面：收到国拨经费XX万元，占总国拨经费X%，已完成经费转拨XX万元，支出经费XX万元，经费执行率为X%；配套经费到位XX万元，占总配套经费X%，支出经费XX万元，经费执行率为X%。"
      />
    </Form.Item>
    <Form.Item
      label="6. 下期工作计划"
      name="nextPlan"
      extra="按技术研究与装备研制、示范建设、组织管理、财务管理等分类，填写下期重点工作及目标。"
      rules={[{ required: true }]}
    >
      <Input.TextArea
        rows={5}
        placeholder={'1.技术研究与装备研制：XXX\n2.示范建设：南XXX\n3.组织管理：XXX\n4.财务管理：XXX\nXXX'}
      />
    </Form.Item>
    <Form.Item
      label="7. 存在问题与措施建议"
      name="problemsAndMeasures"
      extra={'填写项目推进中存在的问题、风险及对应的措施建议。如无则填"无"。'}
      rules={[{ required: true }]}
    >
      <Input.TextArea rows={4} placeholder={'1.问题：XXX\n  措施：XXX'} />
    </Form.Item>
  </Form>;
}
