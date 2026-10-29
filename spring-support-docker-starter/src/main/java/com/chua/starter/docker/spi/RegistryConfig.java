package com.chua.starter.docker.spi;

import lombok.Data;

/**
 * 镜像库配置（SPI 入参）。
 *
 * <p>对应持久化文件 {@code ~/.monitor/docker/registries.json} 的一条记录。密码只在
 * 服务端内部使用：对外接口只回 {@code hasPassword}，{@code pullRef} / {@code search}
 * 等 SPI 方法会拿到完整凭据用于 Basic Auth 或 {@code docker login}。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Data
public class RegistryConfig {

    /**
     * 库标识（UUID；内置 Docker Hub 固定为 {@code dockerhub}）
     */
    private String id;

    /**
     * 展示名
     */
    private String name;

    /**
     * 库类型，对应 {@link DockerRegistryProvider#type()}：dockerhub / generic-v2
     */
    private String type;

    /**
     * 库地址，如 {@code https://registry.example.com}；dockerhub 类型下表示镜像加速地址
     */
    private String url;

    /**
     * 账号（可空，空则匿名访问）
     */
    private String username;

    /**
     * 密码 / 访问令牌（不对外返回）
     */
    private String password;

    /**
     * 命名空间（可空），用于拼 {@code pullRef} 的 {@code namespace/image}
     */
    private String namespace;

    /**
     * 是否启用（禁用后不参与检索，也不能作为安装来源）
     */
    private Boolean enabled;

    /**
     * 是否内置（内置 Docker Hub 不可删除，只能改镜像加速地址与凭据）
     */
    private Boolean builtin;

    /**
     * 创建时间（ISO-8601）
     */
    private String createdAt;

    /**
     * 更新时间（ISO-8601）
     */
    private String updatedAt;

    /**
     * 是否启用，null 视为启用
     *
     * @return 是否启用
     */
    public boolean enabledOrDefault() {
        return !Boolean.FALSE.equals(enabled);
    }
}
