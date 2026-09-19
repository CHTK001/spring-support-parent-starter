package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务管理 —— 协议服务器节点主数据。
 *
 * <p>对应表 {@code ms_server_node}。存储基于 common-starter {@code Server} 接口
 * 创建的协议服务器定义（HTTP / HTTP_代理 / TCP_代理 / SIP），运行期实例由
 * {@code ServerNodeRegistry} 按定义拉起与回收。</p>
 *
 * <ul>
 *   <li>{@code protocol}：节点协议类型，见 {@code ServerNodeProtocol}</li>
 *   <li>{@code engine}：传输引擎（http 支持 jdk/nio/aio；代理与 sip 固定为 jdk/sip）</li>
 *   <li>{@code config}：协议专属配置 JSON（sip.token / 代理 backendHost、backendPort / ssl 等）</li>
 *   <li>{@code filters}：ServerFilter 插件配置 JSON 数组，见 {@code ServerFilterDescriptor}</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "服务管理-协议服务器节点")
@TableName("ms_server_node")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsServerNode extends SysBase {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /** 节点名称（唯一） */
    @TableField("ms_server_node_name")
    @Schema(description = "节点名称（唯一）", example = "fileserver-01")
    private String name; // 名称

    /**
     * 协议类型: HTTP / HTTP_代理 / TCP_代理 / SIP
     */
    @TableField("ms_server_node_protocol")
    @Schema(description = "协议类型: HTTP / HTTP_PROXY / TCP_PROXY / SIP", example = "HTTP")
    private String protocol; // 协议

    /**
     * 传输引擎: http= jdk/nio/aio，代理= jdk，sip= sip
     */
    @TableField("ms_server_node_engine")
    @Schema(description = "传输引擎: http= jdk/nio/aio，代理= jdk，sip= sip", example = "jdk")
    private String engine; // engine

    /** 监听地址 */
    @TableField("ms_server_node_host")
    @Schema(description = "监听地址", example = "0.0.0.0")
    private String host; // 主机

    /** 监听端口 */
    @TableField("ms_server_node_port")
    @Schema(description = "监听端口", example = "18080")
    private Integer port; // 端口

    /** 是否启用(1 启用：启动期自动拉起；0 停用) */
    @TableField("ms_server_node_enabled")
    @Schema(description = "是否启用: 1 启用并随应用启动自动拉起，0 停用", example = "1")
    private Integer enabled; // 启用

    /** 备注 */
    @TableField("ms_server_node_remark")
    @Schema(description = "备注")
    private String remark; // 评论

    /**
     * 协议专属配置(JSON 映射)：sip.令牌、代理.backend主机/backend端口 等
     */
    @TableField("ms_server_node_config")
    @Schema(description = "协议专属配置(JSON): sip.token、proxy.backendHost/backendPort、ssl 等")
    private String config; // 配置

    /** 服务端过滤器 插件配置(JSON 数组) */
    @TableField("ms_server_node_filters")
    @Schema(description = "ServerFilter 插件配置(JSON 数组)")
    private String filters; // 过滤器
}
