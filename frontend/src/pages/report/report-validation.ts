export function validDemonstrationProgress(value: unknown): boolean {
  if (typeof value !== 'string') return false;
  const content = value.trim();
  return content === '无' || Array.from(content).length >= 300;
}
