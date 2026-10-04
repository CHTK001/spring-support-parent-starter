package com.chua.starter.job.support.manage.vo;

import com.chua.starter.job.support.log.JobLogRecord;

/**
 * 任务执行日志出参。
 *
 * @param logId        日志主键 (字符串, 仅作行标识)
 * @param jobNo        台账业务编号
 * @param jobKey       处理器 Bean 名
 * @param jobId        所属台账行主键 (字符串)
 * @param app          产生日志的应用名
 * @param triggerType  触发方式
 * @param triggerCode  派发结果码
 * @param executeCode  执行结果码: SUCCESS / FAILURE / SKIPPED
 * @param costMs       执行耗时毫秒
 * @param message      结果说明
 * @param executeParam 执行参数快照
 * @param address      触发地址
 * @param nodeId       执行节点标识
 * @param filePath     日志文件相对路径
 * @param triggerTime  触发时刻毫秒
 * @since 4.0.0.42
 * @author CH
 */
public record JobLogVO(
        String logId,
        String jobNo,
        String jobKey,
        String jobId,
        String app,
        String triggerType,
        String triggerCode,
        String executeCode,
        Long costMs,
        String message,
        String executeParam,
        String address,
        String nodeId,
        String filePath,
        Long triggerTime) {

    /**
     * 由读取记录构造出参。
     *
     * @param record 记录
     * @return 出参, 记录为空时返回 {@code null}
     */
    public static JobLogVO of(JobLogRecord record) {
        if (record == null) {
            return null;
        }
        return new JobLogVO(record.logId(), record.jobNo(), record.jobKey(), record.jobId(),
                record.app(), record.triggerType(), record.triggerCode(), record.executeCode(),
                record.costMs(), record.message(), record.executeParam(), record.address(),
                record.nodeId(), record.filePath(), record.triggerTimeMs());
    }
}
