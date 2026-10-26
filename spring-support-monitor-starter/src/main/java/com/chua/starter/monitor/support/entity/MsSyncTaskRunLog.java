package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步执行日志实体（{@code ms_sync_task_run_log}）。
 *
 * <p>一次执行一行，把该次执行的过程日志整段落库。行内不建从表：
 * 调试日志只在「点开某一次执行」时整体读取，没有按行查询的需求，
 * 拆从表会换来一次 JOIN 和一次级联删除，而这里只多一个 LONGTEXT 列。</p>
 *
 * <p><b>与内存缓冲的分工</b>：{@code SyncTaskRunLogBuffer} 仍负责「正在跑的那次」
 * 的实时追加（每秒轮询必须走内存，不能反复读库）；执行结束时由它把定格的快照
 * 写入本表，此后即使后端重启，历史执行日志仍可查。</p>
 *
 * <p><b>不继承 {@code SysBase}</b>：执行日志是系统自动产生的过程数据，没有「谁创建 /
 * 谁更新」的语义，也不该被界面以创建人身份回写。时间字段直接从执行本身取。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Data
@Schema(description = "同步执行日志")
@TableName("ms_sync_task_run_log")
public class MsSyncTaskRunLog {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 任务 ID（{@code ms_sync_task.id}）；任务删除后本行保留，便于事后追溯
     */
    @TableField("ms_sync_task_run_log_task_id")
    @Schema(description = "任务 ID", example = "31")
    private Long runLogTaskId;

    /**
     * 触发方式：MANUAL 手动 / SCHEDULED 定时 / REALTIME 实时录入
     */
    @TableField("ms_sync_task_run_log_trigger")
    @Schema(description = "触发方式 MANUAL/SCHEDULED/REALTIME", example = "MANUAL")
    private String runLogTrigger;

    /**
     * 执行状态：SUCCESS 成功 / FAILED 失败
     */
    @TableField("ms_sync_task_run_log_status")
    @Schema(description = "执行状态 SUCCESS/FAILED", example = "SUCCESS")
    private String runLogStatus;

    /**
     * 执行开始时刻
     */
    @TableField("ms_sync_task_run_log_run_time")
    @Schema(description = "执行开始时刻")
    private LocalDateTime runLogRunTime;

    /**
     * 本次执行总耗时（毫秒）
     */
    @TableField("ms_sync_task_run_log_cost_ms")
    @Schema(description = "本次执行总耗时(毫秒)", example = "112")
    private Long runLogCostMs;

    /**
     * 过程日志正文：逐行 {@code 时间\t级别\t内容}，以换行分隔。
     *
     * <p><b>为什么用文本而不是 JSON 数组</b>：日志内容里本来就含换行（ERROR 堆栈）
     * 与引号，JSON 要额外转义；而这份数据的唯一用途是原样还给界面重新拼成行，
     * 以 {@code \t} 分列可以在「不转义正文」的前提下稳定还原三列。</p>
     */
    @TableField("ms_sync_task_run_log_lines")
    @Schema(description = "过程日志正文：逐行「时间\\t级别\\t内容」换行分隔")
    private String runLogLines;

    /**
     * 日志行数（列表展示用，避免为了显示「N 行」去解析正文）
     */
    @TableField("ms_sync_task_run_log_line_count")
    @Schema(description = "日志行数", example = "12")
    private Integer runLogLineCount;

    /**
     * 入库时刻
     */
    @TableField("ms_sync_task_run_log_create_time")
    @Schema(description = "入库时刻")
    private LocalDateTime runLogCreateTime;
}
