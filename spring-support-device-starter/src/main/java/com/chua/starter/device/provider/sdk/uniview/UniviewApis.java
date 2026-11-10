package com.chua.starter.device.provider.sdk.uniview;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * 宇视（Uniview）云开放平台接口目录。
 *
 * <p>网关：{@code https://open.uniview.com}（已实测可达；{@code /openapi} 类路径返回
 * SPA 页面，说明接口走 XHR 而非公开文档路径）。</p>
 *
 * <p><b>路径核实说明</b>：宇视开放平台文档需登录控制台查看接口清单，下列 path 按
 * 「{@code /api/v1/...}」命名约定登记，<b>尚未逐条实测</b>。
 * 凭据可用 {@code extra("apiPath.&lt;code&gt;")} 覆盖；请以控制台「API 列表」为准。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class UniviewApis {

    /** 宇视 openapi 前缀（待控制台核对） */
    private static final String P = "/api/v1";

    /**
     * 工具类不可实例化。
     */
    private UniviewApis() {
    }

    /** 全部接口 */
    private static final List<PlatformApiSpec> ALL = List.of(
            // ---------- 认证 ----------
            PlatformApiSpec.global("auth.token", "获取 accessToken", "POST", P + "/openapi/getAccessToken", "v1", "other"),

            // ---------- 设备 ----------
            PlatformApiSpec.global("device.list", "查询设备列表", "POST", P + "/device/list", "v1", "device"),
            PlatformApiSpec.global("device.info", "获取设备详情", "POST", P + "/device/info", "v1", "device"),
            PlatformApiSpec.global("device.status", "查询设备在线状态", "POST", P + "/device/status", "v1", "device"),
            PlatformApiSpec.global("device.channels", "查询设备通道", "POST", P + "/device/channels", "v1", "device"),

            // ---------- 组织 ----------
            PlatformApiSpec.global("org.list", "查询组织列表", "POST", P + "/org/list", "v1", "org"),

            // ---------- 视频 ----------
            PlatformApiSpec.global("video.live", "获取实时预览地址", "POST", P + "/video/live", "v1", "video"),
            PlatformApiSpec.global("video.playback", "获取回放地址", "POST", P + "/video/playback", "v1", "video"),
            PlatformApiSpec.global("video.records", "查询录像列表", "POST", P + "/video/records", "v1", "video"),
            PlatformApiSpec.global("video.capture", "设备抓图", "POST", P + "/video/capture", "v1", "video"),

            // ---------- 云台 ----------
            PlatformApiSpec.global("ptz.control", "云台控制", "POST", P + "/ptz/control", "v1", "video"),

            // ---------- 告警 ----------
            PlatformApiSpec.global("alarm.list", "查询告警消息", "POST", P + "/alarm/list", "v1", "alarm"),
            PlatformApiSpec.global("alarm.subscribe", "订阅告警", "POST", P + "/alarm/subscribe", "v1", "alarm")
    );

    /**
     * 全部接口目录。
     *
     * @return 接口声明
     */
    public static List<PlatformApiSpec> all() {
        return ALL;
    }
}
