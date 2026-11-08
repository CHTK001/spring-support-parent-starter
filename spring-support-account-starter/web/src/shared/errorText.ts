/**
 * 异常文案归一化。
 *
 * <p>为什么不用 {@code String(err)}：模板库是从模板应用整块搬进账号项目的，
 * 原先的写法把异常直接转字符串，网络层抛出的 {@code AxiosError} 会被渲染成
 * 一个光秃秃的 "AxiosError"（AxiosError 的 toString 不带上 message），
 * 用户看到后完全无从判断是后端没起、超时还是 500。</p>
 *
 * <p>这里按「后端 msg → Error.message → 原始字符串」三级取第一个非空值，
 * 后端统一信封 {@code {success:false, msg}} 的文案也能直接露出来。</p>
 *
 * @param error 捕获到的异常
 * @param fallback 全部取不到时的兜底文案
 * @returns 可直接展示给用户的文本
 */
export function errorText(error: unknown, fallback = '操作失败'): string {
  if (error === null || error === undefined) return fallback;
  if (typeof error === 'string') return error.trim() || fallback;

  const anyError = error as {
    name?: unknown;
    message?: unknown;
    msg?: unknown;
    code?: unknown;
    status?: unknown;
    response?: { status?: unknown; data?: { msg?: unknown; message?: unknown } | unknown };
  };

  const body = anyError.response?.data;
  if (body && typeof body === 'object') {
    const fromBody = (body as { msg?: unknown; message?: unknown }).msg ?? (body as { message?: unknown }).message;
    if (typeof fromBody === 'string' && fromBody.trim()) return fromBody.trim();
  }

  const msg = anyError.msg ?? anyError.message;
  if (typeof msg === 'string' && msg.trim()) return msg.trim();

  // 走到这里说明 message 被清空了：框架的 5xx/网络拦截器在弹过自己的 toast 后，
  // 会把 error.message 置空（避免调用方把原始错误直出）。此时只能靠状态码 / 错误码
  // 还原一句人话——否则 String(error) 只会得到光秃秃的 "AxiosError"。
  const status = Number(anyError.response?.status ?? anyError.status);
  if (Number.isFinite(status) && status > 0) {
    if (status === 502 || status === 503 || status === 504) {
      return `服务暂时不可用（HTTP ${status}），请确认对应后端服务已启动`;
    }
    return `请求失败（HTTP ${status}）`;
  }

  const code = typeof anyError.code === 'string' ? anyError.code : '';
  if (code === 'ERR_NETWORK') return '网络连接失败，请确认后端服务已启动';
  if (code === 'ECONNABORTED' || code === 'ETIMEDOUT' || code === 'ERR_TIMEOUT') {
    return '请求超时，请稍后重试';
  }

  const text = String(error);
  // Error 子类名（AxiosError / CanceledError …）本身不带信息量，不如给兜底文案。
  if (!text || text === '[object Object]' || /^[A-Za-z]*Error$/.test(text)) return fallback;
  return text;
}

/**
 * 是否为「用户主动取消」。
 *
 * <p>取消不是失败：{@code ElMessageBox} 取消会抛 cancel / close，
 * 这类异常不该弹红色错误提示。</p>
 *
 * @param error 捕获到的异常
 * @returns 是否是取消
 */
export function isCancelled(error: unknown): boolean {
  if (typeof error === 'string') return error === 'cancel' || error === 'close';
  const text = String((error as { message?: unknown } | null)?.message ?? error ?? '');
  return text === 'cancel' || text === 'close' || text === 'Error: cancel';
}
