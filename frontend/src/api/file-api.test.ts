import { describe, expect, it } from 'vitest';
import { canPreviewFile } from './file-api';

describe('文件预览类型', () => {
  it('仅将安全的文档和栅格图片送入浏览器预览', () => {
    expect(canPreviewFile('application/pdf')).toBe(true);
    expect(canPreviewFile('image/png')).toBe(true);
    expect(canPreviewFile('text/html')).toBe(false);
    expect(canPreviewFile('image/svg+xml')).toBe(false);
    expect(canPreviewFile('text/html; charset=utf-8')).toBe(false);
  });
});
