package com.chua.starter.server.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 串口（Serial）远控参数
 * <p>
 * 通过 {@code plugin.server.support.serial.*} 注入：
 * <ul>
 *     <li>baud-rate：波特率，默认 115200</li>
 *     <li>databits：数据位，默认 8（jSerialComm 合法值 5/6/7/8）</li>
 *     <li>stopbits：停止位，默认 1（jSerialComm 合法值 1/2）</li>
 *     <li>parity：校验位，默认 0（no parity）</li>
 *     <li>flow-control：流控，默认 0（关）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.48
 */
@Data
@ConfigurationProperties(prefix = "plugin.server.support.serial")
public class SerialProperties {

    /**
     * 串口远控开关：false 时不启用串口页面与 WS 端点，
     * 但注册表/查询接口仍然可用（仅不上线）
     */
    private boolean enabled = true;

    /**
     * 默认波特率
     */
    // [P3C A20 豁免]
    private int baudRate = 115200;

    /**
     * 默认数据位
     */
    private int dataBits = 8;

    /**
     * 默认停止位
     */
    private int stopBits = 1;

    /**
     * 默认校验位（0 无校验 / 1 奇 / 2 偶 / 3 标记 / 4 空），
     * 与 jSerialComm{@link com.fazecast.jSerialComm.SerialPort#NO_PARITY} 一致
     */
    private int parity = 0;

    /**
     * 默认流控（0 关 / 1 RTS / 2 CTS / 4 DSR / 8 DTR / 16 XON/XOFF 入 / 32 XON/XOFF 出）
     */
    private int flowControl = 0;

    /**
     * 读超时（毫秒），只影响轮询节奏，不阻塞设备
     */
    // [P3C A20 豁免]
    private int readTimeout = 1000;

    /**
     * 写超时（毫秒），0 表示等待发送完成
     */
    private int writeTimeout = 0;

    /**
     * 是否独占打开串口（大多数串口设备需要独占，避免与终端/监控系统冲突）
     */
    private boolean exclusive = true;

    /**
     * 默认设备路径前缀： Unix 系（/dev/ttyUSB0/ttyACM0）前置 /，Windows 系（COM3）无前缀
     * <p>
     * 仅用于设备路径规范化，实际路径以主机台账 {@code serverHostAddress} 字段为准
     * </p>
     */
    private String devicePathPrefix = "/dev/";
}
