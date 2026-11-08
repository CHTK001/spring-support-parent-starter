// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.enums;

/**
 * 远控协议类型
 *
 * <h3>协议阵营</h3>
 * <ul>
 *   <li>guacd 中转通道：SSH 终端、RDP 桌面、VNC 桌面（经 guacd 进行协议转换）</li>
 *   <li>平台直连通道：SERIAL 串口终端 + TELNET console 终端 —— 均由平台自研
 *       直连处理器（Serial / Telnet）原样桥接收发流，无需 guacd 中转；MANAGEMENT（命令执行）/
 *       SFTP（文件管理）不走远控通道，不在本枚举里占位。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public enum ProtocolType {

    /**
     * SSH 终端
    */
    SSH,

    /**
     * SFTP 文件管理（REST 实现，不经 guacd）
    */
    SFTP,

    /**
     * 远程桌面
    */
    RDP,

    /**
     * VNC 桌面
    */
    VNC,

    /**
     * 串口终端（platform direct）
     * <p>
     * 不经 guacd，直接走 {@link com.chua.starter.server.support.terminal.handler.SerialWebSocketHandler}
     * 桥接：浏览器收发的字节流原样进出串口设备。
     * </p>
    */
    SERIAL,

    /**
     * Telnet console 终端（platform direct）
     * <p>
     * 不经 guacd，由 {@link com.chua.starter.server.support.terminal.telnet.TelnetWebSocketHandler}
     * 原样桥接：浏览器收发的字节流原样进出目标主机的 Telnet 服务（TCP 默认 23）。
     * </p>
    */
    TELNET;

    /**
     * 判断是否为 guacd 承载的图形协议
     * <p>
     * 目前仅 SSH（终端）/ RDP（桌面）/ VNC（桌面）经 guacd 中转；
     * SERIAL / TELNET 等非图形协议均走平台自研直连通道。
     * </p>
     *
     * @return 是否经 guacd
     */
    public boolean isGuacdProtocol() {
        return this == SSH || this == RDP || this == VNC;
    }

    /**
     * 判断协议是否由平台自有客户端直连承载（不经 guacd）
     * <p>
     * 在主机位图的「远控通道」列筛选、连接页签路由里统一使用这个口径：
     * guacd 通道只管 SSH/RDP/VNC，SERIAL/TELNET 被归类为平台直连通道。
     * </p>
     *
     * @return 是否平台直连
     */
    public boolean isPlatformDirectProtocol() {
        return this == SERIAL || this == TELNET;
    }
}
