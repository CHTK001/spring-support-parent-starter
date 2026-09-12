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
    private Long id; // 标识

    /** 节点名称 */
    @Schema(description = "节点名称")
    private String name; // 名称

    /** 协议编码: HTTP / HTTP_代理 / TCP_代理 / SIP */
    @Schema(description = "协议编码: HTTP / HTTP_PROXY / TCP_PROXY / SIP")
    private String protocol; // 协议

    /** 协议描述 */
    @Schema(description = "协议描述")
    private String protocolDesc; // 协议desc

    /** 传输引擎 */
    @Schema(description = "传输引擎")
    private String engine; // engine

    /** 监听地址 */
    @Schema(description = "监听地址")
    private String host; // 主机

    /** 监听端口 */
    @Schema(description = "监听端口")
    private Integer port; // 端口

    /** 是否启用: 1 启用（随应用自动拉起） */
    @Schema(description = "是否启用: 1 启用，0 停用")
    private Integer enabled; // 已启用

    /** 备注 */
    @Schema(description = "备注")
    private String remark; // 评论

    /** 协议专属配置 */
    @Schema(description = "协议专属配置(JSON Map)")
    private Map<String, Object> config; // 配置

    /** 服务端过滤器 插件配置列表 */
    @Schema(description = "ServerFilter 插件配置列表")
    private java.util.List<ServerFilterDescriptor> filters; // 过滤器

    /** 运行状态: RUNNING / STOPPED / 错误 */
    @Schema(description = "运行状态: RUNNING / STOPPED / ERROR")
    private String status; // 状态

    /** 最近一次启动错误信息 */
    @Schema(description = "最近一次启动错误信息")
    private String error; // 错误

    /** 访问地址（运行中才有值） */
    @Schema(description = "访问地址（运行中才有值）", example = "http://127.0.0.1:18080/")
    private String serverUrl; // 服务端url
}
