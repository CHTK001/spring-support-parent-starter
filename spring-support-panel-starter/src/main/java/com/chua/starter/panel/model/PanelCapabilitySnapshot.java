package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 面板能力快照。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "面板能力快照")
public class PanelCapabilitySnapshot {

    /** 是否启用 JDBC 能力。 */
    @Schema(description = "是否启用 JDBC 能力")
    private boolean jdbcEnabled;
    /** 是否启用文档能力。 */
    @Schema(description = "是否启用文档能力")
    private boolean documentEnabled;
    /** 是否启用 AI 能力。 */
    @Schema(description = "是否启用 AI 能力")
    private boolean aiEnabled;
    /** 是否已启用 AI Starter。 */
    @Schema(description = "是否已启用 AI Starter")
    private boolean aiStarterEnabled;
    /** 提示信息。 */
    @Schema(description = "提示信息")
    private String message;
}
