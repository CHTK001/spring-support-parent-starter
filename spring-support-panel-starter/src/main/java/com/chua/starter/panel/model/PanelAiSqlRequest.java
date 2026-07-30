package com.chua.starter.panel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * AI 生成 SQL 请求。
 *
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 生成 SQL 请求")
public class PanelAiSqlRequest {

    /** 生成提示词。 */
    @Schema(description = "生成提示词")
    private String prompt;
    /** 涉及的表名列表。 */
    @Schema(description = "涉及的表名列表")
    private List<String> tableNames;
}
