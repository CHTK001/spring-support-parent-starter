// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.provider.sdk.hikvision;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * 海康云眸官方接口目录（逐条取自 pic.hik-cloud.com《外部独立应用开发》）。
 *
 * <p>网关固定 {@code https://api2.hik-cloud.com}；换令牌 {@code POST /oauth/token}
 * （表单 {@code client_id/client_secret/grant_type=client_credentials}），
 * 业务请求统一 {@code Authorization: Bearer <access_token>}。</p>
 *
 * <p>本目录中的路径全部来自官方文档「请求地址」原文，未取到字段文档的接口不在此登记，
 * 由会话层显式抛 {@link UnsupportedOperationException}，避免出现推测路径。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class HikvisionCloudEyeApis {

    /** 云眸基础服务前缀 */
    private static final String P = "/api/v1/open";

    /** 云眸历史（社区）接口前缀 */
    private static final String C = "/api/v1/community";

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
            PlatformApiSpec.global("device.add", "添加设备", "POST", P + "/basic/devices/create", "v1", "device"),
            PlatformApiSpec.global("device.update", "修改设备（改名）", "POST", P + "/basic/devices/update", "v1", "device"),
            PlatformApiSpec.global("device.delete", "删除设备", "POST", P + "/basic/devices/delete", "v1", "device"),
            PlatformApiSpec.global("device.count", "设备数量统计", "GET", P + "/basic/devices/actions/deviceCount", "v1", "device"),
            PlatformApiSpec.global("device.status", "设备能力/在网状态", "GET", "/api/v1/ezviz/devices/queryDeviceStatus", "v1", "device"),
            PlatformApiSpec.global("device.reboot", "远程重启设备", "POST", P + "/basic/devices/actions/system/reboot", "v1", "device"),
            // ---------- 基础服务：通道 ----------
            PlatformApiSpec.global("channel.list", "通道分页查询", "GET", P + "/basic/channels/list", "v1", "monitor"),
            PlatformApiSpec.global("channel.sync", "同步设备通道", "POST", P + "/basic/channels/actions/sync", "v1", "monitor"),
            PlatformApiSpec.global("channel.update", "修改通道（改名）", "POST", P + "/basic/channels/update", "v1", "monitor"),
            PlatformApiSpec.global("channel.name.sync", "通道名称下发设备", "POST", P + "/basic/channels/actions/name/sync", "v1", "monitor"),
            PlatformApiSpec.global("channel.capture", "通道抓图", "POST", P + "/basic/channels/actions/capture", "v1", "monitor"),
            // ---------- 基础服务：分组（组织） ----------
            PlatformApiSpec.global("group.create", "新增分组", "POST", P + "/basic/groups/create", "v1", "org"),
            PlatformApiSpec.global("group.update", "修改分组", "POST", P + "/basic/groups/update", "v1", "org"),
            PlatformApiSpec.global("group.delete", "删除分组", "POST", P + "/basic/groups/delete", "v1", "org"),
            PlatformApiSpec.global("group.get", "查询分组详情", "GET", P + "/basic/groups/get", "v1", "org"),
            PlatformApiSpec.global("group.listAll", "查询全部权限内分组", "GET", P + "/basic/groups/actions/listAll", "v1", "org"),
            PlatformApiSpec.global("group.children", "查询下级分组", "GET", P + "/basic/groups/actions/childrenList", "v1", "org"),
            // ---------- 基础服务：人员 ----------
            PlatformApiSpec.global("person.create", "添加人员", "POST", P + "/basic/persons/create", "v1", "person"),
            PlatformApiSpec.global("person.update", "修改人员", "POST", P + "/basic/persons/update", "v1", "person"),
            PlatformApiSpec.global("person.delete", "删除人员", "POST", P + "/basic/persons/delete", "v1", "person"),
            PlatformApiSpec.global("person.get", "查询人员详情", "GET", P + "/basic/persons/get", "v1", "person"),
            PlatformApiSpec.global("person.face.update", "更新人员人脸", "POST", P + "/basic/faces/update", "v1", "person"),
            PlatformApiSpec.global("person.face.delete", "删除人员人脸", "POST", P + "/basic/faces/delete", "v1", "person"),
            PlatformApiSpec.global("person.list", "人员批量分页查询", "POST", C + "/access/persons/actions/getPersons", "v1", "person"),
            // ---------- 视频服务 ----------
            PlatformApiSpec.global("video.live", "获取标准流播放地址", "GET", "/v1/carrier/wing/endpoint/video/getVideoUrl", "v1", "video"),
            // [P3C DF-代码格式 豁免]
            PlatformApiSpec.global("video.live.disable", "失效标准流播放地址", "POST", "/siot/video/api/standardStreams/actions/disableLiveAddress", "v1", "video"),
            PlatformApiSpec.global("video.records", "查询设备录像片段", "GET", "/v1/carrier/communicate/record/daily", "v1", "video"),
            // ---------- 视频服务：云台 / 预置位 ----------
            PlatformApiSpec.global("ptz.start", "云台开始转动", "POST", P + "/basic/channels/actions/ptz/start", "v1", "video"),
            PlatformApiSpec.global("ptz.stop", "云台停止转动", "POST", P + "/basic/channels/actions/ptz/stop", "v1", "video"),
            PlatformApiSpec.global("ptz.position3d", "三维定位", "POST", P + "/basic/channels/actions/ptz/position3d", "v1", "video"),
            PlatformApiSpec.global("ptz.wiper", "手动雨刷", "POST", P + "/basic/channels/actions/manualWiper", "v1", "video"),
            PlatformApiSpec.global("preset.create", "新增预置点", "POST", P + "/basic/presets/create", "v1", "video"),
            PlatformApiSpec.global("preset.update", "修改预置点", "POST", P + "/basic/presets/update", "v1", "video"),
            PlatformApiSpec.global("preset.delete", "删除预置点", "POST", P + "/basic/presets/delete", "v1", "video"),
            PlatformApiSpec.global("preset.move", "调用预置点", "POST", P + "/basic/presets/actions/move", "v1", "video"),
            PlatformApiSpec.global("preset.list", "查询预置点列表", "GET", P + "/basic/presets/list", "v1", "video"),
            // ---------- 门禁服务 ----------
            PlatformApiSpec.global("access.record.list", "门禁事件分页查询", "GET", P + "/accessControl/accessEvents/actions/list", "v1", "access"),
            PlatformApiSpec.global("access.door.open", "远程控门", "POST", P + "/accessControl/remoteControl/actions/open", "v1", "access"),
            // ---------- 事件：ISAPI 订阅 + 消息通道 ----------
            PlatformApiSpec.global("event.subscribe", "ISAPI 事件订阅", "POST", "/v1/carrier/charon/device/isapi/open/subscribe", "v1", "alarm"),
            PlatformApiSpec.global("event.unsubscribe", "ISAPI 事件取消订阅", "POST", "/api/v1/device/isapi/isapiEvent/actions/unsubscribe", "v1", "alarm"),
            PlatformApiSpec.global("mq.consumer.create", "创建消息消费者", "POST", "/api/v1/mq/consumer/group1", "v1", "alarm"),
            PlatformApiSpec.global("mq.consumer.messages", "拉取消息", "POST", "/api/v1/mq/consumer/messages", "v1", "alarm"),
            PlatformApiSpec.global("mq.consumer.offsets", "提交消费偏移", "POST", "/api/v1/mq/consumer/offsets", "v1", "alarm"),
            // ---------- 历史（社区）接口：设备/通道 ----------
            PlatformApiSpec.global("legacy.device.list", "历史-设备列表", "GET", "/v1/devices/list", "v1", "device"),
            PlatformApiSpec.global("legacy.device.add", "历史-添加设备", "POST", "/v1/devices/add", "v1", "device"),
            PlatformApiSpec.global("legacy.device.info", "历史-设备详情", "GET", "/v1/devices/{deviceId}/info", "v1", "device"),
            PlatformApiSpec.global("legacy.device.update", "历史-修改设备", "POST", "/v1/devices/{deviceId}/update", "v1", "device"),
            PlatformApiSpec.global("legacy.device.delete", "历史-删除设备", "POST", "/v1/devices/{deviceId}/delete", "v1", "device"),
            PlatformApiSpec.global("legacy.channel.list", "历史-通道列表", "GET", "/v1/channels/list", "v1", "monitor"),
            PlatformApiSpec.global("legacy.channel.sync", "历史-同步通道", "POST", "/v1/devices/{deviceId}/synchChannels", "v1", "monitor"),
            PlatformApiSpec.global("legacy.channel.update", "历史-修改通道", "POST", "/v1/channels/{channelId}/update", "v1", "monitor"),
            PlatformApiSpec.global("legacy.channel.capture", "历史-通道抓图", "POST", "/v1/channels/{channelId}/capture", "v1", "monitor")
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