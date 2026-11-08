package com.chua.starter.server.support.terminal;

import com.chua.starter.server.support.model.vo.SerialCommDeviceVO;
import com.fazecast.jSerialComm.SerialPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 本机串口设备列表服务
 * <p>
 * 通过 jSerialComm 的系统设备枚举，列出本机能发现的全部物理/虚拟串口：
 * 系统名（Windows 上如 COM3，Unix 上如 /dev/ttyUSB0）、系统路径、
 * 设备描述、厂商/产品 ID 与占用状态。
 * </p>
 *
 * @author CH
 * @since 4.0.0.48
 */
@Slf4j
@Component
public class SerialCommListingService {

    /**
     * 设备标识取不到时的兜底值
     */
    private static final String UNKNOWN = "unknown";

    /**
     * 列出本机串口设备
     * <p>
     * jSerialComm 的枚举只返回系统中存在的端口；活性的判定由 {@link SerialPort#isOpen()}
     * 携带（被其他进程独占时仍可见但已打开），不可用的屏蔽由库自身处理。
     * 失败时不抛异常，返回空列表。
     * </p>
     *
     * @return 设备视图列表
     */
    public List<SerialCommDeviceVO> listDevices() {
        List<SerialCommDeviceVO> devices = new ArrayList<>();
        try {
            for (SerialPort port : SerialPort.getCommPorts()) {
                if (port == null) {
                    continue;
                }
                SerialCommDeviceVO device = new SerialCommDeviceVO();
                device.setAvailable(true);
                device.setName(port.getSystemPortName() != null ? port.getSystemPortName() : UNKNOWN);
                device.setPath(port.getSystemPortPath() != null ? port.getSystemPortPath() : UNKNOWN);
                device.setDescription(port.getDescriptivePortName() != null ? port.getDescriptivePortName() : UNKNOWN);
                device.setVendorId(port.getVendorID());
                device.setProductId(port.getProductID());
                device.setOpened(port.isOpen());
                devices.add(device);
            }
        } catch (Exception e) {
            // 枚举失败（无权限、驱动缺失）不影响服务启动，只记日志
            log.warn("[serial] 列出串口设备失败: {}", e.getMessage(), e);
        }
        return devices;
    }
}
