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
    String DEVICE_PING = "monitor/device/ping";
}
