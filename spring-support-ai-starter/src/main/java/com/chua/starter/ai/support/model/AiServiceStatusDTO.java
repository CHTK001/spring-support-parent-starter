package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 服务运行状态 DTO，字段命名与前端面板对齐。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@Schema(name = "服务运行状态 DTO")
public class AiServiceStatusDTO implements Serializable {

    /**
     * 服务名称
     */
    @Schema(description = "服务名称")
    private String name;

    /**
     * 是否运行中
     */
    @Schema(description = "是否运行中")
    private Boolean running;

    /**
     * 进程 PID
     */
    @Schema(description = "进程 PID")
    private Long pid;

    /**
     * 运行时长(秒)
     */
    @Schema(description = "运行时长(秒)")
    private Long uptime;
}
