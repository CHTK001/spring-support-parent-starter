package com.chua.starter.monitor.support.server.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* 服务管理 —— 创建/更新服务器请求体。
*
* <p>配置约定：</p>
* <ul>
*   <li>proxy（HTTP_PROXY/TCP_PROXY）：{@code config.backendHost} + {@code config.backendPort} 必填</li>
*   <li>SIP：{@code config.token} 可选（缺省使用框架默认令牌）</li>
*   <li>HTTP 通用项：{@code config.contextPath}、{@code readTimeout}、{@code gzipEnabled}、{@code reactor} 等</li>
* </ul>
*
* @author CH
* @since 4.0.0.42
 */
@Data
@Schema(description = "服务管理-创建/更新服务器请求")
public class ServerNodeRequest {

    /** 节点名称（唯一） */
    @Schema(description = "节点名称（唯一）", example = "fileserver-01")
    private String name; // 名称

    /** 协议类型: http / http-代理 / tcp-代理 / sip（大小写、-/_ 均可） */
    @Schema(description = "协议类型: http / http-proxy / tcp-proxy / sip", example = "http")
    private String protocol; // 协议

    /** 传输引擎: http= jdk/nio/aio（默认 jdk） */
    @Schema(description = "传输引擎: http= jdk/nio/aio（默认 jdk）", example = "jdk")
    private String engine; // engine

    /** 监听地址 */
    @Schema(description = "监听地址", example = "0.0.0.0")
    private String host; // 主机

    /** 监听端口（缺省按协议默认） */
    @Schema(description = "监听端口（缺省按协议默认）", example = "18080")
    private Integer port; // 端口

    /** 是否启用: 1 启动并随应用拉起，0 停用（默认 1） */
    @Schema(description = "是否启用: 1 启动并随应用拉起，0 停用（默认 1）", example = "1")
    private Integer enabled; // 已启用

    /** 备注 */
    @Schema(description = "备注")
    private String remark; // 评论

    /** 协议专属配置（JSON 映射） */
    @Schema(description = "协议专属配置: proxy 需要 backendHost/backendPort，sip 可选 token")
    private Map<String, Object> config = new LinkedHashMap<>(); // 配置

    /** 服务端过滤器 插件配置列表 */
    @Schema(description = "ServerFilter 插件配置列表")
    private List<ServerFilterDescriptor> filters = new ArrayList<>(); // 过滤器
}
