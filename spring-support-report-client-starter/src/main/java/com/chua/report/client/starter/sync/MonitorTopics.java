package com.chua.report.client.starter.sync;

/**
   * 监控 通信主题常量
 *
   * @版本 1.0.0
 * @since 2024/12/05
 * @author CH
 */
public interface MonitorTopics {

 // ==================== 作业 ====================
    String JOB_DISPATCH = "monitor/job/dispatch";
    String JOB_CANCEL = "monitor/job/cancel"; // 作业cancel
    String JOB_RESULT = "monitor/job/result"; // 作业结果

 // ==================== 文件 ====================
    String FILE_REQUEST = "monitor/file/request";
    String FILE_RESPONSE = "monitor/file/response"; // 文件响应

    // ==================== Device Report ====================
    String DEVICE_METRICS = "monitor/device/metrics";
    String DEVICE_INFO = "monitor/device/info"; // device信息

    // ==================== App Report ====================
    /** 应用信息上报（定时） */
    String APP_REPORT = "monitor/app/report";

    // ==================== API Control ====================
    /** API 功能开关控制 */
    String API_FEATURE_CONTROL = "monitor/control/api-feature";

 // ==================== 日志 配置 ====================
    /** 日志级别配置 */
    String LOGGING_CONFIG = "monitor/control/log-level";

 // ==================== mybatis 配置 ====================
    /** MyBatis 配置管理 */
    String MYBATIS_CONFIG = "monitor/control/mybatis";

    // ==================== URL QPS Statistics ====================
    /** URL 请求统计上报 */
    String URL_QPS_REPORT = "monitor/url/qps-report";

 // ==================== 节点 Control ====================
    /** 节点重启控制 */
    String NODE_RESTART = "monitor/control/node-restart";
    /** 节点关闭控制 */
    String NODE_SHUTDOWN = "monitor/control/node-shutdown";

 // ==================== 节点 Maintenance ====================
    /** 节点配置备份 */
    String NODE_BACKUP = "monitor/maintenance/backup";
    /** 节点升级 */
    String NODE_UPGRADE = "monitor/maintenance/upgrade";
    /** 节点配置还原 */
    String NODE_RESTORE = "monitor/maintenance/restore";

 // ==================== 日志 Push ====================
    /** 日志推送（智能体 -> 监控） */
    String LOG_PUSH = "monitor/log/push";
    /** 日志订阅请求（监控 -> 智能体） */
    String LOG_SUBSCRIBE = "monitor/log/subscribe";
    /** 日志取消订阅（监控 -> 智能体） */
    String LOG_UNSUBSCRIBE = "monitor/log/unsubscribe";
}
