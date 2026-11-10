package com.chua.starter.device.provider.sdk.hikvision;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * 海康 ISUP / EHome 接入网关接口目录。
 *
 * <p><b>协议性质</b>：ISUP（原 EHome）是海康的<b>设备主动注册协议</b>，
 * 不是 HTTP OpenAPI：设备通过 SDK/协议向 ISUP 服务端注册（默认 7660 端口），
 * 平台侧通过 ISUP SDK（C/C++ 动态库）或第三方 ISUP 服务接收注册、透传预览/回放/云台/告警。</p>
 *
 * <p>因此本目录登记的是「能力点」而非 HTTP 端点：{@code path} 记录协议动作名，
 * 调用方通过 {@link HikvisionIsupSdk#invoke} 时由具体实现决定（SDK/JNA 或
 * 转发的 ISUP 服务端 REST 封装）。<b>没有 HTTP 网关，baseUrl 无意义。</b></p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class HikvisionIsupApis {

    /** 协议动作前缀（非 HTTP 路径） */
    private static final String P = "isup://";

    /**
     * 工具类不可实例化。
     */
    private HikvisionIsupApis() {
    }

    /** 全部能力点 */
    private static final List<PlatformApiSpec> ALL = List.of(
            PlatformApiSpec.global("isup.register", "设备注册（主动上线）", "EVENT", P + "register", "1.0", "device"),
            PlatformApiSpec.global("isup.logout", "设备注销", "EVENT", P + "logout", "1.0", "device"),
            PlatformApiSpec.global("isup.alarm", "告警上报", "EVENT", P + "alarm", "1.0", "alarm"),
            PlatformApiSpec.global("isup.heartbeat", "心跳保活", "EVENT", P + "heartbeat", "1.0", "device"),
            PlatformApiSpec.global("isup.device.list", "已注册设备列表", "QUERY", P + "device/list", "1.0", "device"),
            PlatformApiSpec.global("isup.device.info", "设备详情", "QUERY", P + "device/info", "1.0", "device"),
            PlatformApiSpec.global("isup.device.channels", "设备通道列表", "QUERY", P + "device/channels", "1.0", "device"),
            PlatformApiSpec.global("isup.live", "实时预览（透传码流）", "STREAM", P + "live", "1.0", "video"),
            PlatformApiSpec.global("isup.playback", "录像回放", "STREAM", P + "playback", "1.0", "video"),
            PlatformApiSpec.global("isup.ptz", "云台控制", "COMMAND", P + "ptz", "1.0", "video"),
            PlatformApiSpec.global("isup.capture", "设备抓图", "COMMAND", P + "capture", "1.0", "video"),
            PlatformApiSpec.global("isup.record.query", "录像查询", "QUERY", P + "record/query", "1.0", "video")
    );

    /**
     * 全部能力点。
     *
     * @return 能力声明
     */
    public static List<PlatformApiSpec> all() {
        return ALL;
    }
}
