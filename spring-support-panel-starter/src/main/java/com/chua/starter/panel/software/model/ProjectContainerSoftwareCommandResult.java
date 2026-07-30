package com.chua.starter.panel.software.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ProjectContainerSoftwareCommandResult {
    private boolean success;
    private int exitCode;
    private String message;
    private String output;

    public ProjectContainerSoftwareCommandResult() {
        this.success = true;
        this.exitCode = 0;
    }
}
