package com.chua.starter.soft.support.model;

import java.util.Map;
import lombok.Data;

@Data
public class SoftGuidePreviewRequest {
    private Integer softPackageVersionId;
    private Integer softTargetId;
    private String installationName;
    private String installPath;
    private String serviceName;
    private Map<String, Object> installOptions;
    private Map<String, Object> serviceOptions;
    private Map<String, Object> configOptions;
}
