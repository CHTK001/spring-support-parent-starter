package com.chua.starter.monitor.support.server.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 服务管理 —— 服务器节点视图（实体 + 运行时状态）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "服务管理-服务器节点视图")
public class ServerNodeView {

    /** 主键 */
    @Schema(description = "主键")
    private Long id;

    /** 节点名称 */
    @Schema(description = "节点名称")
    private String name;

    /** 协议编码: HTTP / HTTP_PROXY / TCP_PROXY / SIP */
    @Schema(description = "协议编码: HTTP / HTTP_PROXY / TCP_PROXY / SIP")
    private String protocol;

    /** 协议描述 */
    @Schema(description = "协议描述")
    private String protocolDesc;

    /** 传输引擎 */
    @Schema(description = "传输引擎")
    private String engine;

    /** 监听地址 */
    @Schema(description = "监听地址")
    private String host;

    /** 监听端口 */
    @Schema(description = "监听端口")
    private Integer port;

    /** 是否启用: 1 启用（随应用自动拉起） */
    @Schema(description = "是否启用: 1 启用，0 停用")
    private Integer enabled;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 协议专属配置 */
    @Schema(description = "协议专属配置(JSON Map)")
    private Map<String, Object> config;

    /** ServerFilter 插件配置列表 */
    @Schema(description = "ServerFilter 插件配置列表")
    private java.util.List<ServerFilterDescriptor> filters;

    /** 运行状态: RUNNING / STOPPED / ERROR */
    @Schema(description = "运行状态: RUNNING / STOPPED / ERROR")
    private String status;

    /** 最近一次启动错误信息 */
    @Schema(description = "最近一次启动错误信息")
    private String error;

    /** 访问地址（运行中才有值） */
    @Schema(description = "访问地址（运行中才有值）", example = "http://127.0.0.1:18080/")
    private String serverUrl;
}
