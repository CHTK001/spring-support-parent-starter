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
public class SoftGuideField {
    private String fieldKey;
    private String fieldLabel;
    private String fieldScope;
    private String componentType;
    private String groupName;
    private String fieldDescription;
    private Integer sortOrder;
    private Boolean requiredFlag;
    private Object defaultValue;
    private List<Map<String, Object>> options;
    private Map<String, Object> validation;
    private Map<String, Object> condition;
    private String targetPath;
    private Map<String, Object> metadata;
}
