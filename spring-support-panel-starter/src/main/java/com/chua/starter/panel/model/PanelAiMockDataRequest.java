package com.chua.starter.panel.model;

import lombok.Data;

/**
 * AI 示例数据生成请求。
 */
@Data
public class PanelAiMockDataRequest {

    private String panelCatalogName;
    private String panelSchemaName;
    private String panelTableName;
    private Integer panelCount;
}
