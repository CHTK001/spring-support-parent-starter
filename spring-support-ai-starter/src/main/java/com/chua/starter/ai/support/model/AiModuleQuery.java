package com.chua.starter.ai.support.model;

import lombok.Data;

@Data
public class AiModuleQuery {

    private Integer current;
    private Integer size;
    private String sysAiModuleType;
    private String sysAiModuleVendor;
    private String keyword;
}
