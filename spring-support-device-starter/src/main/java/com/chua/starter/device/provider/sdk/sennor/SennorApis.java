package com.chua.starter.device.provider.sdk.sennor;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * 精讯畅通（Sennor）精讯云 HTTP 取数接口目录。
 *
 * <p>路径取自官方在线文档“协议 → 获取协议 → HTTP 数据获取”
 * （{@code http://docs.sennor.net/consolePanel/insideFile?val=agreement}）。
 * 精讯云把传感器设备与继电器设备分成两套路径：传感器在 {@code /device/*}，
 * 继电器在 {@code /relay/*}；两套接口都以 16 位 {@code secretKey} 鉴权。</p>
 *
 * <p>文档没有给出独立网关域名，接口路径相对于精讯云平台地址。控制台账号登录
 * 使用 {@code /index/login}，该接口不在本目录内，由会话层单独处理。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
public final class SennorApis {

    /** 传感器设备列表 */
    public static final String P_DEVICE_LIST = "/device/getDeviceList";

    /** 传感器设备详情 */
    public static final String P_DEVICE_INFO = "/device/getDeviceInfo";

    /** 修改传感器设备名称 */
    public static final String P_DEVICE_RENAME = "/device/upDeviceInfo";

    /** 最近历史数据 */
    public static final String P_DEVICE_HISTORY_LATEST = "/device/selLatelyHistory";

    /** 按日期查询历史数据 */
    public static final String P_DEVICE_HISTORY_BY_DATE = "/device/selHistoryForDate";

    /** 设备下全部节点实时数据 */
    public static final String P_DEVICE_SENSOR_DATA = "/device/selSensorData";

    /** 单个节点实时数据 */
    public static final String P_DEVICE_SINGLE_SENSOR_DATA = "/device/selSingleSensorData";

    /** 传感器设备继电器控制（兼容旧版设备接口） */
    public static final String P_DEVICE_RELAY_COMMAND = "/device/sendRelayCommand";

    /** 继电器设备列表 */
    public static final String P_RELAY_LIST = "/relay/getDeviceList";

    /** 继电器控制 */
    public static final String P_RELAY_COMMAND = "/relay/sendRelayCommand";

    /** 继电器设备改名 */
    public static final String P_RELAY_RENAME = "/relay/updateDeviceName";

    /** 继电器定时任务列表 */
    public static final String P_RELAY_TASK_LIST = "/relay/taskList";

    /** 单个继电器设备下发定时任务 */
    public static final String P_RELAY_SEND_TASK = "/relay/sendTask";

    /** 多个继电器设备批量下发定时任务 */
    public static final String P_RELAY_BATCH_SEND_TASK = "/relay/batchSendTask";

    /**
     * 全部已核实的官方接口。
     *
     * <p>只登记文档中明确给出路径的接口；文档之外的“猜测路径”不进入目录。</p>
     */
    private static final List<PlatformApiSpec> ALL = List.of(
            PlatformApiSpec.global("device.list", "获取传感器设备列表", "GET",
                    P_DEVICE_LIST, "v1", "device"),
            PlatformApiSpec.global("device.info", "获取传感器设备信息", "GET",
                    P_DEVICE_INFO, "v1", "device"),
            PlatformApiSpec.global("device.rename", "修改传感器设备名称", "POST",
                    P_DEVICE_RENAME, "v1", "device"),
            PlatformApiSpec.global("device.history.latest", "查询最近历史数据", "GET",
                    P_DEVICE_HISTORY_LATEST, "v1", "device"),
            PlatformApiSpec.global("device.history.date", "按日期查询历史数据", "GET",
                    P_DEVICE_HISTORY_BY_DATE, "v1", "device"),
            PlatformApiSpec.global("device.sensorData", "查询设备节点实时数据", "GET",
                    P_DEVICE_SENSOR_DATA, "v1", "device"),
            PlatformApiSpec.global("device.sensorData.single", "查询单个节点实时数据", "GET",
                    P_DEVICE_SINGLE_SENSOR_DATA, "v1", "device"),
            PlatformApiSpec.global("device.relayCommand", "传感器设备继电器控制", "GET",
                    P_DEVICE_RELAY_COMMAND, "v1", "device"),

            PlatformApiSpec.global("relay.list", "获取继电器设备列表", "GET",
                    P_RELAY_LIST, "v1", "device"),
            PlatformApiSpec.global("relay.command", "继电器控制", "GET",
                    P_RELAY_COMMAND, "v1", "device"),
            PlatformApiSpec.global("relay.rename", "修改继电器设备名称", "POST",
                    P_RELAY_RENAME, "v1", "device"),
            PlatformApiSpec.global("relay.taskList", "查询继电器定时任务", "GET",
                    P_RELAY_TASK_LIST, "v1", "device"),
            PlatformApiSpec.global("relay.sendTask", "下发继电器定时任务", "POST",
                    P_RELAY_SEND_TASK, "v1", "device"),
            PlatformApiSpec.global("relay.batchSendTask", "批量下发继电器定时任务", "POST",
                    P_RELAY_BATCH_SEND_TASK, "v1", "device")
    );

    /**
     * 工具类不可实例化。
     */
    private SennorApis() {
    }

    /**
     * 全部接口目录。
     *
     * @return 接口声明
     */
    public static List<PlatformApiSpec> all() {
        return ALL;
    }
}
