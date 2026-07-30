package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 面板表修改结果。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "面板表修改结果")
public class PanelTableMutationView {

    /** 影响行数。 */
    @Schema(description = "影响行数")
    private long panelAffectedRows;
    /** 执行耗时（毫秒）。 */
    @Schema(description = "执行耗时（毫秒）")
    private long panelElapsedMillis;
    /** 结果消息。 */
    @Schema(description = "结果消息")
    private String panelMessage;
}
