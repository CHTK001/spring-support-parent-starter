package com.chua.starter.panel.software.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ProjectContainerSoftwareOperationTicket {
    private String operationId;
    private Integer sysProjectId;
    private Integer sysProjectContainerSoftwareBindingId;
    private String action;
    private String operationStatus;
    private int progressPercent;
    private String message;

    public ProjectContainerSoftwareOperationTicket() {
    }
}
