package com.chua.starter.server.support.constants;

/**
 * 服务器资产模块常量
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ServerConstants {

    /**
     * 终端 webSocket 端点路径
    */
    public static final String WEBSOCKET_TERMINAL_PATH = "/ws/ms/server/terminal";

    /**
     * SSH 直连终端 webSocket 端点路径（不经 guacd）
    */
    public static final String WEBSOCKET_SSH_DIRECT_PATH = "/ws/ms/server/terminal/ssh";

    /**
     * VNC 直连桌面 webSocket 端点路径（不经 guacd）
    */
    public static final String WEBSOCKET_VNC_DIRECT_PATH = "/ws/ms/server/terminal/vnc";

    /**
     * SSH 串口 console 直连 webSocket 端点路径（不经 guacd）
     */
    public static final String WEBSOCKET_SERIAL_DIRECT_PATH = "/ws/ms/server/terminal/serial";

    /**
     * Telnet console 直连 webSocket 端点路径（不经 guacd）
     */
    public static final String WEBSOCKET_TELNET_DIRECT_PATH = "/ws/ms/server/terminal/telnet";

    /**
     * RDP 直连桌面 webSocket 端点路径（不经 guacd，FreeRDP 透过 JNA 驱动 C 扩展）
     */
    public static final String WEBSOCKET_RDP_DIRECT_PATH = "/ws/ms/server/terminal/rdp";

    /**
     * RDP 直连桌面网页路径（生成式：canvas 帧 + 键鼠输入）
     */
    public static final String PAGE_RDP_DIRECT = "/ms/server/remote/rdp-direct.html";

    /**
     * 串口 console 网页路径（生成式：xterm + 二进制收发）
     */
    public static final String PAGE_SERIAL_DIRECT = "/ms/server/remote/serial-direct.html";

    /**
     * Telnet console 网页路径（生成式：xterm + 原始 TCP 收发）
     */
    public static final String PAGE_TELNET_DIRECT = "/ms/server/remote/telnet-direct.html";

    /**
     * 接口根路径
    */
    public static final String API_PREFIX = "/ms/server";

    /**
     * 默认 SSH 端口
    */
    public static final int DEFAULT_SSH_PORT = 22;

    /**
     * 默认 VNC 端口
    */
    public static final int DEFAULT_VNC_PORT = 5900;

    /**
     * 默认 RDP 端口
    */
    public static final int DEFAULT_RDP_PORT = 3389;

    /**
     * 会话状态：打开
    */
    public static final String SESSION_OPEN = "OPEN";

    /**
     * 会话状态：关闭
    */
    public static final String SESSION_CLOSED = "CLOSED";

    /**
     * 执行日志类型：批量命令
    */
    public static final String EXEC_TYPE_COMMAND = "COMMAND";

    /**
     * 执行日志类型：文件分发
    */
    public static final String EXEC_TYPE_FILE = "FILE";

    /**
     * 计划任务类型：定时（后台按 cron 调度）
     */
    public static final String EXEC_JOB_TYPE_CRON = "CRON";

    /**
     * 计划任务类型：脚本（仅手动执行，不调度）
     */
    public static final String EXEC_JOB_TYPE_NONE = "NONE";

    /**
     * 执行日志状态：成功
     */
    public static final String EXEC_STATUS_SUCCESS = "SUCCESS";

    /**
     * 执行日志状态：失败
    */
    public static final String EXEC_STATUS_FAIL = "FAIL";

    /**
     * 工具类禁止实例化
     */
    private ServerConstants() {
    }
}
