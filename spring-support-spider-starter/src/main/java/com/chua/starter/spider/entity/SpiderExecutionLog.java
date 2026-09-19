package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 执行日志实体。
 *
 * <p>对应 {@code spider_execution_log} 表，记录每次执行中每个节点的输入输出、
 * 状态与耗时，以 执行_no（追踪标识）串联一次执行的完整链路。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spider_execution_log")
@Schema(name = "执行日志")
public class SpiderExecutionLog extends SysBase {

    /**
     * 主键 标识（自增）
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键 ID")
    /** 标识 */
    private Long id;

    /**
     * 执行号（追踪标识）
     */
    @TableField("execution_no")
    @Schema(description = "执行号")
    /** 执行no */
    private String executionNo;

    /**
     * 爬虫编码
     */
    @TableField("definition_code")
    @Schema(description = "爬虫编码")
    /** Definition代码 */
    private String definitionCode;

    /**
     * 流程 标识
     */
    @TableField("flow_id")
    @Schema(description = "流程 ID")
    /** 流标识 */
    private Long flowId;

    /**
     * 节点 标识
     */
    @TableField("node_id")
    @Schema(description = "节点 ID")
    /** 节点标识 */
    private String nodeId;

    /**
     * 节点名称
     */
    @TableField("node_name")
    @Schema(description = "节点名称")
    /** 节点名称 */
    private String nodeName;

    /**
     * 节点类型
     */
    @TableField("node_type")
    @Schema(description = "节点类型")
    /** 节点类型 */
    private String nodeType;

    /**
     * 状态（RUNNING/成功/失败）
     */
    @TableField("status")
    @Schema(description = "状态")
    /** 状态 */
    private String status;

    /**
     * 节点输入数据（JSON）
     */
    @TableField("input_data")
    @Schema(description = "节点输入数据")
    /** 输入数据 */
    private String inputData;

    /**
     * 节点输出数据（JSON）
     */
    @TableField("output_data")
    @Schema(description = "节点输出数据")
    /** 输出数据 */
    private String outputData;

    /**
     * 失败信息
     */
    @TableField("error_message")
    @Schema(description = "失败信息")
    /** 错误消息 */
    private String errorMessage;

    /**
     * 节点开始时间
     */
    @TableField("start_at")
    @Schema(description = "节点开始时间")
    /** 开始AT */
    private LocalDateTime startAt;

    /**
     * 节点结束时间
     */
    @TableField("end_at")
    @Schema(description = "节点结束时间")
    /** 结束AT */
    private LocalDateTime endAt;

    /**
     * 逻辑删除标记（0 未删除 / 1 已删除）
     */
    @TableField("sys_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    /** SYS删除标记 */
    private Integer sysDeleted;
}
