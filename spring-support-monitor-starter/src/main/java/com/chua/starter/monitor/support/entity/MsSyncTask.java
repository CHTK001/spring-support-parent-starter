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
     * 源数据源 ID
     */
    @TableField("ms_sync_task_source_datasource_id")
    @Schema(description = "源数据源 ID", example = "20")
    private Long syncTaskSourceDatasourceId;

    /**
     * 源读取 SQL，必须是 SELECT
     */
    @TableField("ms_sync_task_source_sql")
    @Schema(description = "源读取 SQL，必须是 SELECT")
    private String syncTaskSourceSql;

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
}