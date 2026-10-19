package com.chua.starter.monitor.support.server.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * 服务管理 —— 服务器节点视图（实体 + 运行时状态）。
 *
 * @param id          主键
 * @param name        节点名称
 * @param protocol    协议编码: HTTP / HTTP_代理 / TCP_代理 / SIP
 * @param protocolDesc 协议描述
 * @param engine      传输引擎
 * @param host        监听地址
 * @param port        监听端口
 * @param enabled     是否启用: 1 启用，0 停用
 * @param remark      备注
 * @param config      协议专属配置(JSON Map)
 * @param filters     ServerFilter 插件配置列表
 * @param status      运行状态: RUNNING / STOPPED / ERROR
 * @param error       最近一次启动错误信息
 * @param serverUrl   访问地址（运行中才有值）
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "服务管理-服务器节点视图")
public record ServerNodeView(
        @Schema(description = "主键") Long id,
        @Schema(description = "节点名称") String name,
        @Schema(description = "协议编码: HTTP / HTTP_PROXY / TCP_PROXY / SIP") String protocol,
        @Schema(description = "协议描述") String protocolDesc,
        @Schema(description = "传输引擎") String engine,
        @Schema(description = "监听地址") String host,
        @Schema(description = "监听端口") Integer port,
        @Schema(description = "是否启用: 1 启用，0 停用") Integer enabled,
        @Schema(description = "备注") String remark,
        @Schema(description = "协议专属配置(JSON Map)") Map<String, Object> config,
        @Schema(description = "ServerFilter 插件配置列表") List<ServerFilterDescriptor> filters,
        @Schema(description = "运行状态: RUNNING / STOPPED / ERROR") String status,
        @Schema(description = "最近一次启动错误信息") String error,
        @Schema(description = "访问地址（运行中才有值）", example = "http://127.0.0.1:18080/") String serverUrl) {
}
