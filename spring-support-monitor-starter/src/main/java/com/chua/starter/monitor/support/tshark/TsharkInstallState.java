package com.chua.starter.monitor.support.tshark;

/**
 * tshark 运行时的准备状态。
 *
 * <p>状态是单向推进的，前端只需要判断「能不能用」和「现在在干什么」：</p>
 * <pre>
 * CHECKING ──found──▶ READY
 *    │
 *    └─missing─▶ MISSING ──有安装包──▶ DOWNLOADING ─▶ EXTRACTING ─▶ READY
 *                                                              └──▶ FAILED
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public enum TsharkInstallState {

    /**
     * 正在探测本机是否已有可用的 tshark。
     */
    CHECKING,

    /**
     * 确认没有可用的 tshark，且没有可用的安装包（或自动安装被关闭）。
     */
    MISSING,

    /**
     * 正在下载便携包。
     */
    DOWNLOADING,

    /**
     * 正在解压并自检。
     */
    EXTRACTING,

    /**
     * 可用。
     */
    READY,

    /**
     * 安装失败（网络、包损坏、依赖不全等），消息里带可读原因。
     */
    FAILED
}
