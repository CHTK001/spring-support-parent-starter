package com.chua.starter.project.service.runtime;

public interface ProjectRuntimeCommandStrategy {

    String buildStart(ProjectRuntimeCommandContext context);

    String buildStop(ProjectRuntimeCommandContext context);

    default String buildRestart(ProjectRuntimeCommandContext context) {
        return buildStop(context) + "; sleep 2; " + buildStart(context);
    }
}
