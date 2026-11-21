package com.chua.starter.logger.event;

/**
 * 插件操作日志事件。
 *
 * <p>一次写操作会产生三条事件：{@code start}（开始）、{@code success}（成功）、
 * {@code failure}（失败）。前端实时日志抽屉按 {@code action + symbolicName + 时间}
 * 串成一条时间线，失败时能直接看到原因，不必再去翻服务端日志。</p>
 *
 * <p><b>为什么带 {@code duration}</b>：安装 / 升级要落盘、解析 manifest、重启 bundle，
 * 耗时从几百毫秒到几秒不等。有了耗时，「为什么这次装得特别慢」这类问题不用再复现。</p>
 *
 * <p><b>契约归属</b>：本事件是 osgi-starter（生产方，经 Spring 应用事件发布）与
 * socket-starter（消费方，SocketOsgiLogBridge 监听后广播到前端）的共享契约，
 * 定义在最底层的 logger-starter，避免 osgi 直接依赖 socket 形成
 * {@code common -> osgi -> socket -> common} 的依赖环。</p>
 *
 * @param level        日志级别：{@code INFO} / {@code WARN} / {@code ERROR}
 * @param status       阶段：{@code start} / {@code success} / {@code failure}
 * @param action       操作类型：{@code install} / {@code update} / {@code start} /
 *                     {@code stop} / {@code uninstall} / {@code delete-jar} / 读操作名
 * @param symbolicName 插件符号名；清单类操作可能为空
 * @param message      可展示的说明或错误信息
 * @param startedAt    操作开始时间戳（毫秒）
 * @param duration     操作耗时（毫秒）；{@code start} 阶段为 0
 * @author CH
 * @since 4.0.0.51
 */
public record OsgiLogEvent(String level,
                           String status,
                           String action,
                           String symbolicName,
                           String message,
                           long startedAt,
                           long duration) {

    /**
     * 日志级别：普通信息。
     */
    public static final String LEVEL_INFO = "INFO";

    /**
     * 日志级别：警告。
     */
    public static final String LEVEL_WARN = "WARN";

    /**
     * 日志级别：错误。
     */
    public static final String LEVEL_ERROR = "ERROR";

    /**
     * 阶段：开始。
     */
    public static final String STATUS_START = "start";

    /**
     * 阶段：成功。
     */
    public static final String STATUS_SUCCESS = "success";

    /**
     * 阶段：失败。
     */
    public static final String STATUS_FAILURE = "failure";

    /**
     * 构造「开始」事件。
     *
     * @param action       操作类型
     * @param symbolicName 插件符号名，可为空
     * @return 开始事件
     */
    public static OsgiLogEvent start(String action, String symbolicName) {
        return new OsgiLogEvent(LEVEL_INFO, STATUS_START, action, symbolicName, "", System.currentTimeMillis(), 0L);
    }

    /**
     * 构造「成功」事件。
     *
     * @param action       操作类型
     * @param symbolicName 插件符号名，可为空
     * @param startedAt    操作开始时间戳
     * @param message      结果说明
     * @return 成功事件
     */
    public static OsgiLogEvent success(String action, String symbolicName, long startedAt, String message) {
        long now = System.currentTimeMillis();
        return new OsgiLogEvent(LEVEL_INFO, STATUS_SUCCESS, action, symbolicName, message, startedAt, now - startedAt);
    }

    /**
     * 构造「失败」事件。
     *
     * @param action       操作类型
     * @param symbolicName 插件符号名，可为空
     * @param startedAt    操作开始时间戳
     * @param message      失败原因
     * @return 失败事件
     */
    public static OsgiLogEvent failure(String action, String symbolicName, long startedAt, String message) {
        long now = System.currentTimeMillis();
        return new OsgiLogEvent(LEVEL_ERROR, STATUS_FAILURE, action, symbolicName, message, startedAt, now - startedAt);
    }

    /**
     * 构造即时「信息」事件（读操作、下载等没有开始/结束配对的场景）。
     *
     * @param action       操作类型
     * @param symbolicName 插件符号名，可为空
     * @param message      说明
     * @return 信息事件
     */
    public static OsgiLogEvent info(String action, String symbolicName, String message) {
        long now = System.currentTimeMillis();
        return new OsgiLogEvent(LEVEL_INFO, STATUS_SUCCESS, action, symbolicName, message, now, 0L);
    }

    /**
     * 构造即时「警告」事件。
     *
     * @param action       操作类型
     * @param symbolicName 插件符号名，可为空
     * @param message      说明
     * @return 警告事件
     */
    public static OsgiLogEvent warn(String action, String symbolicName, String message) {
        long now = System.currentTimeMillis();
        return new OsgiLogEvent(LEVEL_WARN, STATUS_FAILURE, action, symbolicName, message, now, 0L);
    }
}
