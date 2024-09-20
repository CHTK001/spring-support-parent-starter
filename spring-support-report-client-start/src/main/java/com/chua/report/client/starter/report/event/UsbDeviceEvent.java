package com.chua.report.client.starter.report.event;

import com.chua.oshi.support.UsbDevice;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示USB设备的类，继承自TimestampEvent，用于记录USB设备的相关信息和事件的时间戳
 * @author CH
 * @since 2024/9/19
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UsbDeviceEvent extends TimestampEvent {

    /**
     * USB设备的名称
     */
    private String name;

    /**
     * USB设备的产品ID
     */
    private String productId;

    /**
     * USB设备的制造商
     */
    private String vendor;

    /**
     * USB设备制造商的ID
     */
    private String vendorId;

    /**
     * USB设备的序列号
     */
    private String serialNumber;

    /**
     * USB设备的唯一设备ID
     */
    private String uniqueDeviceId;
}
