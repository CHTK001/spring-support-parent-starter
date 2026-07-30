package com.chua.starter.panel.software.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.software.model.ProjectContainerSoftwareOperationTicket;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareOperationRequest;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareTopologyOperationRequest;

import java.util.Map;

public interface SysProjectContainerSoftwareOperationService {
    ReturnResult<ProjectContainerSoftwareOperationTicket> submit(ProjectContainerSoftwareOperationRequest request);

    ReturnResult<ProjectContainerSoftwareOperationTicket> submitTopology(ProjectContainerSoftwareTopologyOperationRequest request);

    ReturnResult<Map<String, Object>> aiAnalyzeFailure(Map<String, Object> request);
}
