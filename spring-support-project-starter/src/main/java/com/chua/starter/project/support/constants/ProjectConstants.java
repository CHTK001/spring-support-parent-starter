package com.chua.starter.project.support.constants;

/**
* 发布项目管理模块常量
*
* @author CH
* @since 4.0.0.42
 */
public final class ProjectConstants {

    /** 接口根路径 */
    public static final String API_PREFIX = "/ms/project";

    /** 部署操作类型：部署 */
    public static final String ACTION_DEPLOY = "DEPLOY";

    /** 部署操作类型：升级 */
    public static final String ACTION_UPGRADE = "UPGRADE";

    /** 部署操作类型：启动 */
    public static final String ACTION_START = "START";

    /** 部署操作类型：停止 */
    public static final String ACTION_STOP = "STOP";

    /** 部署操作类型：重启 */
    public static final String ACTION_RESTART = "RESTART";

    /** 部署操作类型：回滚 */
    public static final String ACTION_ROLLBACK = "ROLLBACK";

    /** 部署操作类型：守护检测 */
    public static final String ACTION_WATCHDOG = "WATCHDOG";

    /** 日志状态：成功 */
    public static final String LOG_STATUS_SUCCESS = "SUCCESS";

    /** 日志状态：失败 */
    public static final String LOG_STATUS_FAIL = "FAIL";

    /** 日志状态：进行中 */
    public static final String LOG_STATUS_RUNNING = "RUNNING";

    /** 运行时状态：运行中 */
    public static final String RUNTIME_RUNNING = "RUNNING";

    /** 运行时状态：已停止 */
    public static final String RUNTIME_STOPPED = "STOPPED";

    /** 运行时状态：未知 */
    public static final String RUNTIME_UNKNOWN = "UNKNOWN";

    /** 运行时状态：部署中 */
    public static final String RUNTIME_DEPLOYING = "DEPLOYING";

    /** 运行时状态：升级中 */
    public static final String RUNTIME_UPGRADING = "UPGRADING";

    /** 运行时状态：重启中 */
    public static final String RUNTIME_RESTARTING = "RESTARTING";

    /** 运行时状态：回滚中 */
    public static final String RUNTIME_ROLLING_BACK = "ROLLING_BACK";

    /** 推送模块名：project */
    public static final String PUSH_MODULE = "project";

    /** 推送事件：部署进度 */
    public static final String PUSH_EVENT_DEPLOY_PROGRESS = "deploy-progress";

    /** 推送事件：项目指标实时值 */
    public static final String PUSH_EVENT_PROJECT_METRIC = "project-metric";

    /** 健康检查类型：端口 */
    public static final String HEALTH_TYPE_PORT = "PORT";

    /** 健康检查类型：HTTP */
    public static final String HEALTH_TYPE_HTTP = "HTTP";

    /** 健康检查类型：进程 */
    public static final String HEALTH_TYPE_PROCESS = "PROCESS";

    /** 运行时状态：健康 */
    public static final String RUNTIME_HEALTHY = "HEALTHY";

    /** 运行时状态：不健康 */
    public static final String RUNTIME_UNHEALTHY = "UNHEALTHY";

    /** 覆盖模式：直接覆盖（同名覆盖，保留其余文件） */
    public static final String OVERWRITE_MODE_OVERWRITE = "OVERWRITE";

    /** 覆盖模式：全量替换（清空部署目录后整体释放） */
    public static final String OVERWRITE_MODE_REPLACE = "REPLACE";

    /**
    * 工具类禁止实例化
     */
    private ProjectConstants() {
    }
}