package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 面板连接描述。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "面板连接描述")
public class PanelConnectionDescriptor {

    /** 连接标识。 */
    @Schema(description = "连接标识")
    private String connectionId;
    /** 连接名称。 */
    @Schema(description = "连接名称")
    private String connectionName;
    /** 连接类型。 */
    @Schema(description = "连接类型")
    private PanelConnectionType connectionType;
    /** 是否已缓存。 */
    @Schema(description = "是否已缓存")
    private boolean cached;
    /** 是否启用。 */
    @Schema(description = "是否启用")
    private boolean enabled;
    /** 最近访问时间。 */
    @Schema(description = "最近访问时间")
    private LocalDateTime lastAccessTime;
}
