package com.chua.starter.docker;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Docker 容器创建请求（{@code docker run} 的前端表单投影）。
 *
 * <p>字段与 {@code docker run} 的常用参数一一对应，服务端负责拼装成受控的
 * 命令行：不做任意命令透传，避免把「创建容器」变成「以 root 执行任意命令」。
 * 每一项都会先做格式校验（端口映射 {@code host:container}、环境变量
 * {@code KEY=VALUE}、卷映射 {@code src:dst}），校验不过直接报错。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Data
@Schema(description = "Docker 容器创建请求")
public class DockerCreateRequest {

    /**
     * 容器名称（可空，空则由 Docker 随机命名）
     */
    @Schema(description = "容器名称，可空")
    private String name;

    /**
     * 镜像名（含 tag），如 nginx:alpine
     */
    @Schema(description = "镜像名（含 tag）", example = "nginx:alpine")
    private String image;

    /**
     * 启动后执行的命令（可空，空则用镜像默认 CMD）
     */
    @Schema(description = "覆盖镜像 CMD 的启动命令，可空")
    private String command;

    /**
     * 端口映射，形如 {@code 8080:80}、{@code 53:53/udp}
     */
    @Schema(description = "端口映射（host:container[/proto]）")
    private List<String> ports;

    /**
     * 环境变量，形如 {@code TZ=Asia/Shanghai}
     */
    @Schema(description = "环境变量（KEY=VALUE）")
    private List<String> env;

    /**
     * 卷映射，形如 {@code /data/nginx:/usr/share/nginx/html}
     */
    @Schema(description = "卷映射（宿主路径:容器路径）")
    private List<String> volumes;

    /**
     * 网络名（可空，空则用默认 bridge）
     */
    @Schema(description = "加入的网络名，可空")
    private String network;

    /**
     * 重启策略：no / always / unless-stopped / on-failure
     */
    @Schema(description = "重启策略", example = "unless-stopped")
    private String restartPolicy;

    /**
     * 是否在创建前先拉取镜像（本地没有镜像时必须为 true）
     */
    @Schema(description = "创建前是否拉取镜像")
    private Boolean pull;

    /**
     * 镜像库标识（可空，空则直接使用镜像名）
     *
     * <p>非空时服务端按该库的 SPI 实现拼出 {@code pullRef}，并在会话内用该库
     * 凭据执行 {@code docker login}，密码不落盘到命令行历史。</p>
     */
    @Schema(description = "镜像库标识，可空")
    private String registryId;

    /**
     * 模板标识（可空）
     *
     * <p>非空时服务端先用模板填充未传字段，再按最终配置创建容器；请求里显式
     * 传入的字段优先，不会被模板覆盖。</p>
     */
    @Schema(description = "模板标识，可空")
    private String templateId;
}
