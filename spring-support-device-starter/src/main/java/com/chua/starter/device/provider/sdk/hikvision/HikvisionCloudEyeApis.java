package com.chua.starter.device.provider.sdk.hikvision;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * 海康云眸官方接口目录（来自 pic.hik-cloud.com 云眸 IoT API 文档）。
 *
 * <p>云眸接口统一在 {@code https://api2.hik-cloud.com/api/v1/open/...} 下，
 * 认证用 {@code Authorization: Bearer <access_token>}（client_id/client_secret 换取）。</p>
 *
 * <p>路径与参数以官方文档为准；凭据可用 {@code extra("apiPath.<code>")} 覆盖具体路径。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class HikvisionCloudEyeApis {

    /** 云眸业务前缀 */
    private static final String P = "/api/v1/open";

    /**
     * 工具类不可实例化。
     */
    private HikvisionCloudEyeApis() {
    }

    /** 全部接口 */
    private static final List<PlatformApiSpec> ALL = List.of(
            // ---------- 基础服务：设备 ----------
            PlatformApiSpec.global("device.list", "设备分页查询", "GET", P + "/basic/devices/list", "v1", "device"),
            PlatformApiSpec.global("device.info", "获取设备详情", "GET", P + "/basic/devices/get", "v1", "device"),
            PlatformApiSpec.global("device.add", "添加设备", "POST", P + "/basic/devices/add", "v1", "device"),
            PlatformApiSpec.global("device.update", "修改设备", "POST", P + "/basic/devices/update", "v1", "device"),
            PlatformApiSpec.global("device.delete", "删除设备", "POST", P + "/basic/devices/delete", "v1", "device"),
            PlatformApiSpec.global("device.online", "设备在线状态", "GET", P + "/basic/devices/online", "v1", "device"),
            PlatformApiSpec.global("device.channels", "设备通道列表", "GET", P + "/basic/devices/channels", "v1", "device"),
            // ---------- 基础服务：监控点 ----------
            PlatformApiSpec.global("camera.list", "监控点分页查询", "GET", P + "/basic/cameras/list", "v1", "monitor"),
            PlatformApiSpec.global("camera.info", "监控点详情", "GET", P + "/basic/cameras/get", "v1", "monitor"),
            // ---------- 视频服务 ----------
            PlatformApiSpec.global("video.live", "获取实时预览地址", "POST", P + "/video/live/address", "v1", "video"),
            PlatformApiSpec.global("video.playback", "获取回放地址", "POST", P + "/video/playback/address", "v1", "video"),
            PlatformApiSpec.global("video.records", "查询录像列表", "POST", P + "/video/records", "v1", "video"),
            PlatformApiSpec.global("video.capture", "设备抓图", "POST", P + "/video/capture", "v1", "video"),
            PlatformApiSpec.global("video.ptz", "云台控制", "POST", P + "/video/ptz/control", "v1", "video"),
            // ---------- 门禁服务 ----------
            PlatformApiSpec.global("access.door.list", "门禁点分页", "GET", P + "/access/doors/list", "v1", "access"),
            PlatformApiSpec.global("access.door.open", "远程开门", "POST", P + "/access/doors/open", "v1", "access"),
            PlatformApiSpec.global("access.record.list", "门禁通行记录", "GET", P + "/access/records/list", "v1", "access"),
            PlatformApiSpec.global("access.person.list", "门禁人员分页", "GET", P + "/access/persons/list", "v1", "person"),
            PlatformApiSpec.global("access.person.add", "添加门禁人员", "POST", P + "/access/persons/add", "v1", "person"),
            // ---------- 事件 ----------
            PlatformApiSpec.global("event.list", "事件分页查询", "GET", P + "/events/list", "v1", "alarm")
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
