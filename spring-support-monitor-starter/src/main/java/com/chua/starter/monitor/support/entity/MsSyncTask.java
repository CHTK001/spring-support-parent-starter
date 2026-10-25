package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 数据同步任务实体（{@code ms_sync_task}）。
 *
 * <p>一条任务描述「从哪个 jdbc 数据源按什么 SQL 读、按哪些字段映射转换、以什么模式写进
 * 哪个 jdbc 数据源的哪张表、要不要按 cron 周期跑」。源与目标都是数据源中心的 jdbc 台账，
 * 不单独存连接串，避免口令在本模块出现第二份。</p>
 *
 * <p>列名沿用本模块「完整表名做前缀」的规范；创建/更新人、创建/更新时间继承
 * {@link SysBase}，由 MyBatis-Plus 自动填充。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据同步任务")
@TableName("ms_sync_task")
public class MsSyncTask extends SysBase {

    /**
     * 串行版本 UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 任务名称
     */
    @TableField("ms_sync_task_name")
    @Schema(description = "任务名称", example = "订单表 -> 报表库")
    private String syncTaskName;

    /**
     * 输入源类型：JDBC / FILE / DIR
     */
    @TableField("ms_sync_task_source_type")
    @Schema(description = "输入源类型 JDBC/FILE/DIR", example = "JDBC")
    private String syncTaskSourceType;

    /**
     * 源数据源 ID（仅 JDBC 源使用；文件/目录源为 null）
     */
    @TableField("ms_sync_task_source_datasource_id")
    @Schema(description = "源数据源 ID，仅 JDBC 源使用", example = "20")
    private Long syncTaskSourceDatasourceId;

    /**
     * 源读取 SQL，必须是 SELECT（仅 JDBC 源使用；文件/目录源为 null）
     */
    @TableField("ms_sync_task_source_sql")
    @Schema(description = "源读取 SQL，必须是 SELECT，仅 JDBC 源使用")
    private String syncTaskSourceSql;

    /**
     * 输入源路径：FILE=文件路径 / DIR=目录路径（服务器本地绝对路径）
     */
    @TableField("ms_sync_task_source_path")
    @Schema(description = "输入源路径：FILE=文件 / DIR=目录（服务器本地绝对路径）")
    private String syncTaskSourcePath;

    /**
     * 输入源扩展选项 JSON：{@code {format,delimiter,sheetIndex,pattern,recursive,afterProcess}}
     */
    @TableField("ms_sync_task_source_options")
    @Schema(description = "输入源扩展选项 JSON {format,delimiter,sheetIndex,pattern,recursive,afterProcess}")
    private String syncTaskSourceOptions;

    /**
     * 目标数据源 ID
     */
    @TableField("ms_sync_task_target_datasource_id")
    @Schema(description = "目标数据源 ID", example = "21")
    private Long syncTaskTargetDatasourceId;

    /**
     * 目标表名
     */
    @TableField("ms_sync_task_target_table")
    @Schema(description = "目标表名", example = "report_order")
    private String syncTaskTargetTable;

    /**
     * 多目标列表 JSON：一次采集分别写入多张表。
     *
     * <p>每一项形如
     * {@code {"targetDatasourceId":21,"targetTable":"db.tb","writeMode":"UPSERT",
     * "syncMode":"INCREMENTAL","incFields":["created_at","id"],"columnMapping":[...],
     * "batchSize":500,"incBatchSize":1000,"lastSeq":"..."}}。</p>
     *
     * <p><b>为什么用 JSON 而不是子表</b>：目标只在「随任务整体保存/整体读取」时使用，
     * 没有独立查询需求；子表会引入额外的关联查询与级联删除，而这里换来的是
     * 「存一条任务 = 一次 insert」。旧库为空时回落到
     * {@link #syncTaskTargetDatasourceId} + {@link #syncTaskTargetTable} 单目标语义。</p>
     */
    @TableField("ms_sync_task_targets")
    @Schema(description = "多目标 JSON 数组，空表示按旧单目标字段解析")
    private String syncTaskTargets;

    /**
     * 多目标各自的增量水位 JSON：{@code {"数据源ID#库.表": "水位"}}。
     *
     * <p>与单目标时代的 {@link #syncTaskLastSeq} 并存：多目标走这里，
     * 旧单目标仍然走 {@code ms_sync_task_last_seq}。两者都由服务端执行成功后回写，
     * <b>都不接受入参指定</b>。</p>
     */
    @TableField("ms_sync_task_target_seqs")
    @Schema(description = "多目标水位 JSON，服务端回写，不可由入参指定")
    private String syncTaskTargetSeqs;

    /**
     * 字段映射 JSON 数组，空表示按同名匹配
     */
    @TableField("ms_sync_task_column_mapping")
    @Schema(description = "字段映射 JSON: [{\"source\":\"a\",\"target\":\"b\"}]，空表示按同名匹配")
    private String syncTaskColumnMapping;

