package com.chua.starter.panel.software.pojo;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "项目容器软件操作请求")
public class ProjectContainerSoftwareOperationRequest {
    @Schema(description = "项目容器软件关联ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("bindingId")
    private Integer sysProjectContainerSoftwareBindingId;
    @Schema(description = "操作类型: INSTALL/START/STOP/RESTART/STATUS/UNINSTALL/PURGE")
    private String action;
    @Schema(description = "操作参数")
    private String params;
    @Schema(description = "SSH 连接ID (为空时使用本地执行)")
    private String connectionId;
}
