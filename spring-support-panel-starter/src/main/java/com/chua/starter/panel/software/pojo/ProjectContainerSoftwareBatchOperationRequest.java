package com.chua.starter.panel.software.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "项目容器软件批量操作请求 - 支持在多台服务器上执行相同操作")
public class ProjectContainerSoftwareBatchOperationRequest {
    @Schema(description = "项目ID")
    private Integer sysProjectId;
    @Schema(description = "预设软件ID（INSTALL 时需要）")
    private Integer sysProjectContainerSoftwarePresetId;
    @Schema(description = "操作类型: INSTALL/START/STOP/RESTART/UNINSTALL/STATUS")
    private String action;
    @Schema(description = "目标服务器列表")
    private List<ProjectContainerSoftwareBatchTarget> targets;
}
