package com.chua.starter.device.provider.sdk.dahua;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * 大华乐橙（Imou / LeChange）官方接口目录。
 *
 * <p>乐橙统一网关：{@code POST https://openapi.lechange.cn/openapi/{method}}，
 * 请求体固定 {@code {system, id, params}} 三层，签名为
 * {@code md5("time:{t},nonce:{n},appSecret:{s}")}。</p>
 *
 * <p>目录里 {@code path} 记录的是 {@code /openapi/<method>}，业务调用时取最后一段作为方法名；
 * 凭据可用 {@code extra("apiMethod.<code>")} 覆盖，官方新增/改名方法无需改代码。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class LeChangeApis {

    /**
     * 工具类不可实例化。
     */
    private LeChangeApis() {
    }

    /** 全部接口 */
    private static final List<PlatformApiSpec> ALL = List.of(
            // 认证
            PlatformApiSpec.global("auth.token", "获取管理员 accessToken", "POST",
                    "/openapi/accessToken", "v1", "other"),

            // 设备管理
            PlatformApiSpec.global("device.page", "分页查询设备详情", "POST",
                    "/openapi/listDeviceDetailsByPage", "v1", "device"),
            PlatformApiSpec.global("device.info", "获取单个设备信息", "POST",
                    "/openapi/deviceInfo", "v1", "device"),
            PlatformApiSpec.global("device.online", "查询设备在线状态", "POST",
                    "/openapi/deviceOnline", "v1", "device"),
            PlatformApiSpec.global("device.bind", "绑定设备", "POST",
                    "/openapi/bindDevice", "v1", "device"),
            PlatformApiSpec.global("device.unbind", "解绑设备", "POST",
                    "/openapi/unbindDevice", "v1", "device"),
            PlatformApiSpec.global("device.channels", "查询设备通道列表", "POST",
                    "/openapi/listDeviceChannelByPage", "v1", "device"),
            PlatformApiSpec.global("device.ability", "查询设备能力集", "POST",
                    "/openapi/deviceAbility", "v1", "device"),
            PlatformApiSpec.global("device.updateName", "修改设备名称", "POST",
                    "/openapi/modifyDeviceName", "v1", "device"),

            // 分组
            PlatformApiSpec.global("group.page", "分页查询设备分组", "POST",
                    "/openapi/deviceGroupList", "v1", "org"),
            PlatformApiSpec.global("group.add", "添加设备分组", "POST",
                    "/openapi/addDeviceGroup", "v1", "org"),
            PlatformApiSpec.global("group.update", "修改设备分组", "POST",
                    "/openapi/modifyDeviceGroup", "v1", "org"),
            PlatformApiSpec.global("group.delete", "删除设备分组", "POST",
                    "/openapi/deleteDeviceGroup", "v1", "org"),

            // 视频
            PlatformApiSpec.global("video.live", "获取直播/取流地址", "POST",
                    "/openapi/liveList", "v1", "video"),
            PlatformApiSpec.global("video.playback", "获取回放地址", "POST",
                    "/openapi/playbackByDevice", "v1", "video"),
            PlatformApiSpec.global("video.records", "查询设备本地录像", "POST",
                    "/openapi/queryLocalRecords", "v1", "video"),
            PlatformApiSpec.global("video.cloud.records", "查询云录像", "POST",
                    "/openapi/queryCloudRecords", "v1", "video"),
            PlatformApiSpec.global("video.capture", "设备抓图", "POST",
                    "/openapi/setDeviceSnap", "v1", "video"),
            PlatformApiSpec.global("video.ptz", "云台控制", "POST",
                    "/openapi/controlMovePTZ", "v1", "video"),
            PlatformApiSpec.global("video.ptz.preset", "预置点操作", "POST",
                    "/openapi/controlLocationPTZ", "v1", "video"),

            // 门禁与人员
            PlatformApiSpec.global("access.record", "门禁通行记录", "POST",
                    "/openapi/accessRecordList", "v1", "access"),
            PlatformApiSpec.global("access.door.open", "远程开门", "POST",
                    "/openapi/openDoor", "v1", "access"),
            PlatformApiSpec.global("person.page", "人员分页查询", "POST",
                    "/openapi/personList", "v1", "person"),

            // 告警
            PlatformApiSpec.global("alarm.page", "告警消息分页查询", "POST",
                    "/openapi/alarmMessageList", "v1", "alarm"),
            PlatformApiSpec.global("alarm.subscribe", "订阅设备告警消息", "POST",
                    "/openapi/subscribeAlarmMessage", "v1", "alarm")
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
