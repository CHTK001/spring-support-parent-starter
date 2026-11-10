package com.chua.starter.device.support.callback;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备域生命周期事件（回调通知负载）。
 *
 * <p>回调框架不替业务做事务决策：SUCCESS 表示本地动作已完成，FAILED 表示本地动作
 * 抛错（下游据此自行回滚它在别的服务器上的操作，例如人脸库下发）。</p>
 *
 * @param eventType  事件类型：DEVICE_CREATED / DEVICE_UPDATED / DEVICE_DELETED / PERSON_ENROLLED
 * @param phase      阶段：SUCCESS / FAILED
 * @param deviceId   设备标识，添加失败时可能为占位值
 * @param vendor     厂商标识
 * @param payload    业务负载（设备快照、人员标识等），只读
 * @param error      失败原因，成功时为 null
 * @param occurredAt 事件发生时间
 * @since 4.0.0.51
 * @author CH
 */
public record DeviceLifecycleEvent(
        String eventType,
        String phase,
        String deviceId,
        String vendor,
        Map<String, Object> payload,
        String error,
        Instant occurredAt
) {

    /** 事件类型：设备添加 */
    public static final String TYPE_DEVICE_CREATED = "DEVICE_CREATED";
    /** 事件类型：设备更新 */
    public static final String TYPE_DEVICE_UPDATED = "DEVICE_UPDATED";
    /** 事件类型：设备删除 */
    public static final String TYPE_DEVICE_DELETED = "DEVICE_DELETED";
    /** 事件类型：人员/人脸录入 */
    public static final String TYPE_PERSON_ENROLLED = "PERSON_ENROLLED";
    /** 事件类型：告警入站（webhook 收到并落库后广播） */
    public static final String TYPE_ALARM_RECEIVED = "ALARM_RECEIVED";
    /** 阶段：本地动作成功 */
    public static final String PHASE_SUCCESS = "SUCCESS";
    /** 阶段：本地动作失败 */
    public static final String PHASE_FAILED = "FAILED";

    /**
     * 规范构造器：负载做防御性不可变拷贝。
     */
    public DeviceLifecycleEvent {
        payload = payload == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /**
     * 构造一个成功事件。
     *
     * @param eventType 事件类型
     * @param deviceId  设备标识
     * @param vendor    厂商标识
     * @param payload   业务负载
     * @return 成功事件
     */
    public static DeviceLifecycleEvent success(String eventType, String deviceId, String vendor,
                                               Map<String, Object> payload) {
        return new DeviceLifecycleEvent(eventType, PHASE_SUCCESS, deviceId, vendor, payload, null, Instant.now());
    }

    /**
     * 构造一个失败事件。
     *
     * @param eventType 事件类型
     * @param deviceId  设备标识
     * @param vendor    厂商标识
     * @param error     失败原因
     * @return 失败事件
     */
    public static DeviceLifecycleEvent failed(String eventType, String deviceId, String vendor, String error) {
        return new DeviceLifecycleEvent(eventType, PHASE_FAILED, deviceId, vendor, Map.of(), error, Instant.now());
    }
}
