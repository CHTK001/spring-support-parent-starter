package com.chua.starter.panel.software.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "项目容器软件导入预设请求")
public class ProjectContainerSoftwareImportPresetRequest {
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer sysProjectId;
    @Schema(description = "预设软件ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer sysProjectContainerSoftwarePresetId;
    @Schema(description = "关联备注")
    private String bindingRemark;
    @Schema(description = "服务名称")
    private String serviceName;
    @Schema(description = "暴露端口")
    private String exposePorts;
}
