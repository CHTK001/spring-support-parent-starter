package com.chua.starter.logger.event;

/**
 * OSGi bundle 生命周期状态事件。
 *
 * <p>由 osgi-starter 的状态事件桥（挂在框架级 {@code BundleLifecycleListener} 链上）
 * 作为 Spring 应用事件发布；宿主引入 socket-starter 时由 SocketOsgiStateBridge
 * 监听并广播到全局 socket 连接，前端经
 * {@code socket.subscribe("osgi", "bundle-state", handler)} 收到后实时刷新
 * bundle 状态墙，不必整表轮询 bundle 清单。</p>
 *
 * <p><b>契约归属</b>：与 {@link OsgiLogEvent} 同理，本事件是 osgi-starter（生产方）
 * 与 socket-starter（消费方）的共享契约，定义在最底层的 logger-starter，
 * 避免 {@code common -> osgi -> socket -> common} 的依赖环。</p>
 *
 * @param action       生命周期动作：{@code installed} / {@code started} / {@code stopped} /
 *                     {@code updated} / {@code uninstalled} / {@code state-changed} /
 *                     {@code removed} / {@code resolve-failed}
 * @param symbolicName bundle 符号名
 * @param oldState     状态变化前的状态（仅 {@code state-changed} 有值）
 * @param newState     变化后的状态（如 {@code ACTIVE} / {@code RESOLVED} / {@code UNINSTALLED}）
 * @param version      bundle 版本（{@code updated} 时为新版本、{@code removed} 时为卸载前版本）
 * @param message      附加说明（如解析失败原因），无则为空串
 * @param timestamp    事件时间戳（毫秒）
 * @author CH
 * @since 4.0.0.51
 */
public record OsgiStateEvent(String action,
                             String symbolicName,
                             String oldState,
                             String newState,
                             String version,
                             String message,
                             long timestamp) {

    /**
     * 动作：安装。
     */
    public static final String ACTION_INSTALLED = "installed";

    /**
     * 动作：启动。
     */
    public static final String ACTION_STARTED = "started";

    /**
     * 动作：停止。
     */
    public static final String ACTION_STOPPED = "stopped";

    /**
     * 动作：更新。
     */
    public static final String ACTION_UPDATED = "updated";

    /**
     * 动作：卸载。
     */
    public static final String ACTION_UNINSTALLED = "uninstalled";

    /**
     * 动作：状态变化。
     */
    public static final String ACTION_STATE_CHANGED = "state-changed";

    /**
     * 动作：移除（bundle 位置记录被清除）。
     */
    public static final String ACTION_REMOVED = "removed";

    /**
     * 动作：解析失败（依赖无法满足等原因）。
     */
    public static final String ACTION_RESOLVE_FAILED = "resolve-failed";

    /**
     * 紧凑构造器：归一空值，时间戳缺省取当前时间。
     *
     * @param action       生命周期动作
     * @param symbolicName bundle 符号名
     * @param oldState     变化前状态
     * @param newState     变化后状态
     * @param version      bundle 版本
     * @param message      附加说明
     * @param timestamp    事件时间戳
     */
    public OsgiStateEvent {
        action = action == null ? "" : action;
        symbolicName = symbolicName == null ? "" : symbolicName;
        oldState = oldState == null ? "" : oldState;
        newState = newState == null ? "" : newState;
        version = version == null ? "" : version;
        message = message == null ? "" : message;
        timestamp = timestamp <= 0 ? System.currentTimeMillis() : timestamp;
    }

    /**
     * 构造「安装」事件。
     *
     * @param symbolicName bundle 符号名
     * @return 安装事件
     */
    public static OsgiStateEvent installed(String symbolicName) {
        return new OsgiStateEvent(ACTION_INSTALLED, symbolicName, "", "", "", "", System.currentTimeMillis());
    }

    /**
     * 构造「启动」事件。
     *
     * @param symbolicName bundle 符号名
     * @return 启动事件
     */
    public static OsgiStateEvent started(String symbolicName) {
        return new OsgiStateEvent(ACTION_STARTED, symbolicName, "", "", "", "", System.currentTimeMillis());
    }

    /**
     * 构造「停止」事件。
     *
     * @param symbolicName bundle 符号名
     * @return 停止事件
     */
    public static OsgiStateEvent stopped(String symbolicName) {
        return new OsgiStateEvent(ACTION_STOPPED, symbolicName, "", "", "", "", System.currentTimeMillis());
    }

    /**
     * 构造「更新」事件。
     *
     * @param symbolicName bundle 符号名
     * @param newVersion   更新后的版本号
     * @return 更新事件
     */
    public static OsgiStateEvent updated(String symbolicName, String newVersion) {
        return new OsgiStateEvent(ACTION_UPDATED, symbolicName, "", "", newVersion, "", System.currentTimeMillis());
    }

    /**
     * 构造「卸载」事件。
     *
     * @param symbolicName bundle 符号名
     * @return 卸载事件
     */
    public static OsgiStateEvent uninstalled(String symbolicName) {
        return new OsgiStateEvent(ACTION_UNINSTALLED, symbolicName, "", "", "", "", System.currentTimeMillis());
    }

    /**
     * 构造「状态变化」事件。
     *
     * @param symbolicName bundle 符号名
     * @param oldState     变化前状态
     * @param newState     变化后状态
     * @return 状态变化事件
     */
    public static OsgiStateEvent stateChanged(String symbolicName, String oldState, String newState) {
        return new OsgiStateEvent(ACTION_STATE_CHANGED, symbolicName, oldState, newState, "",
                "", System.currentTimeMillis());
    }

    /**
     * 构造「移除」事件（bundle 位置记录被清除）。
     *
     * @param symbolicName bundle 符号名
     * @param version      卸载前的版本号，可为空
     * @return 移除事件
     */
    public static OsgiStateEvent removed(String symbolicName, String version) {
        return new OsgiStateEvent(ACTION_REMOVED, symbolicName, "", "", version, "", System.currentTimeMillis());
    }

    /**
     * 构造「解析失败」事件。
     *
     * @param symbolicName bundle 符号名
     * @param reason       框架给出的失败原因，可为空
     * @return 解析失败事件
     */
    public static OsgiStateEvent resolveFailed(String symbolicName, String reason) {
        return new OsgiStateEvent(ACTION_RESOLVE_FAILED, symbolicName, "", "", "", reason,
                System.currentTimeMillis());
    }
}
