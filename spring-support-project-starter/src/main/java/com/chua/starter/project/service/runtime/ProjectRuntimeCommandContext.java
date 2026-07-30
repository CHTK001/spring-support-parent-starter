package com.chua.starter.project.service.runtime;

import com.chua.starter.project.model.remote.ProjectRemoteProject;

public record ProjectRuntimeCommandContext(
        ProjectRemoteProject project,
        String rootDirectory,
        String logDirectory,
        String startupLogFile,
        String pidFile,
        String statusPidScript,
        String pidScript
) {
}
