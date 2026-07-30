package com.chua.starter.panel.software.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "批量操作目标服务器")
public class ProjectContainerSoftwareBatchTarget {
    @Schema(description = "项目容器软件关联ID（导入后自动生成, 为空时从 presetId 导入）")
    private Integer sysProjectContainerSoftwareBindingId;
    @Schema(description = "SSH/Docker 连接ID")
    private String connectionId;
    @Schema(description = "服务器地址（用于导入预设时的连接）")
    private String serverAddress;
    @Schema(description = "自定义服务名称")
    private String serviceName;
    @Schema(description = "关联备注（JSON，含 Docker 配置：镜像/端口/卷/环境变量等）")
    private String bindingRemark;
    @Schema(description = "暴露端口（如 80:80,3306:3306）")
    private String exposePorts;
}
