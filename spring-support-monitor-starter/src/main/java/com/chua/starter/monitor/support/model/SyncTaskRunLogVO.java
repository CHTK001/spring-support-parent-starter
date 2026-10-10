package com.chua.starter.monitor.support.model;

import java.util.List;

/**
 * 一次同步执行的调试日志：触发方式、状态、起止时间与逐步过程行。
 *
 * <p>与 {@link SyncTaskRunVO}（执行结果的汇总）不同，本结构是**过程**日志：每行带
 * 时间、级别与内容（连了哪个源、执行了什么 SQL、解析出哪些列、映射怎么配对、
 * 每个文件读写多少、异常完整堆栈），用于排障「为什么读了 0 行 / 为什么写失败」。
 * 数据来自内存环形缓冲（见 {@code SyncTaskRunLogBuffer}），后端重启即清空。</p>
 *
 * <p>{@code status} 为 {@code RUNNING} 时该次执行仍在进行，{@code costMs} 是已进行
 * 毫秒数、{@code lines} 会随执行继续增长——界面据此轮询实时“直播”调试日志。</p>
 *
 * @param trigger 触发方式：{@code MANUAL} 手动 / {@code SCHEDULED} 定时 / {@code REALTIME} 实时录入
 * @param status  执行状态：{@code RUNNING} 进行中 / {@code SUCCESS} 成功 / {@code FAILED} 失败
 * @param runTime 执行开始时刻（{@code yyyy-MM-dd HH:mm:ss}）
 * @param costMs  已进行/总耗时（毫秒）；进行中为已进行时长
 * @param lines   逐步过程行（执行先后顺序，最新行在末尾）
 * @author CH
 * @since 4.0.0.43
 */
public record SyncTaskRunLogVO(
        String trigger,
        String status,
        String runTime,
        Long costMs,
        List<Line> lines) {

    /**
     * 手动触发。
     */
    public static final String TRIGGER_MANUAL = "MANUAL";

    /**
     * 定时触发。
     */
    public static final String TRIGGER_SCHEDULED = "SCHEDULED";

    /**
     * 实时录入（目录监听到新文件）触发。
     */
    public static final String TRIGGER_REALTIME = "REALTIME";

    /**
     * 进行中状态：该次执行尚未结束，日志仍在追加。
     */
    public static final String STATUS_RUNNING = "RUNNING";

    /**
     * 调试日志的单行。
     *
     * @param time    行时间（{@code HH:mm:ss.SSS}）
     * @param level   级别：{@code INFO} / {@code WARN} / {@code ERROR}
     * @param message 内容；ERROR 行可能内嵌换行（完整堆栈）
     */
    public record Line(String time, String level, String message) {
    }
}
