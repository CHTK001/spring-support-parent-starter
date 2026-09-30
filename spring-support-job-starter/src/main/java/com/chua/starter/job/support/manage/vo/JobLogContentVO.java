package com.chua.starter.job.support.manage.vo;

/**
 * 单次执行的日志正文。
 *
 * <p>数据源是 {@code FileJobLogSink} 落的「每次执行一个文件」的日志，路径由日志行的
 * {@code sys_job_log_file_path} 记录（相对 {@code plugin.job.log.base-path}）。
 * 控制台据此提供「查看日志」入口，把这一轮到底打了什么日志读出来。</p>
 *
 * <p>{@code sys_job_log_trigger_msg} 只有 4000 字符且是「结果说明」，长任务的业务日志会被
 * 截断；完整正文只在这份文件里，所以两者不是重复功能。</p>
 *
 * @param filePath  日志文件相对路径；文件日志未启用时为空串
 * @param exists    文件是否存在
 * @param truncated 文件超过读取上限、只返回了尾部时为 true
 * @param content   日志正文；不存在或未启用时为空串
 * @since 4.0.0.42
 * @author CH
 */
public record JobLogContentVO(String filePath, boolean exists, boolean truncated, String content) {
}
