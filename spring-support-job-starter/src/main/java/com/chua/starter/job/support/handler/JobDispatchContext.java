package com.chua.starter.job.support.handler;

/**
 * 当前这一拍派发的上下文。
 *
 * <p>{@link JobHandler#execute()} 不带参数，调度器只能把「为什么被叫起来」放到线程上下文里：
 * 定时循环到期和有人在作业台点了「立即运行」对处理器是同一次调用，但对触发方式的记账完全不同。
 * 值由 {@code JobExecutor} 在回调前后写入和清除，业务侧只读。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class JobDispatchContext {

    /**
     * 手动派发标记
     */
    private static final ThreadLocal<Boolean> MANUAL = new ThreadLocal<>();

    /**
     * 本轮执行参数（台账 {@code sys_job_execute_param}，随命令下发）
     */
    private static final ThreadLocal<String> PARAM = new ThreadLocal<>();

    /**
     * 创建 作业派发上下文 实例
     */
    private JobDispatchContext() {
    }

    /**
     * 写入本轮派发来源 (由执行器调用)。
     *
     * @param manual 手动派发传 true
     */
    public static void begin(boolean manual) {
        begin(manual, null);
    }

    /**
     * 写入本轮派发来源与执行参数 (由执行器调用)。
     *
     * @param manual       手动派发传 true
     * @param executeParam 台账执行参数，可为空
     */
    public static void begin(boolean manual, String executeParam) {
        MANUAL.set(manual);
        PARAM.set(executeParam);
    }

    /**
     * 清除本轮派发来源 (由执行器调用)。
     */
    public static void end() {
        MANUAL.remove();
        PARAM.remove();
    }

    /**
     * 本轮任务键对应的执行参数（台账 sys_job_execute_param，原样字符串）。
     *
     * <p>处理器无入参，需要自定义参数的处理器在 {@code execute()} 里从这里读：
     * {@code String param = JobDispatchContext.param();}</p>
     *
     * @return 执行参数，未配置返回 空
     */
    public static String param() {
        return PARAM.get();
    }

    /**
     * 这一拍是不是人点出来的。
     *
     * @return 手动派发返回 true; 不在派发线程上（如本地直接调用）返回 false
     */
    public static boolean isManual() {
        return Boolean.TRUE.equals(MANUAL.get());
    }
}
