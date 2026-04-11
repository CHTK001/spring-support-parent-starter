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
public class SoftPackageGuide {
    private Integer softPackageId;
    private Integer softPackageVersionId;
    private Integer softPackageProfileId;
    private String profileCode;
    private String profileName;
    private List<SoftGuideField> installFields;
    private List<SoftGuideField> serviceFields;
    private List<SoftGuideField> configFields;
    private List<SoftGuideTemplate> templates;
    private Map<String, Object> versionOverrides;
}
