package com.chua.starter.sync.data.support.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 同步任务查询参数
 *
 * @author CH
 * @since 2024/12/19
 */
@Data
@Schema(description = "同步任务查询参数")
public class SyncTaskQuery {

    /**
     * 页码
     */
    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小", example = "10")
    private Integer size = 10;

    /**
     * 任务名称(模糊查询)
     */
    @Schema(description = "任务名称")
    private String taskName;

    /**
     * 任务状态: STOPPED/RUNNING/ERROR
     */
    @Schema(description = "任务状态: STOPPED/RUNNING/ERROR")
    private String taskStatus;

    /**
     * 排序字段
     */
    @Schema(description = "排序字段", example = "syncTaskCreateTime")
    private String orderBy = "syncTaskCreateTime";

    /**
     * 是否降序
     */
    @Schema(description = "是否降序", example = "true")
    private Boolean desc = true;
}
