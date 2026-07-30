package com.chua.starter.panel.software.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "项目容器软件拓扑操作请求")
public class ProjectContainerSoftwareTopologyOperationRequest {
    @Schema(description = "项目ID")
    private Integer sysProjectId;
    @Schema(description = "操作类型: START/STOP/RESTART")
    private String action;
    @Schema(description = "拓扑步骤列表")
    private List<ProjectContainerSoftwareTopologyStepRequest> steps;
}
