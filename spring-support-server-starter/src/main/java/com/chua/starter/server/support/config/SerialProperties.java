package com.chua.starter.server.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 串口（Serial）远控参数
 * <p>
 * 通过 {@code plugin.server.support.serial.*} 注入，完整 YAML 配置示例：
 * </p>
 *
 * <pre>{@code
 * plugin:
 *   server:
 *     support:
 *       serial:
 *         enabled: true             # 开关字段（当前仅完成属性绑定，不参与装配判断）
 *         baud-rate: 115200         # 默认波特率
 *         data-bits: 8              # 数据位（jSerialComm 合法值 5/6/7/8）
 *         stop-bits: 1              # 停止位（jSerialComm 合法值 1/2）
 *         parity: 0                 # 校验位：0 无 / 1 奇 / 2 偶 / 3 标记 / 4 空
 *         flow-control: 0           # 流控位掩码：0 关 / 1 RTS / 2 CTS / 4 DSR / 8 DTR / 16 XON/XOFF 入 / 32 出
 *         read-timeout: 1000        # 读超时（毫秒）
 *         write-timeout: 0          # 写超时（毫秒），0 表示等待发送完成
 *         exclusive: true           # 是否独占打开串口
 *         device-path-prefix: /dev/ # 设备路径规范化前缀（实际路径以主机台账为准）
 * }</pre>
 *
 * <p>字段速览：baud-rate 默认 115200；data-bits 默认 8；stop-bits 默认 1；
 * parity 默认 0（no parity）；flow-control 默认 0（关）。</p>
 *
 * @author CH
 * @since 4.0.0.48
 */
@Data
@ConfigurationProperties(prefix = "plugin.server.support.serial")
public class SerialProperties {

    /**
     * 串口远控开关字段：当前后端仅完成属性绑定，未参与任何装配或端点注册判断
     * （置 false 不会卸载 Bean，串口 WS 端点也未在本模块 {@code TerminalWebSocketConfig} 注册）
     * <p>默认值：{@code true}（配置项缺省时回落到该初始值；显式配置 false 即生效为 false）。
     * YAML 示例：{@code plugin.server.support.serial.enabled: true}</p>
     */
    private boolean enabled = true;

    /**
     * 默认波特率
     * <p>默认值：{@code 115200}（配置项缺省时回落到该初始值；无 ≤0 兜底，常用值如 9600/19200/115200）。
     * YAML 示例：{@code plugin.server.support.serial.baud-rate: 115200}</p>
     */
    // [P3C A20 豁免]
    private int baudRate = 115200;

    /**
     * 默认数据位（jSerialComm 合法值 5/6/7/8）
     * <p>默认值：{@code 8}（配置项缺省时回落到该初始值；无 ≤0 兜底）。
     * YAML 示例：{@code plugin.server.support.serial.data-bits: 8}</p>
     */
    private int dataBits = 8;

    /**
     * 默认停止位（jSerialComm 合法值 1/2）
     * <p>默认值：{@code 1}（配置项缺省时回落到该初始值；无 ≤0 兜底）。
     * YAML 示例：{@code plugin.server.support.serial.stop-bits: 1}</p>
     */
    private int stopBits = 1;

    /**
     * 默认校验位（0 无校验 / 1 奇 / 2 偶 / 3 标记 / 4 空），
     * 与 jSerialComm{@link com.fazecast.jSerialComm.SerialPort#NO_PARITY} 一致
     * <p>默认值：{@code 0}（配置项缺省时回落到该初始值；无 ≤0 兜底）。
     * YAML 示例：{@code plugin.server.support.serial.parity: 0}</p>
     */
    private int parity = 0;

    /**
     * 默认流控（0 关 / 1 RTS / 2 CTS / 4 DSR / 8 DTR / 16 XON/XOFF 入 / 32 XON/XOFF 出）
     * <p>默认值：{@code 0}（配置项缺省时回落到该初始值；多个流控位可按位或组合）。
     * YAML 示例：{@code plugin.server.support.serial.flow-control: 0}</p>
     */
    private int flowControl = 0;

    /**
     * 读超时（毫秒），只影响轮询节奏，不阻塞设备
     * <p>默认值：{@code 1000}（配置项缺省时回落到该初始值；无 ≤0 兜底，经
     * {@code SerialPort#setComPortTimeouts} 以非阻塞模式生效）。
     * YAML 示例：{@code plugin.server.support.serial.read-timeout: 1000}</p>
     */
    // [P3C A20 豁免]
    private int readTimeout = 1000;

    /**
     * 写超时（毫秒），0 表示等待发送完成
     * <p>默认值：{@code 0}（配置项缺省时回落到该初始值；0 是有意义的取值——等待发送完成，并非"未配置"）。
     * YAML 示例：{@code plugin.server.support.serial.write-timeout: 0}</p>
     */
    private int writeTimeout = 0;

    /**
     * 是否独占打开串口（大多数串口设备需要独占，避免与终端/监控系统冲突）
     * <p>默认值：{@code true}（配置项缺省时回落到该初始值；显式配置 false 即生效为 false）。
     * YAML 示例：{@code plugin.server.support.serial.exclusive: true}</p>
     */
    private boolean exclusive = true;

    /**
     * 默认设备路径前缀： Unix 系（/dev/ttyUSB0/ttyACM0）前置 /，Windows 系（COM3）无前缀
     * <p>
     * 仅用于设备路径规范化，实际路径以主机台账 {@code serverHostAddress} 字段为准
     * </p>
     * <p>默认值：{@code /dev/}（配置项缺省时回落到该初始值）。
     * YAML 示例：{@code plugin.server.support.serial.device-path-prefix: /dev/}</p>
     */
    private String devicePathPrefix = "/dev/";
}
