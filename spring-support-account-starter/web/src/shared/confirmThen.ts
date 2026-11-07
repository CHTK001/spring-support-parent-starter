/**
 * 把「取消即 reject」的确认框包成布尔语义。
 *
 * <p><b>为什么需要它</b>：{@code @repo/core} 的 {@code message.confirm}
 * 底层是 {@code msgBox}，它在用户点「取消」时走的是
 * {@code .catch(() => reject(false))} —— 也就是<b>抛异常</b>而不是返回
 * {@code false}。</p>
 *
 * <p>这带来两个真实后果：</p>
 * <ol>
 *   <li>写成 {@code await message.confirm(...)} 且外面没有 try/catch 时，
 *       「取消」会产生一个<b>未捕获的 Promise rejection</b>。删除类操作里
 *       它是每次取消都往控制台刷一条红字；一旦全局挂了
 *       {@code unhandledrejection} 上报，用户刚点「取消」就收到一个
 *       「系统繁忙」提示 —— 明明是他自己取消的。</li>
 *   <li>更隐蔽的一种写法是 {@code if (await message.confirm(...))}：
 *       取消时直接抛穿，后面的语句不执行，看起来「恰好是对的」，
 *       于是没人发现这个函数其实靠抛异常来表达「不」，
 *       下一个改它的人很容易把 catch 补成「吞掉所有异常」，
 *       把真正的删除失败也一起吞掉。</li>
 * </ol>
 *
 * <p>本函数把语义摆正：<b>确认返回 true，取消返回 false，永不抛出</b>。
 * 确认框本身的失败（弹不出来等）也算 false —— 拿不到用户的明确同意
 * 就不该继续执行破坏性操作。</p>
 */

/**
 * 等待一次确认，拿到布尔结果。
 *
 * @param confirmFn 确认框函数，签名需与 `message.confirm` 兼容
 * @param args      透传给确认框的参数
 * @returns 用户确认返回 true；取消、关闭、或确认框自身抛错一律返回 false
 */
export async function confirmThen<T extends unknown[]>(
  confirmFn: (...args: T) => Promise<unknown>,
  ...args: T
): Promise<boolean> {
  try {
    await confirmFn(...args);
    return true;
  } catch {
    // 取消与「弹不出来」在这里不可区分，但对调用方而言处理方式相同：
    // 都没有拿到明确同意，因此都不继续。不要把异常往上抛 ——
    // 那正是本函数要消除的行为。
    return false;
  }
}