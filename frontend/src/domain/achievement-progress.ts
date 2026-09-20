export type UnitProgressState = 'UNALLOCATED' | 'NOT_REQUIRED' | 'NOT_SUBMITTED' | 'PARTIAL' | 'COMPLETE';

export function classifyUnitProgress(allocated: boolean, target: number, submitted: number): UnitProgressState {
  if (!allocated) return 'UNALLOCATED';
  if (target <= 0) return 'NOT_REQUIRED';
  if (submitted <= 0) return 'NOT_SUBMITTED';
  if (submitted < target) return 'PARTIAL';
  return 'COMPLETE';
}
