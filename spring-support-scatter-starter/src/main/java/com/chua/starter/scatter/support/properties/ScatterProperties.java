package com.chua.starter.scatter.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * scatter 组网配置项（前缀 {@code scatter.}）。
 *
 * <p>所有一等配置项均可覆盖，未配置时回落到框架缺省值：</p>
 * <ul>
 *   <li>{@code scatter.enabled} — 总开关，缺省 {@code false}；未开启时本 starter 零装配、零副作用</li>
 *   <li>{@code scatter.node-id} — 节点唯一标识，缺省 {@code {spring.application.name}@{ip}:{port}}</li>
 *   <li>{@code scatter.service-path} — 服务分组（发现路径），缺省 {@code /{spring.application.name}}</li>
 *   <li>{@code scatter.group} — 组网范围（scatterId），同组节点互相同步，缺省 {@code default}</li>
 *   <li>{@code scatter.context-path} — 业务上下文路径，缺省取 {@code server.servlet.context-path}</li>
 *   <li>{@code scatter.protocol} — 组网协议 {@code tcp} / {@code udp}，缺省 {@code tcp}</li>
 *   <li>{@code scatter.port} — 组网监听端口，缺省自动挑选空闲端口（自动发现要求端口固定，生产建议显式配置）</li>
 *   <li>{@code scatter.seeds} — 种子地址列表 {@code host:port}</li>
 *   <li>{@code scatter.subnet} — 子网过滤（CIDR），仅发现同网段节点</li>
 *   <li>{@code scatter.self-balance} — self-balance Filter 开关，缺省 {@code true}</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = "scatter")
public class ScatterProperties {

    /**
     * 总开关：{@code true} 才装配 scatter 组网与 self-balance Filter；缺省 {@code false}，未配置零副作用。
     */
    private boolean enabled = false;

    /**
     * 组网协议：{@code tcp} / {@code udp}，缺省 {@code tcp}。
     */
    private String protocol = "tcp";

    /**
     * 组网监听端口；&le;0 时自动挑选空闲端口。自动发现要求各节点端口固定，生产环境建议显式配置。
     */
    private int port = 0;

    /**
     * 组网绑定地址；缺省取工程探测到的本机地址。
     */
    private String host;

    /**
     * 对外通告地址；缺省与 {@code host} 一致。
     */
    private String announceHost;

    /**
     * 节点唯一标识；缺省 {@code {spring.application.name}@{ip}:{port}}。
     */
    private String nodeId;

    /**
     * 服务分组（发现路径）；缺省 {@code /{spring.application.name}}。
     */
    private String servicePath;

    /**
     * 组网范围（scatterId）；同组节点互相同步成员与负载，缺省 {@code default}。
     */
    private String group = "default";

    /**
     * 业务上下文路径；缺省取 {@code server.servlet.context-path}。登记进业务端点 metadata，便于对端拼转发地址。
     */
    private String contextPath;

    /**
     * 种子地址列表（{@code host:port}）；seed 模式下用于首次入网。
     */
    private List<String> seeds = new ArrayList<>();

    /**
     * 子网过滤（CIDR），仅发现同网段节点；缺省不过滤。
     */
    private String subnet;

    /**
     * 是否开启自动发现（组播 / gossip），缺省 {@code true}。
     */
    private boolean autoDiscovery = true;

    /**
     * 自动发现周期（毫秒）；&le;0 用 utils 层缺省值。
     */
    private long autoDiscoveryInterval = 0;

    /**
     * 心跳周期（毫秒）；&le;0 用 utils 层缺省值。
     */
    private long heartbeatInterval = 0;

    /**
     * 心跳连续失败多少次后剔除节点；&le;0 用 utils 层缺省值。
     */
    private int failRemoveCount = 0;

    /**
     * 网络 / 同步超时（毫秒）；&le;0 用 utils 层缺省值。
     */
    private long timeoutMillis = 0;

    /**
     * 是否持久化成员表；Spring 宿主重启即无状态，缺省 {@code false}（utils 层原始缺省为 true，这里按宿主形态改缺省）。
     */
    private boolean persistenceEnabled = false;

    /**
     * 成员表持久化文件路径；仅 {@code persistenceEnabled=true} 时生效。
     */
    private String persistenceFile;

    /**
     * self-balance Filter 开关；缺省 {@code true}。开启后本节点收到请求时按负载均衡选中其它实例则反向代理转发。
     */
    private boolean selfBalance = true;

    /**
     * self-balance 负载均衡策略：{@code weight} / {@code random} / {@code round}，缺省 {@code weight}。
     */
    private String balanceStrategy = "weight";

    /**
     * 转发请求体大小上限（字节），超过则放弃转发、本地放行；&le;0 表示不限制。缺省 10MB。
     */
    private long maxForwardBodyBytes = 10L * 1024 * 1024;

    /**
     * self-balance Filter 注册顺序（值越小越靠前），缺省 {@code 0}。
     */
    private int filterOrder = 0;
}
