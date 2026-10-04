package com.chua.starter.job.support.log;

/**
 * 任务执行日志的一行读取结果。
 *
 * <p>与落库实体解耦: 时间统一成毫秒时间戳 (前端自行格式化), 雪花主键以字符串暴露,
 * 避免 JS 数值精度丢失。日志行不存任务名称, 页面需要时按 {@link #jobNo} 回台账关联。</p>
 *
 * @param logId         日志主键 (字符串形式, 仅作行标识)
 * @param jobNo         台账业务编号
 * @param jobKey        处理器 Bean 名 (派发事件里的任务键)
 * @param jobId         所属台账行主键 (字符串形式), 未归属到台账时为空
 * @param app           产生日志的应用名
 * @param triggerType   触发方式 (CRON / DELAY / FIXED / NONE)
 * @param triggerCode   派发结果码
 * @param executeCode   执行结果码: SUCCESS / FAILURE / SKIPPED
 * @param costMs        执行耗时毫秒
 * @param message       结果说明 (失败时为异常信息, 成功时为 ok)
 * @param executeParam  执行参数快照
 * @param address       触发地址
 * @param nodeId        执行节点标识
 * @param filePath      日志文件相对路径 (未开启文件日志时为空)
 * @param triggerTimeMs 触发时刻毫秒
 * @since 4.0.0.42
 * @author CH
 */
public record JobLogRecord(
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
        Long triggerTimeMs) {
}
