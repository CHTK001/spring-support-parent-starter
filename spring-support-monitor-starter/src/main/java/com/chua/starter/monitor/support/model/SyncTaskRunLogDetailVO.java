package com.chua.starter.monitor.support.model;

import java.util.List;

/**
 * 一次同步执行的日志详情（记录 + 完整过程行）。
 *
 * <p>「同步日志」菜单页点开某条记录时返回：在 {@link SyncTaskRunLogItemVO}
 * 的摘要字段基础上，补上本次执行的逐步过程日志行（连了哪个源、执行了什么
 * SQL、解析出哪些列、每个文件读写多少、异常完整堆栈），用于排障。</p>
 *
 * @param id         日志主键（{@code ms_sync_task_run_log.id}）
 * @param taskId     任务 ID
 * @param taskName   任务名（联 {@code ms_sync_task} 得到）
 * @param trigger    触发方式：{@code MANUAL} 手动 / {@code SCHEDULED} 定时 / {@code REALTIME} 实时录入
 * @param status     执行状态：{@code SUCCESS} 成功 / {@code FAILED} 失败
 * @param runTime    执行开始时刻（{@code yyyy-MM-dd HH:mm:ss}）
 * @param costMs     本次总耗时（毫秒）
 * @param lineCount  过程日志行数
 * @param lines      逐步过程行（执行先后顺序）
 * @param readCount  本轮读取行数（入库列；存量行为 null 时按正文动态解析）
 * @param writeCount 本轮写入行数（入库列；存量行为 null 时按正文动态解析）
 * @author CH
 * @since 4.0.0.44
 */
public record SyncTaskRunLogDetailVO(
        Long id,
        Long taskId,
        String taskName,
        String trigger,
        String status,
        String runTime,
        Long costMs,
        Integer lineCount,
        List<SyncTaskRunLogVO.Line> lines,
        Long readCount,
        Long writeCount) {
}
