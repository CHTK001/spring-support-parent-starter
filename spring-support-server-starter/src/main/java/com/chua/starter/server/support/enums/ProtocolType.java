package com.chua.starter.server.support.enums;

/**
 * 远控协议类型
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
    VNC;

    /**
     * 判断是否为 guacd 承载的图形协议
     *
     * @return 是否经 guacd
     */
    public boolean isGuacdProtocol() {
        return this == SSH || this == RDP || this == VNC;
    }
}
