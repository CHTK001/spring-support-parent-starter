package com.chua.starter.common.support.log;

import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * MDC 的 {@link ThreadLocalAccessor} 适配器：把 SLF4J MDC 线程变量接入
 * micrometer {@code context-propagation} 体系。
 *
 * <p>配合 {@code Hooks.enableAutomaticContextPropagation()} 使用后，Reactor
 * {@code Flux}/{@code Mono} 链路在订阅时自动快照当前线程 MDC，并在任意信号
 * （onNext / onError / onComplete）执行前恢复、执行后还原，覆盖 flatMap 内层、
 * publishOn / subscribeOn 切换、WebClient 回调线程等场景，无需逐点包装。</p>
 *
 * <p>恢复栈语义：{@link #setValue} 先压入当前现场再应用快照，{@link #restore}
 * 弹出最近一次压入的现场还原，保证信号处理嵌套时逐层正确回滚，不污染
 * Reactor 复用线程的下一次任务。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public class MdcThreadLocalAccessor implements ThreadLocalAccessor<Map<String, String>> {

    /**
     * 注册键：在 {@code ContextRegistry} 中的唯一标识。
     */
    public static final String KEY = "mdc";

    /**
     * 每线程恢复栈：保存每次 setValue 之前的 MDC 现场。
     */
    private static final ThreadLocal<Deque<Map<String, String>>> PREVIOUS = new ThreadLocal<>();

    /**
     * 注册键。
     * @return 固定返回 {@link #KEY}
     */
    @Override
    public Object key() {
        return KEY;
    }

    /**
     * 读取当前线程 MDC 快照。
     * @return MDC 键值副本；无线上下文时为 {@code null}
     */
    @Override
    public Map<String, String> getValue() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * 应用快照：先保存当前现场（供 restore 回滚），再设置快照值。
     * @param value 订阅线程的 MDC 快照，可为 {@code null}
     */
    @Override
    public void setValue(Map<String, String> value) {
        previousStack().push(MDC.getCopyOfContextMap());
        apply(value);
    }

    /**
     * 信号处理结束：弹出并恢复最近一次 setValue 前的现场。
     */
    @Override
    public void restore() {
        Deque<Map<String, String>> stack = PREVIOUS.get();
        Map<String, String> previous = (stack == null || stack.isEmpty()) ? null : stack.pop();
        if (stack != null && stack.isEmpty()) {
            PREVIOUS.remove();
        }
        apply(previous);
    }

    /**
     * 取当前线程恢复栈，不存在时创建。
     * @return 恢复栈
     */
    private static Deque<Map<String, String>> previousStack() {
        Deque<Map<String, String>> stack = PREVIOUS.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            PREVIOUS.set(stack);
        }
        return stack;
    }

    /**
     * 应用 MDC 快照：空快照表示清理，避免污染复用线程。
     * @param snapshot MDC 快照
     */
    private static void apply(Map<String, String> snapshot) {
        if (snapshot == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(snapshot);
        }
    }
}
