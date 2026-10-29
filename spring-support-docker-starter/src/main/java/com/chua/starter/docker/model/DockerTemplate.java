package com.chua.starter.docker.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Docker 容器模板（保存的 {@code docker run} 配置）。
 *
 * <p>模板字段与 {@link com.chua.starter.docker.DockerCreateRequest} 一一对应，
 * 安装容器时可以带 {@code templateId} 让服务端回填未传字段，也可以直接当作表单
 * 的「一键回填」数据源。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Data
@Schema(description = "Docker 容器模板")
public class DockerTemplate {

    /**
     * 模板标识（UUID，新增时为空）
     */
    @Schema(description = "模板标识，新增时为空")
    private String id;

    /**
     * 模板名称
     */
    @Schema(description = "模板名称")
    private String name;

    /**
     * 说明
     */
    @Schema(description = "说明，可空")
    private String description;

    /**
     * 镜像名（含 tag）
     */
    @Schema(description = "镜像名（含 tag）", example = "nginx:alpine")
    private String image;

    /**
     * 镜像库标识（可空）
     */
    @Schema(description = "镜像库标识，可空")
    private String registryId;

    /**
     * 启动命令（可空，空则用镜像默认 CMD）
     */
    @Schema(description = "覆盖镜像 CMD 的启动命令，可空")
    private String command;

    /**
     * 端口映射
     */
    @Schema(description = "端口映射（host:container[/proto]）")
    private List<String> ports;

    /**
     * 环境变量
     */
    @Schema(description = "环境变量（KEY=VALUE）")
    private List<String> env;

    /**
     * 卷映射
     */
    @Schema(description = "卷映射（宿主路径:容器路径）")
    private List<String> volumes;

    /**
     * 网络名
     */
    @Schema(description = "加入的网络名，可空")
    private String network;

    /**
     * 重启策略
     */
    @Schema(description = "重启策略", example = "unless-stopped")
    private String restartPolicy;

    /**
     * 创建前是否拉取镜像
     */
    @Schema(description = "创建前是否拉取镜像")
    private Boolean pull;

    /**
     * 创建时间（ISO-8601）
     */
    @Schema(description = "创建时间")
    private String createdAt;

    /**
     * 更新时间（ISO-8601）
     */
    @Schema(description = "更新时间")
    private String updatedAt;
}
