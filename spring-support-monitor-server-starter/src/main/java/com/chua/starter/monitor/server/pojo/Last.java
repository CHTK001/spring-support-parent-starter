package com.chua.starter.monitor.server.pojo;

import lombok.Data;

/**
 * Last类用于封装用户登录和登出的信息。
 * 包含用户名称、登录来源、登录时间及登出时间。
 *
 * last命令
 * @author CH
 * @since 2024/7/4
 */
@Data
public class Last {

    /**
     * 用户名，用于标识登录系统的用户。
     */
    private String user;

    /**
     * 登录来源，指示用户登录的设备或位置。
     */
    private String from;

    /**
     * 城市
     */
    private String city;

    /**
     * 登录时间，记录用户成功登录的时刻。
     */
    private String loginTime;

    /**
     * 登出时间，记录用户结束会话的时刻。
     */
    private String logoutTime;
}
