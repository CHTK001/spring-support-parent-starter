package com.chua.starter.soft.support.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftGuideTemplate {
    private String templateScope;
    private String templateCode;
    private String templateName;
    private String templatePath;
    private String templateEngine;
    private String templateContent;
    private Integer sortOrder;
    private Map<String, Object> metadata;
}
