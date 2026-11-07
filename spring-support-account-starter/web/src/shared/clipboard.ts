/**
 * 剪贴板工具 —— 统一的「复制到剪贴板」实现。
 *
 * <p>优先用异步 Clipboard API；不可用（非安全上下文 / 旧浏览器 / 权限被拒）时
 * 回退到隐藏 textarea + execCommand。两种方式都失败才抛错，由调用方决定提示文案。</p>
 *
 * @param text 待复制文本
 * @returns 复制成功返回 true
 */
export async function copyText(text: string | null | undefined): Promise<boolean> {
  const value = text == null ? '' : String(text);
  if (!value) {
    return false;
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(value);
      return true;
    }
  } catch {
    // 落到 execCommand 回退
  }
  try {
    const ta = document.createElement('textarea');
    ta.value = value;
    ta.setAttribute('readonly', '');
    ta.style.cssText = 'position:fixed;top:0;left:0;opacity:0;pointer-events:none';
    document.body.appendChild(ta);
    ta.select();
    ta.setSelectionRange(0, value.length);
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}
