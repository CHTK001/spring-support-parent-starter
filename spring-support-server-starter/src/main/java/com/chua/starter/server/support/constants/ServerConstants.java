package com.chua.starter.server.support.constants;

/**
 * 服务器资产模块常量
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ServerConstants {

    /** 终端 WebSocket 端点路径 */
    public static final String WEBSOCKET_TERMINAL_PATH = "/ws/ms/server/terminal";

    /** 接口根路径 */
    public static final String API_PREFIX = "/ms/server";

    /** 默认 SSH 端口 */
    public static final int DEFAULT_SSH_PORT = 22;

    /** 会话状态：打开 */
    public static final String SESSION_OPEN = "OPEN";

    /** 会话状态：关闭 */
    public static final String SESSION_CLOSED = "CLOSED";

    /** 执行日志类型：批量命令 */
    public static final String EXEC_TYPE_COMMAND = "COMMAND";

    /** 执行日志类型：文件分发 */
    public static final String EXEC_TYPE_FILE = "FILE";

    /** 执行日志状态：成功 */
    public static final String EXEC_STATUS_SUCCESS = "SUCCESS";

    /** 执行日志状态：失败 */
    public static final String EXEC_STATUS_FAIL = "FAIL";

    /**
     * 工具类禁止实例化
     */
    private ServerConstants() {
    }
}
