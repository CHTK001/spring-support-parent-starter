package com.chua.starter.panel.software.pojo;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "项目容器软件重配置请求")
public class ProjectContainerSoftwareReconfigureRequest {
    @Schema(description = "项目容器软件关联ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("bindingId")
    private Integer sysProjectContainerSoftwareBindingId;
    @Schema(description = "配置字段值键值对", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Object> configValues;
}
