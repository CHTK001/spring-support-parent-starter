package com.chua.starter.device.provider.sdk.hikvision;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * 海康综合安防平台（iSecure Center / artemis 网关）官方接口目录。
 *
 * <p>路径按官方 OpenAPI 文档的 {@code /artemis/api/...} 形态登记；签名由
 * {@code artemis-http-client} SDK 完成，所以全部标 {@link PlatformApiSpec.Auth#GLOBAL}。</p>
 *
 * <p>版本：综合安防的版本体现在路径里（{@code resource/v1}、{@code video/v2}、
 * {@code resource/v2}），同一平台不同资源版本不同，故逐条写清。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class HikvisionArtemisApis {

    /**
     * 工具类不可实例化。
     */
    private HikvisionArtemisApis() {
    }

    /** 设备与资源 */
    private static final List<PlatformApiSpec> DEVICE = List.of(
            PlatformApiSpec.global("device.search", "设备分页查询", "POST", "/artemis/api/resource/v2/device/search", "v2", "device"),
            PlatformApiSpec.global("device.info", "获取设备详情", "POST", "/artemis/api/resource/v1/device/deviceInfo", "v1", "device"),
            PlatformApiSpec.global("device.add", "添加设备", "POST", "/artemis/api/resource/v1/device/add", "v1", "device"),
            PlatformApiSpec.global("device.update", "修改设备", "POST", "/artemis/api/resource/v1/device/update", "v1", "device"),
            PlatformApiSpec.global("device.delete", "删除设备", "POST", "/artemis/api/resource/v1/device/delete", "v1", "device"),
            PlatformApiSpec.global("device.online", "设备在线状态", "POST", "/artemis/api/nms/v1/online/state/get", "v1", "device"),
            PlatformApiSpec.global("org.list", "组织列表", "POST", "/artemis/api/resource/v1/org/orgList", "v1", "org"),
            PlatformApiSpec.global("org.add", "添加组织", "POST", "/artemis/api/resource/v1/org/add", "v1", "org"),
            PlatformApiSpec.global("org.update", "修改组织", "POST", "/artemis/api/resource/v1/org/update", "v1", "org"),
            PlatformApiSpec.global("org.delete", "删除组织", "POST", "/artemis/api/resource/v1/org/delete", "v1", "org"),
            PlatformApiSpec.global("region.list", "区域列表", "POST", "/artemis/api/resource/v1/regions", "v1", "org"),
            PlatformApiSpec.global("camera.search", "监控点分页查询", "POST", "/artemis/api/resource/v2/camera/search", "v2", "monitor"),
            PlatformApiSpec.global("camera.info", "监控点详情", "POST", "/artemis/api/resource/v1/camera/getCameras", "v1", "monitor"),
            PlatformApiSpec.global("camera.preview", "获取预览取流 URL", "POST", "/artemis/api/video/v2/cameras/previewURLs", "v2", "video"),
            PlatformApiSpec.global("camera.playback", "获取回放取流 URL", "POST", "/artemis/api/video/v2/cameras/playbackURLs", "v2", "video"),
            PlatformApiSpec.global("camera.records", "查询录像片段", "POST", "/artemis/api/video/v2/cameras/records", "v2", "video"),
            PlatformApiSpec.global("camera.capture", "监控点抓图", "POST", "/artemis/api/video/v1/cameras/images", "v1", "video"),
            PlatformApiSpec.global("ptz.control", "云台控制", "POST", "/artemis/api/video/v1/ptz/controlling", "v1", "video"),
            PlatformApiSpec.global("ptz.preset.list", "预置点列表", "POST", "/artemis/api/video/v1/ptz/presets", "v1", "video"),
            PlatformApiSpec.global("ptz.preset.set", "设置预置点", "POST", "/artemis/api/video/v1/ptz/presets/set", "v1", "video"),
            PlatformApiSpec.global("ptz.preset.delete", "删除预置点", "POST", "/artemis/api/video/v1/ptz/presets/delete", "v1", "video"),
            PlatformApiSpec.global("door.list", "门禁点列表", "POST", "/artemis/api/resource/v1/door/search", "v1", "access"),
            PlatformApiSpec.global("door.control", "门禁远程控制", "POST", "/artemis/api/access/v1/door/control", "v1", "access"),
            PlatformApiSpec.global("door.status", "门禁点状态", "POST", "/artemis/api/access/v1/door/status", "v1", "access"),
            PlatformApiSpec.global("access.record", "门禁出入记录", "POST", "/artemis/api/access/v1/accessRecords", "v1", "access"),
            PlatformApiSpec.global("person.list", "人员列表", "POST", "/artemis/api/resource/v2/person/personList", "v2", "person"),
            PlatformApiSpec.global("person.add", "添加人员", "POST", "/artemis/api/resource/v1/person/single/add", "v1", "person"),
            PlatformApiSpec.global("person.update", "修改人员", "POST", "/artemis/api/resource/v1/person/single/update", "v1", "person"),
            PlatformApiSpec.global("person.delete", "删除人员", "POST", "/artemis/api/resource/v1/person/single/delete", "v1", "person"),
            PlatformApiSpec.global("event.search", "事件查询", "POST", "/artemis/api/eventService/v1/eventRecords/page", "v1", "alarm"),
            PlatformApiSpec.global("alarm.search", "告警记录查询", "POST", "/artemis/api/eventService/v1/eventRecords/page", "v1", "alarm")
    );

    /**
     * 全部接口目录。
     *
     * @return 接口声明
     */
    public static List<PlatformApiSpec> all() {
        return DEVICE;
    }
}
