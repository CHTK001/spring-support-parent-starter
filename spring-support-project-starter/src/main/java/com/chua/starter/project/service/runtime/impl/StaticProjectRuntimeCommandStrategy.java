package com.chua.starter.project.service.runtime.impl;

import com.chua.common.support.annotation.Spi;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandContext;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandStrategy;

@Spi("static")
public class StaticProjectRuntimeCommandStrategy implements ProjectRuntimeCommandStrategy {

    @Override
    public String buildStart(ProjectRuntimeCommandContext context) {
        return "";
    }

    @Override
    public String buildStop(ProjectRuntimeCommandContext context) {
        return "";
    }

    @Override
    public String buildRestart(ProjectRuntimeCommandContext context) {
        return "";
    }
}
