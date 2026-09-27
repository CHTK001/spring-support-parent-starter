package com.chua.starter.job.support.manage.vo;

import com.chua.starter.job.support.log.JobLogRecord;
import lombok.Data;

/**
 * 任务执行日志出参。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobLogVO {

    /**
     * 日志主键 (字符串, 仅作行标识)
     */
    private String logId;

    /**
     * 台账业务编号
     */
    private String jobNo;

    /**
     * 处理器 Bean 名
     */
    private String jobKey;

    /**
     * 所属台账行主键 (字符串)
     */
    private String jobId;

    /**
     * 产生日志的应用名
     */
    private String app;

    /**
     * 触发方式
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
     * 结果说明
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
     * 日志文件相对路径
     */
    private String filePath;

    /**
     * 触发时刻毫秒
     */
    private Long triggerTime;

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
        JobLogVO vo = new JobLogVO();
        vo.setLogId(record.getLogId());
        vo.setJobNo(record.getJobNo());
        vo.setJobKey(record.getJobKey());
        vo.setJobId(record.getJobId());
        vo.setApp(record.getApp());
        vo.setTriggerType(record.getTriggerType());
        vo.setTriggerCode(record.getTriggerCode());
        vo.setExecuteCode(record.getExecuteCode());
        vo.setCostMs(record.getCostMs());
        vo.setMessage(record.getMessage());
        vo.setExecuteParam(record.getExecuteParam());
        vo.setAddress(record.getAddress());
        vo.setNodeId(record.getNodeId());
        vo.setFilePath(record.getFilePath());
        vo.setTriggerTime(record.getTriggerTimeMs());
        return vo;
    }
}
