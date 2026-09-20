import { describe, expect, it } from 'vitest';
import { classifyUnitProgress } from './achievement-progress';

describe('classifyUnitProgress', () => {
  it('distinguishes an unpublished allocation from a published zero allocation', () => {
    expect(classifyUnitProgress(false, 0, 0)).toBe('UNALLOCATED');
    expect(classifyUnitProgress(true, 0, 0)).toBe('NOT_REQUIRED');
  });

  it('classifies submitted progress after allocation', () => {
    expect(classifyUnitProgress(true, 3, 0)).toBe('NOT_SUBMITTED');
    expect(classifyUnitProgress(true, 3, 1)).toBe('PARTIAL');
    expect(classifyUnitProgress(true, 3, 3)).toBe('COMPLETE');
  });
});
