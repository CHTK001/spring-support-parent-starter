package com.chua.starter.docker.spi;

import java.util.List;
import java.util.Map;

/**
 * 镜像库 SPI —— 不同镜像库（Docker Hub / 通用 Registry v2 / 私有制品库）的实现契约。
 *
 * <p>由 Spring 注入 {@code List<DockerRegistryProvider>}，{@code RegistryService} 按
 * {@link #type()} 选择实现；找不到实现时直接报错，不做静默降级。新增镜像库类型只需
 * 实现本接口并声明为 Spring Bean，不需要改动服务与控制器。</p>
 *
 * <p>检索结果必须包含统一的 {@code pullRef}：安装 / 拉取镜像时以后端拼出的
 * {@code pullRef} 作为 {@code docker pull|run} 的目标，避免前端各自拼 host 与命名空间。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
public interface DockerRegistryProvider {

    /**
     * 库类型标识（持久化在 {@link RegistryConfig#getType()}）
     *
     * @return 唯一类型标识，如 dockerhub / generic-v2
     */
    String type();

    /**
     * 类型展示名（前端「镜像库类型」下拉项）
     *
     * @return 展示名
     */
    String displayName();

    /**
     * 在线检索镜像。
     *
     * @param cfg     镜像库配置（含凭据）
     * @param keyword 关键词，可空
     * @param limit   返回条数上限
     * @return 统一结构：{@code name / description / stars / official / pullRef}
     */
    List<Map<String, Object>> search(RegistryConfig cfg, String keyword, int limit);

    /**
     * 查询镜像的标签列表。
     *
     * @param cfg   镜像库配置（含凭据）
     * @param image 镜像名（不含 registry host）
     * @return 标签列表，查询失败返回空列表
     */
    List<String> tags(RegistryConfig cfg, String image);

    /**
     * 拼出可直接用于 {@code docker pull|run} 的完整镜像引用。
     *
     * @param cfg   镜像库配置
     * @param image 镜像名（不含 registry host）
     * @return pullRef
     */
    String pullRef(RegistryConfig cfg, String image);

    /**
     * 连通性 / 凭据测试。
     *
     * @param cfg 镜像库配置（含凭据）
     * @return 是否可用
     */
    boolean test(RegistryConfig cfg);
}
