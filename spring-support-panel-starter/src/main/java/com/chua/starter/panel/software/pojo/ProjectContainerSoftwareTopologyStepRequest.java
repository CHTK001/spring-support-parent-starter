package com.chua.starter.panel.software.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "项目容器软件拓扑步骤请求")
public class ProjectContainerSoftwareTopologyStepRequest {
    @Schema(description = "排序")
    private Integer order;
    @Schema(description = "标签")
    private String label;
    @Schema(description = "项目容器软件关联ID")
    private Integer sysProjectContainerSoftwareBindingId;
    @Schema(description = "节点类型")
    private String nodeKind;
    @Schema(description = "远程项目ID")
    private String remoteProjectId;
    @Schema(description = "是否必需")
    private Boolean required;
    @Schema(description = "失败策略: continue/stop")
    private String failurePolicy;
    @Schema(description = "启动延迟秒数")
    private Integer startDelaySec;
}
