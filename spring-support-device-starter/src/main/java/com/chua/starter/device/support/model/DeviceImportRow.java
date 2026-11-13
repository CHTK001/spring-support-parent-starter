package com.chua.starter.device.support.model;

import lombok.Data;

/**
 * 台账导入的单行结果。
 *
 * <p>{@code action} 只有四种：{@code create} 新增、{@code update} 更新、
 * {@code skip} 跳过、{@code error} 校验失败。前端按它渲染逐行状态标签。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class DeviceImportRow {

    /**
     * Excel 行号（从 2 开始，1 是表头）
     */
    private int row;

    /**
     * 动作：create / update / skip / error
     */
    private String action;

    /**
     * 设备 ID（新增时可能为空）
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 说明：为什么跳过或失败
     */
    private String message;

    /**
     * 构造一条结果。
     *
     * @param row        行号
     * @param action     动作
     * @param deviceId   设备 ID
     * @param deviceName 设备名称
     * @param message    说明
     * @return 结果行
     */
    public static DeviceImportRow of(int row, String action, String deviceId, String deviceName, String message) {
        DeviceImportRow result = new DeviceImportRow();
        result.setRow(row);
        result.setAction(action);
        result.setDeviceId(deviceId);
        result.setDeviceName(deviceName);
        result.setMessage(message);
        return result;
    }
}