    /**
     * 写入模式
     */
    @TableField("ms_sync_task_write_mode")
    @Schema(description = "写入模式 INSERT/REPLACE/UPSERT/OVERWRITE", example = "INSERT")
    private String syncTaskWriteMode;

    /**
     * 批量提交条数
     */
    @TableField("ms_sync_task_batch_size")
    @Schema(description = "批量提交条数", example = "500")
    private Integer syncTaskBatchSize;

    /**
     * 定时表达式，空表示仅手动执行
     */
    @TableField("ms_sync_task_cron")
    @Schema(description = "定时表达式(Spring 6 段 cron)，空表示仅手动执行", example = "0 0/5 * * * ?")
    private String syncTaskCron;

    /**
     * 是否启用定时
     */
    @TableField("ms_sync_task_enabled")
    @Schema(description = "是否启用定时 1启用 0停用", example = "1")
    private Integer syncTaskEnabled;

    /**
     * 最近一次执行时间
     */
    @TableField("ms_sync_task_last_run_time")
    @Schema(description = "最近一次执行时间")
    private LocalDateTime syncTaskLastRunTime;

    /**
     * 最近一次执行状态
     */
    @TableField("ms_sync_task_last_status")
    @Schema(description = "最近一次状态 SUCCESS/FAILED/RUNNING", example = "SUCCESS")
    private String syncTaskLastStatus;

    /**
     * 最近一次读取行数
     */
    @TableField("ms_sync_task_last_read_count")
    @Schema(description = "最近一次读取行数", example = "1000")
    private Long syncTaskLastReadCount;

    /**
     * 最近一次写入行数
     */
    @TableField("ms_sync_task_last_write_count")
    @Schema(description = "最近一次写入行数", example = "1000")
    private Long syncTaskLastWriteCount;

    /**
     * 最近一次耗时（毫秒）
     */
    @TableField("ms_sync_task_last_cost_ms")
    @Schema(description = "最近一次耗时(毫秒)", example = "320")
    private Long syncTaskLastCostMs;

    /**
     * 最近一次结果说明或错误信息
     */
    @TableField("ms_sync_task_last_message")
    @Schema(description = "最近一次结果说明/错误信息")
    private String syncTaskLastMessage;

    /**
     * 同步模式：FULL 全量 / INCREMENTAL 增量。
     *
     * <p>历史行该列为空时按 {@code FULL} 解析（见 {@code SyncMode.from}）。</p>
     */
    @TableField("ms_sync_task_sync_mode")
    @Schema(description = "同步模式 FULL 全量 / INCREMENTAL 增量", example = "FULL")
    private String syncTaskSyncMode;

    /**
     * 增量字段（仅增量模式）：JDBC 源包裹进源 SQL 做水位过滤，HTTP 源作为查询参数携带。
     *
     * <p><b>支持多字段（复合水位）</b>：按用户选择顺序以逗号分隔保存在同一列里，
     * 如 {@code created_at,id}。顺序即字典序比较顺序，不能重排。每个字段名都只允许
     * 字母、数字、下划线（服务端逐个校验，防注入）。单字段任务写入的就是裸字段名，
     * 与改造前完全兼容。</p>
     */
    @TableField("ms_sync_task_inc_field")
    @Schema(description = "增量字段（仅增量模式），多个按顺序逗号分隔，如 created_at,id")
    private String syncTaskIncField;

    /**
     * 增量每批数量（仅增量模式）：单次执行最多同步条数，空表示不限。
     */
    @TableField("ms_sync_task_inc_batch_size")
    @Schema(description = "增量每批数量：单次执行最多同步条数，空表示不限", example = "10000")
    private Integer syncTaskIncBatchSize;

    /**
     * 增量水位：上一次成功同步到的最大增量字段值，下一轮只取新于它的数据。
     *
     * <p>服务端执行成功后回写，<b>不接受入参指定</b>（与最近一次执行结果同理，
     * 否则界面可以伪造水位跳过数据）。</p>
     */
    @TableField("ms_sync_task_last_seq")
    @Schema(description = "增量水位（服务端执行成功后回写，不可由入参指定）")
    private String syncTaskLastSeq;

    /**
     * Hook 通知地址（HTTP 回调）：空表示不通知。
     */
    @TableField("ms_sync_task_hook_url")
    @Schema(description = "Hook 通知地址（http/https），空表示不通知")
    private String syncTaskHookUrl;

    /**
     * Hook 通知时机：DONE 完成通知 / ROW 每条数据+完成。全量模式固定按完成通知处理。
     */
    @TableField("ms_sync_task_hook_notify")
    @Schema(description = "Hook 通知时机 DONE 完成通知 / ROW 每条数据+完成", example = "DONE")
    private String syncTaskHookNotify;
}