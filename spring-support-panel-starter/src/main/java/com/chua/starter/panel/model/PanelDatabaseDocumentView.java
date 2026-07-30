package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 面板数据库文档视图。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "面板数据库文档视图")
public class PanelDatabaseDocumentView {

    /** catalog 名称。 */
    @Schema(description = "catalog 名称")
    private String panelCatalogName;
    /** schema 数量。 */
    @Schema(description = "schema 数量")
    private long panelSchemaCount;
    /** 表数量。 */
    @Schema(description = "表数量")
    private long panelTableCount;
    /** 生成时间。 */
    @Schema(description = "生成时间")
    private String panelGeneratedAt;
    /** 表文档列表。 */
    @Schema(description = "表文档列表")
    private List<PanelDatabaseTableDocumentView> panelTables;
}
