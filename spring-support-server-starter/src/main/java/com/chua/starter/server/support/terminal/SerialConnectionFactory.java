package com.chua.starter.server.support.terminal;

import com.chua.starter.server.support.config.SerialProperties;
import com.chua.starter.server.support.entity.MsServerHost;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 串口连接工厂
 * <p>
 * 从主机记录拉取串口设备路径与连接参数，组装 {@link SerialPort} 并打开：
 * <ul>
 *   <li>设备路径：主机台账的 {@code serverHostAddress}（Unix 上形如 /dev/ttyUSB0、
 *       Windows 上形如 COM3），不能为空时原样使用；为空时尝试解析 {@code serverHostPort}</li>
 *   <li>串口参数：来自 {@link SerialProperties}，由系统配置表 PRIOR 覆盖</li>
 * </ul>
 * 打开失败时不逐层抛原子异常，而是包装为您可读原因。
 *
 * @author CH
 * @since 4.0.0.48
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SerialConnectionFactory {

    /**
     * 串口参数
     */
    private final SerialProperties properties;

    /**
     * 从主机记录构造串口会话（只取连接参数，不注册、不创建会话）
     * <p>
     * 主机记录写死的回填规则：地址字段写设备路径，端口字段写外挂控制口或留空；
     * 台账只管信息，端口解析由 {@code SerialConnectionFactory} 完成。
     * </p>
     *
     * @param host 主机记录
     * @return 已打开的串口，打开失败时抛 {@link IllegalStateException}
     */
    public SerialPort open(MsServerHost host) {
        String device = resolveDevice(host);
        if (device == null || device.isBlank()) {
            throw new IllegalStateException("未配置串口设备，请设置主机台账的「主机地址」字段");
        }
        return openDevice(device);
    }

    /**
     * 从主机记录解析出唯一设备路径
     * <p>
     * 一致口径：地址优先、端口兜底，避免 Windows（COM3）与 Unix（/dev/ttyUSB0）格式
     * 对自然语言解释不一致。地址为一个纯数字时回退到端口（无地址时）。
     * </p>
     *
     * @param host 主机记录
     * @return 设备路径或空
     */
    public static String resolveDevice(MsServerHost host) {
        String address = host.getServerHostAddress() != null ? host.getServerHostAddress().trim() : "";
        // Unix 路径要带前缀（/dev/...），Windows COM 口不带；地址非数字时原样使用
        if (!address.isEmpty() && !address.matches("[0-9]+")) {
            return address;
        }
        Integer port = host.getServerHostPort() != null && host.getServerHostPort() > 0
                ? host.getServerHostPort() : host.getServerHostRemotePort();
        if (port == null || port <= 0) {
            return null;
        }
        // 端口格式为数字时走 COMn（Windows）约定，否则回退到 /dev/ttyUSBn（Unix）
        return address.isEmpty() ? String.valueOf(port) : "/dev/ttyUSB" + port;
    }

    /**
     * 打开串口设备
     * <p>
     * 这是阻塞式系统调用：Windows 下可能因为驱动加载慢/COM 口被占用而多试一次。
     * </p>
     *
     * @param device 设备路径
     * @return 已打开的串口
     */
    public SerialPort openDevice(String device) {
        try {
            SerialPort port = SerialPort.getCommPort(normalizePortName(device));
            boolean setParams = port.setComPortParameters(
                    properties.getBaudRate(), properties.getDataBits(),
                    properties.getStopBits(), properties.getParity());
            if (!setParams) {
                throw new IllegalStateException("设置串口参数失败: " + device);
            }
            boolean setFlow = port.setFlowControl(properties.getFlowControl());
            if (!setFlow) {
                throw new IllegalStateException("设置流控失败: " + device);
            }
            // 非阻塞读 + 超时，使读线程可被 interrupt 回收
            port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, properties.getReadTimeout(),
                    properties.getWriteTimeout());
            port.openPort();
            if (!port.isOpen()) {
                throw new IllegalStateException("打开串口失败: " + device);
            }
            return port;
        } catch (SerialPortInvalidPortException e) {
            log.warn("[serial] 不可用的串口设备 device={}: {}", device, e.getMessage());
            throw new IllegalStateException("串口设备不在系统上: " + device, e);
        } catch (IllegalStateException e) {
            // 业务约定异常，保持原样
            throw e;
        } catch (Exception e) {
            log.warn("[serial] 打开串口失败 device={}: {}", device, e.getMessage(), e);
            throw new IllegalStateException("打开串口失败: " + device + " - " + e.getMessage(), e);
        }
    }

    /**
     * 规范化设备路径：Windows 下如 COM3 一律大写（jSerialComm 不能区分的统一），
     * Unix 下 /dev/xxx 源样输出
     *
     * @param device 设备路径
     * @return 规范后名称
     */
    public static String normalizePortName(String device) {
        if (device == null) {
            return "COM1";
        }
        String normalized = device.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        // Windows COMn 不区分大小写但全部统一为 COM 大写，避免驱动提取不到
        return normalized.startsWith("/") ? normalized : normalized.toUpperCase();
    }
}
