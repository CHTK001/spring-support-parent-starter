package com.chua.starter.software.support.pojo.source;

import com.chua.starter.software.support.entity.SoftSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 软件源响应
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@Schema(description = "软件源响应")
public class SoftwareSourceDTO extends SoftSource {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 最后同步时间文本
     */
    @Schema(description = "最后同步时间文本")
    /**
     * 最后同步时间文本
    */
    private String lastSyncTimeText;

    /**
     * 同步状态文本
     */
    @Schema(description = "同步状态文本")
    /**
     * 同步状态文本
    */
    private String syncStatusText;

    /**
     * 源类型文本
     */
    @Schema(description = "源类型文本")
    /**
     * 来源类型文本
    */
    private String sourceTypeText;
}
