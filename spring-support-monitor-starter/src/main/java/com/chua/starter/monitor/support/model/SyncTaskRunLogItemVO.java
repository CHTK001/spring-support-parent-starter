package com.chua.starter.monitor.support.model;

/**
 * 一次同步执行的日志记录（列表行，不含过程行）。
 *
 * <p>供「同步日志」菜单页的全局列表使用：跨任务分页列出每一次执行，
 * 与 {@link SyncTaskRunLogVO}（单任务弹窗、按任务查）不同，本结构带
 * {@code taskName} 与日志主键 {@code id}，让用户能从「所有任务」视角
 * 看到每次执行，并按 id 跳转详情。</p>
 *
 * <p>过程行（{@code lines}）不放在列表结构里：LONGTEXT 正文在列表场景
 * 一次只展示「行数」，点开详情再整体读取，避免每行都拖一大段文本。</p>
 *
 * @param id        日志主键（{@code ms_sync_task_run_log.id}）
 * @param taskId    任务 ID
 * @param taskName  任务名（联 {@code ms_sync_task} 得到）
 * @param trigger   触发方式：{@code MANUAL} 手动 / {@code SCHEDULED} 定时 / {@code REALTIME} 实时录入
 * @param status    执行状态：{@code SUCCESS} 成功 / {@code FAILED} 失败
 * @param runTime   执行开始时刻（{@code yyyy-MM-dd HH:mm:ss}）
 * @param costMs    本次总耗时（毫秒）
 * @param lineCount 过程日志行数（列表展示用，免解析正文）
 * @author CH
 * @since 4.0.0.44
 */
public record SyncTaskRunLogItemVO(
        Long id,
        Long taskId,
        String taskName,
        String trigger,
        String status,
        String runTime,
        Long costMs,
        Integer lineCount) {
}
