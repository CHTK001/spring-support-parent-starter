package com.chua.starter.collect.vo;

import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 采集平台运行态出参，控制台首屏用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "数据采集-运行态出参")
public class CollectRuntimeVO {

    /**
     * 平台开关。
     */
    @Schema(description = "平台开关")
    private Boolean enable;

    /**
     * 执行器标识，实例名加进程号。
     */
    @Schema(description = "执行器标识")
    private String executorId;

    /**
     * 本机运行中的任务数。
     */
    @Schema(description = "运行中任务数")
    private Integer runningTasks;

    /**
     * 流程总数。
     */
    @Schema(description = "流程总数")
    private Long flowCount;

    /**
     * 启用调度的流程数。
     */
    @Schema(description = "启用调度的流程数")
    private Long enabledFlowCount;

    /**
     * 任务总数。
     */
    @Schema(description = "任务总数")
    private Long taskCount;

    /**
     * 采集结果总数。
     */
    @Schema(description = "结果总数")
    private Long recordCount;

    /**
     * 采集流程在任务中心里的分组名。
     */
    @Schema(description = "调度台账分组")
    private String schedulerGroup;

    /**
     * 调度台账里已登记的采集流程数。
     */
    @Schema(description = "已登记调度任务数")
    private Long scheduledJobCount;

    /**
     * 单任务最长执行毫秒。
     */
    @Schema(description = "单任务超时毫秒")
    private Long taskTimeoutMs;

    /**
     * 单任务最多落库记录数。
     */
    @Schema(description = "单任务记录上限")
    private Integer maxRecordPerTask;

    /**
     * 流程默认并行线程数。
     */
    @Schema(description = "默认并行线程数")
    private Integer defaultThreadCount;

    /**
     * 单次抓取默认超时毫秒。
     */
    @Schema(description = "抓取超时毫秒")
    private Integer requestTimeoutMs;

    /**
     * 已注册的节点类型元数据。
     */
    @Schema(description = "节点类型元数据")
    private List<CollectShapeMeta> shapes = new ArrayList<>();

    /**
     * 内置表达式函数名，取自 {@code CollectFunctions} 的公开静态方法。
     */
    @Schema(description = "内置函数名（表达式 ${} 与脚本 fn. 共用）")
    private List<String> builtinFunctions = new ArrayList<>();
}
