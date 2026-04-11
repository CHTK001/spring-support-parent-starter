package com.chua.starter.soft.support.model;

import com.chua.starter.soft.support.entity.SoftInstallation;
import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftTarget;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SoftExecutionContext {
    private SoftPackage softPackage;
    private SoftPackageVersion version;
    private SoftTarget target;
    private SoftInstallation installation;
    private Map<String, Object> installOptions;
    private Map<String, Object> serviceOptions;
    private Map<String, Object> configOptions;
    private Map<String, Object> resolvedVariables;
    private Map<String, String> renderedScripts;
    private List<SoftRenderedConfigFile> renderedConfigFiles;
}
