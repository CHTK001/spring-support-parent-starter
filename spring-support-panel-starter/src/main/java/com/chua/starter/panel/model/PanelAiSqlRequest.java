package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI 生成 SQL 请求。
 */
@Data
@Builder
public class PanelAiSqlRequest {

    private String prompt;
    private List<String> tableNames;
}
