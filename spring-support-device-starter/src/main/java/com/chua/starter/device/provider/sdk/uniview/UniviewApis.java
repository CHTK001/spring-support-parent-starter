package com.chua.starter.device.provider.sdk.uniview;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * 宇视（Uniview）云开放平台接口目录。
 *
 * <p>路径取自官方文档站 {@code cloud.uniview.com/navCenter/doc/*} 与前端资源包内的
 * {@code openapi/*} 常量，网关为 {@code https://ezcloud.uniview.com}。
 * 认证：{@code POST /openapi/user/app/token/get}（appId + secretKey）换取 accessToken，
 * 之后所有请求带 {@code Authorization: <accessToken>}，有效期 7 天。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class UniviewApis {

    /** 宇视 openapi 前缀 */
    private static final String P = "/openapi";

    /**
     * 工具类不可实例化。
     */
    private UniviewApis() {
    }

    /** 全部接口 */
    private static final List<PlatformApiSpec> ALL = List.of(
            // ---------- 认证 ----------
            PlatformApiSpec.global("auth.token", "获取 AccessToken", "POST", P + "/user/app/token/get", "v1", "other"),

            // ---------- 设备 ----------
            PlatformApiSpec.global("device.list", "查询设备列表", "POST", P + "/device/list", "v1", "device"),
            PlatformApiSpec.global("device.get", "获取单个设备信息", "POST", P + "/device/get", "v1", "device"),
            PlatformApiSpec.global("device.channels", "获取摄像头列表", "POST", P + "/device/channel/list", "v1", "device"),
            PlatformApiSpec.global("device.add", "添加设备", "POST", P + "/device/add", "v1", "device"),
            PlatformApiSpec.global("device.delete", "删除设备", "POST", P + "/device/delete", "v1", "device"),
            PlatformApiSpec.global("device.rename", "修改设备名称", "POST", P + "/device/name/update", "v1", "device"),
            PlatformApiSpec.global("device.capacity", "查询设备能力集", "POST", P + "/device/capacity/get", "v1", "device"),

            // ---------- 视频 ----------
            PlatformApiSpec.global("video.live", "获取固定拉流地址", "POST", P + "/live/video/device/url/get", "v1", "video"),
            PlatformApiSpec.global("video.records", "根据时间获取存储文件", "POST", P + "/device/record/time/query", "v1", "video"),
            PlatformApiSpec.global("video.capture", "设备抓拍图片", "POST", P + "/device/capture/get", "v1", "video"),

            // ---------- 云台 ----------
            PlatformApiSpec.global("ptz.start", "开始云台控制", "POST", P + "/device/ptz/start", "v1", "video"),
            PlatformApiSpec.global("ptz.stop", "停止云台控制", "POST", P + "/device/ptz/stop", "v1", "video"),
            PlatformApiSpec.global("ptz.preset.add", "添加预置位", "POST", P + "/device/ptz/preset/add", "v1", "video"),
            PlatformApiSpec.global("ptz.preset.list", "获取预置位列表", "POST", P + "/device/ptz/preset/list", "v1", "video"),
            PlatformApiSpec.global("ptz.preset.goto", "调用预置位", "POST", P + "/device/ptz/preset/goto", "v1", "video"),
            PlatformApiSpec.global("ptz.preset.delete", "删除预置位", "POST", P + "/device/ptz/preset/delete", "v1", "video"),

            // ---------- 告警 ----------
            PlatformApiSpec.global("alarm.list", "查询告警消息", "POST", P + "/event/alarm/query", "v1", "alarm")
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
