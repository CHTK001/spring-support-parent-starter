package com.chua.starter.monitor.support.server.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 服务管理 —— 服务端过滤器 插件配置描述。
 *
 * <p>插件加载策略（{@code ServerFilterFactory}）：</p>
 * <ul>
 *   <li>{@code type = "file-storage"}（或 name 同名）：反射创建
 *       {@code com.chua.starter.filesystem.support.server.FileStorageServerFilter}，
 *       {@code params} 逐项映射到 {@code FileStorageSetting}（openPreview/openDownload/openRange/
 * 打开watermark/打开远程文件/打开webjars/watermark），并自动从 Spring 容器
 *       发现所有 {@code FileStorage} Bean 注册为存储桶</li>
 *   <li>其余 {@code type} 视为 ServerFilter 全限定类名（FQCN）：反射实例化
 * （优先无参构造，其次 (服务端setting) 构造），并调用 {@code init(ServerFilterConfig)}
 *       注入 {@code params}</li>
 * </ul>
 *
 * @param name    过滤器唯一名（同一服务器内不重复）
 * @param type   过滤器类型: 预设别名 文件-storage 或 服务端过滤器 全限定类名；缺省取 名称
 * @param order  执行顺序，数值越小越先执行（缺省 100）
 * @param enabled 是否启用（缺省 true）
 * @param params 过滤器初始化参数
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "ServerFilter 插件配置描述")
public record ServerFilterDescriptor(
        @Schema(description = "过滤器唯一名（同一服务器内不重复）", example = "file-storage") String name,
        @Schema(description = "过滤器类型: 预设别名 file-storage 或 ServerFilter 全限定类名；缺省取 name") String type,
        @Schema(description = "执行顺序，数值越小越先执行（缺省 100）", example = "10") Integer order,
        @Schema(description = "是否启用（缺省 true）", example = "true") Boolean enabled,
        @Schema(description = "过滤器初始化参数") Map<String, String> params) {

    /**
     * 解析生效的类型：类型 缺省时取 名称。
     *
     * @return 生效类型
     */
    public String resolveType() {
        if (type != null && !type.isBlank()) {
            return type.trim();
        }
        return name == null ? null : name.trim();
    }
}
