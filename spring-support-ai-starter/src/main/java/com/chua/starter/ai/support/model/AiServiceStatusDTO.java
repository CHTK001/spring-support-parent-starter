package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 服务运行状态 DTO，字段命名与前端面板对齐。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@Schema(name = "服务运行状态 DTO")
public class AiServiceStatusDTO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 服务名称
     */
    @Schema(description = "服务名称")
    /**
     * 名称
    */
    private String name;

    /**
     * 是否运行中
     */
    @Schema(description = "是否运行中")
    /**
     * Running
    */
    private Boolean running;

    /**
     * 进程 PID
     */
    @Schema(description = "进程 PID")
    /**
     * PID
    */
    private Long pid;

    /**
     * 运行时长(秒)
     */
    @Schema(description = "运行时长(秒)")
    /**
     * Uptime
    */
    private Long uptime;
}
