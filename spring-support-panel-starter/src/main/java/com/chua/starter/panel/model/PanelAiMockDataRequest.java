package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 示例数据生成请求。
 *
 * @author CH
 */
@Data
@Schema(description = "AI 示例数据生成请求")
public class PanelAiMockDataRequest {

    /** catalog 名称。 */
    @Schema(description = "catalog 名称")
    private String panelCatalogName;
    /** schema 名称。 */
    @Schema(description = "schema 名称")
    private String panelSchemaName;
    /** 表名。 */
    @Schema(description = "表名")
    private String panelTableName;
    /** 生成条数。 */
    @Schema(description = "生成条数")
    private Integer panelCount;
}
