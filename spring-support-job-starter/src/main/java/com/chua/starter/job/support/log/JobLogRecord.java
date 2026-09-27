package com.chua.starter.job.support.log;

import lombok.Data;

/**
 * 任务执行日志的一行读取结果。
 *
 * <p>与落库实体解耦: 时间统一成毫秒时间戳 (前端自行格式化), 雪花主键以字符串暴露,
 * 避免 JS 数值精度丢失。日志行不存任务名称, 页面需要时按 {@link #jobNo} 回台账关联。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobLogRecord {

    /**
     * 日志主键 (字符串形式, 仅作行标识)
     */
    private String logId;

    /**
     * 台账业务编号
     */
    private String jobNo;

    /**
     * 处理器 Bean 名 (派发事件里的任务键)
     */
    private String jobKey;

    /**
     * 所属台账行主键 (字符串形式), 未归属到台账时为空
     */
    private String jobId;

    /**
     * 产生日志的应用名
     */
    private String app;

    /**
     * 触发方式 (CRON / DELAY / FIXED / NONE)
     */
    private String triggerType;

    /**
     * 派发结果码
     */
    private String triggerCode;

    /**
     * 执行结果码: SUCCESS / FAILURE / SKIPPED
     */
    private String executeCode;

    /**
     * 执行耗时毫秒
     */
    private Long costMs;

    /**
     * 结果说明 (失败时为异常信息, 成功时为 ok)
     */
    private String message;

    /**
     * 执行参数快照
     */
    private String executeParam;

    /**
     * 触发地址
     */
    private String address;

    /**
     * 执行节点标识
     */
    private String nodeId;

    /**
     * 日志文件相对路径 (未开启文件日志时为空)
     */
    private String filePath;

    /**
     * 触发时刻毫秒
     */
    private Long triggerTimeMs;
}
