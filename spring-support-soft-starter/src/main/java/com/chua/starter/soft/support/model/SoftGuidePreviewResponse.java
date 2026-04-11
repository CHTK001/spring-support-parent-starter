package com.chua.starter.soft.support.model;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftGuidePreviewResponse {
    private Map<String, Object> resolvedVariables;
    private Map<String, String> renderedScripts;
    private List<SoftRenderedConfigFile> renderedConfigFiles;
    private List<String> logPaths;
    private List<String> configPaths;
    private String templateSummaryJson;
}
