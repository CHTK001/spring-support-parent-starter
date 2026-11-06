package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 采集流程定义。
 *
 * <p>流程图以 JSON DSL 存放在 {@code collectFlowDsl}，本行只保留调度开关；每个流程在 job-starter
 * 的 {@code sys_job} 里有一行自己的 cron，下次触发时间由那边统一维护。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_flow")
@Schema(description = "数据采集-流程定义")
public class CollectFlow extends SysBase {

    /**
     * 主键ID。
     */
    @TableId(value = "collect_flow_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Integer collectFlowId;

    /**
     * 流程名称。
     */
    @Schema(description = "流程名称")
    private String collectFlowName;

    /**
     * 所属分类ID。
     */
    @Schema(description = "所属分类ID")
    private Integer collectFlowCategoryId;

    /**
     * 流程编码，全局唯一。
     */
    @Schema(description = "流程编码")
    private String collectFlowCode;

    /**
     * 流程图 DSL（JSON）。
     */
    @Schema(description = "流程图DSL(JSON)")
    private String collectFlowDsl;

    /**
     * 定时表达式，空表示不定时。
     */
    @Schema(description = "定时表达式")
    private String collectFlowCron;

    /**
     * 调度状态，0 停用 1 启用。
     */
    @Schema(description = "调度状态 0停用 1启用")
    private Integer collectFlowStatus;

    /**
     * 同一流程允许并发运行的任务数。
     */
    @Schema(description = "允许并发任务数")
    private Integer collectFlowConcurrency;

    /**
     * 当前运行中的任务数，跨节点共享的并发计数。
     */
    @Schema(description = "当前运行中的任务数")
    private Integer collectFlowRunning;

    /**
     * 流程内并行线程数。
     */
    @Schema(description = "流程内并行线程数")
    private Integer collectFlowThreadCount;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String collectFlowRemark;

    /**
     * 最近一次开始执行时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "最近执行时间")
    private LocalDateTime collectFlowLastExecTime;

    /**
     * 累计执行次数。
     */
    @Schema(description = "累计执行次数")
    private Integer collectFlowExecCount;

    /**
     * 累计采集条数（每次任务收尾时按本次产出行数累加）。
     *
     * <p>用 BIGINT 而非 INT：单流程累计条数会长期增长，INT 上限 21 亿在日更场景下并非遥不可及。</p>
     */
    @Schema(description = "累计采集条数")
    private Long collectFlowRecordCount;

    /**
     * 逻辑删除标记，0 正常 1 已删除。
     */
    @Schema(description = "逻辑删除 0正常 1已删除")
    private Integer collectFlowIsDeleted;
}
