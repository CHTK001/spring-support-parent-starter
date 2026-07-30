package com.chua.starter.panel.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面板连接句柄。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "面板连接句柄")
public class PanelConnectionHandle {

    /** 连接标识。 */
    @Schema(description = "连接标识")
    private String connectionId;
    /** 连接定义。 */
    @Schema(description = "连接定义")
    private PanelConnectionDefinition definition;
    /** 原生连接对象。 */
    @JsonIgnore
    @Schema(description = "原生连接对象")
    private Object nativeConnection;
    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
    /** 最近访问时间。 */
    @Schema(description = "最近访问时间")
    private LocalDateTime lastAccessTime;
    /** 过期时间。 */
    @Schema(description = "过期时间")
    private LocalDateTime expireTime;
}
