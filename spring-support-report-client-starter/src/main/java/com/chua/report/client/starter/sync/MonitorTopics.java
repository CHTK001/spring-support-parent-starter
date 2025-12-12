package com.chua.report.client.starter.sync;

/**
 * Monitor 通信主题常量
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 */
public interface MonitorTopics {

    // ==================== Job ====================
    String JOB_DISPATCH = "monitor/job/dispatch";
    String JOB_CANCEL = "monitor/job/cancel";
    String JOB_RESULT = "monitor/job/result";

    // ==================== File ====================
    String FILE_REQUEST = "monitor/file/request";
    String FILE_RESPONSE = "monitor/file/response";

    // ==================== Device Report ====================
    String DEVICE_METRICS = "monitor/device/metrics";
    String DEVICE_INFO = "monitor/device/info";

    // ==================== App Report ====================
    /** 应用信息上报（定时） */
    String APP_REPORT = "monitor/app/report";

    // ==================== API Control ====================
    /** API 功能开关控制 */
    String API_FEATURE_CONTROL = "monitor/control/api-feature";

    // ==================== Logging Config ====================
    /** 日志级别配置 */
    String LOGGING_CONFIG = "monitor/control/log-level";

    // ==================== MyBatis Config ====================
    /** MyBatis 配置管理 */
    String MYBATIS_CONFIG = "monitor/control/mybatis";

    // ==================== URL QPS Statistics ====================
    /** URL 请求统计上报 */
    String URL_QPS_REPORT = "monitor/url/qps-report";

    // ==================== Node Control ====================
    /** 节点重启控制 */
    String NODE_RESTART = "monitor/control/node-restart";
    /** 节点关闭控制 */
    String NODE_SHUTDOWN = "monitor/control/node-shutdown";

    // ==================== Node Maintenance ====================
    /** 节点配置备份 */
    String NODE_BACKUP = "monitor/maintenance/backup";
    /** 节点升级 */
    String NODE_UPGRADE = "monitor/maintenance/upgrade";
    /** 节点配置还原 */
    String NODE_RESTORE = "monitor/maintenance/restore";
}
