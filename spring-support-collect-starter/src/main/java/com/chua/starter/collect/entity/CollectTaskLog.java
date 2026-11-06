package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 节点执行日志。
 *
 * <p>一次任务内每个节点每次执行落一条，用于控制台展示执行链与耗时。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_task_log")
@Schema(description = "数据采集-节点执行日志")
public class CollectTaskLog extends SysBase {

    /**
     * 主键ID。
     */
    @TableId(value = "collect_task_log_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long collectTaskLogId;

    /**
     * 任务ID。
     */
    @Schema(description = "任务ID")
    private Long collectTaskLogTaskId;

    /**
     * 流程ID。
     */
    @Schema(description = "流程ID")
    private Integer collectTaskLogFlowId;

    /**
     * 节点ID。
     */
    @Schema(description = "节点ID")
    private String collectTaskLogNodeId;

    /**
     * 节点名称。
     */
    @Schema(description = "节点名称")
    private String collectTaskLogNodeName;

    /**
     * 节点类型。
     */
    @Schema(description = "节点类型")
    private String collectTaskLogNodeShape;

    /**
     * 循环轮次，从 0 开始。
     */
    @Schema(description = "循环轮次")
    private Integer collectTaskLogIteration;

    /**
     * 结果 SUCCESS/FAILURE/SKIPPED。
     */
    @Schema(description = "结果 SUCCESS/FAILURE/SKIPPED")
    private String collectTaskLogStatus;

    /**
     * 耗时毫秒。
     */
    @Schema(description = "耗时毫秒")
    private Long collectTaskLogCost;

    /**
     * 摘要或异常信息。
     */
    @Schema(description = "摘要或异常信息")
    private String collectTaskLogMessage;

    /**
     * 出参预览。
     */
    @Schema(description = "出参预览")
    private String collectTaskLogOutput;
}
