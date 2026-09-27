package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Objects;

/**
 * 服务运行状态 DTO，字段命名与前端面板对齐。
 *
 * @param name 服务名称
 * @param running 是否运行中
 * @param pid 进程 PID
 * @param uptime 运行时长(秒)
 *
 * @since 2026-08-03
 * @author CH
 */
@Schema(name = "服务运行状态 DTO")
public record AiServiceStatusDTO(
        @Schema(description = "服务名称") String name,
        @Schema(description = "是否运行中") Boolean running,
        @Schema(description = "进程 PID") Long pid,
        @Schema(description = "运行时长(秒)") Long uptime
) implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 规范构造器：校验必填组件非空。
     *
     * <p>value class 前置条件——规范构造器必须空值敌对；服务名称在库表 中 为 NOT NULL，
     * 因此可安全断言；运行状态、进程 与 时长 为可空 包装类型，保持原有可空契约。</p>
     *
     * @param name    服务名称
     * @param running 是否运行中
     * @param pid     进程 PID
     * @param uptime  运行时长(秒)
     */
    public AiServiceStatusDTO {
        name = Objects.requireNonNull(name, "name 不能为 null");
    }
}
