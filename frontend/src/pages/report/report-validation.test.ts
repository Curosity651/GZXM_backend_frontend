import { describe, expect, it } from 'vitest';
import { validDemonstrationProgress } from './report-validation';

describe('示范工程进展提交规则', () => {
  it('只允许至少三百字或单独填写无', () => {
    expect(validDemonstrationProgress('进'.repeat(299))).toBe(false);
    expect(validDemonstrationProgress('进'.repeat(300))).toBe(true);
    expect(validDemonstrationProgress(' 无 ')).toBe(true);
    expect(validDemonstrationProgress('暂无')).toBe(false);
  });
});
