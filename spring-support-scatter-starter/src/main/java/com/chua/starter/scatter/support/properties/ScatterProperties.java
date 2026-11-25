package com.chua.starter.scatter.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * scatter 组网配置项（前缀 {@code scatter.}）。
 *
 * <p>所有一等配置项均可覆盖，未配置时回落到框架缺省值。完整 YAML 示例：</p>
 *
 * <pre>{@code
 * spring:
 *   application:
 *     name: demo-service
 * server:
 *   port: 8080                    # 业务端口：self-balance 转发的目标端口，不是组网端口
 * scatter:
 *   enabled: true                 # 总开关，缺省 false（不配置 = 零装配、零副作用）
 *   protocol: tcp                 # 组网协议：tcp / udp，缺省 tcp
 *   port: 9700                    # 组网监听端口；自动发现要求各节点固定且一致
 *   group: default                # 组网范围（scatterId），同组节点互相同步
 *   service-path: /demo-service   # 服务分组（发现路径），缺省 /{spring.application.name}
 *   seeds:                        # 种子地址（指向对端组网端口）；非空即 seed 引导模式
 *     - 10.0.0.10:9700
 *     - 10.0.0.11:9700
 *   # subnet: 192.168.50.0/24     # tcp 自动发现的扫描网段；不配默认本机同网段
 *   self-balance: true            # self-balance 反向代理 Filter，缺省 true
 *   balance-strategy: weight      # weight / random / round
 * }</pre>
 *
 * <p>发现模式按以下优先级自动选择（见 utils {@code ScatterBuilder#buildDiscovery()}）：</p>
 * <ol>
 *   <li>{@code seeds} 非空 → <b>seed 引导模式</b>：只与 seed 同步成员表、seed 扩散、最小
 *       nodeId 选举，不扫网段（跨网段 / 跨机房首选）；</li>
 *   <li>配置了 DNS zone → DNS 服务发现（本 starter 未暴露 DNS 配置项，需要时直接用 utils
 *       {@code ScatterBuilder} API）；</li>
 *   <li>{@code auto-discovery=true} 且 {@code protocol=udp} → <b>多播 + 广播宣告</b>：每轮向
 *       255.255.255.255 与多播组 239.255.0.1 宣告成员表，不逐台探测；</li>
 *   <li>{@code auto-discovery=true} 且 {@code protocol=tcp}（默认）→ <b>网段 gossip 扫描</b>：
 *       对 {@code subnet}（缺省本机同网段，如 192.168.1.0/24）内所有地址的同一
 *       {@code scatter.port} 做 gossip 同步试探，连不上的地址超时跳过；</li>
 *   <li>{@code auto-discovery=false} 且无 {@code seeds} → 独立节点，不发现对端。</li>
 * </ol>
 *
 * <p>注意：自动发现要求各节点使用<b>固定且一致</b>的组网端口；{@code scatter.port} 不配置时
 * 本 starter 启动期随机预占一个空闲端口，各实例端口对不上会导致互相发现不了。生产环境务必显式配置。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = "scatter")
public class ScatterProperties {

    /**
     * 总开关：{@code true} 才装配 scatter 组网与 self-balance Filter。
     *
     * <p>缺省 {@code false}：未配置时本 starter 零装配、零副作用——不监听组网端口、
     * 不注册任何 Bean、不建组网连接。</p>
     *
     * <p>示例：{@code scatter.enabled=true}</p>
     */
    private boolean enabled = false;

    /**
     * 组网协议：{@code tcp} / {@code udp}，缺省 {@code tcp}。
     *
     * <ul>
     *   <li>{@code tcp}：帧协议承载（4 字节长度头 + ScatterFrame，短连接一请求一响应，
     *       单帧上限 16MB），可靠、有序，生产建议；</li>
     *   <li>{@code udp}：数据报承载（无连接、不保证送达）；自动发现走多播 + 广播宣告，
     *       适合局域网零配置快速组网，但跨网段 / 丢包环境下成员同步不稳定。</li>
     * </ul>
     *
     * <p>示例：{@code scatter.protocol=udp}</p>
     */
    private String protocol = "tcp";

    /**
     * 组网监听端口（控制面端口，只承载 scatter 帧，无 HTTP）；&le;0 时本 starter 启动期
     * 自动预占一个空闲端口。
     *
     * <p>自动发现要求各节点端口<b>固定且一致</b>：tcp 模式按该端口扫网段，udp 模式向该端口
     * 宣告；不配置时每个实例随机取端口，节点之间互相发现不了。生产环境建议显式配置，
     * 且 {@code seeds} 指向的也是这个端口（不是 {@code server.port}）。</p>
     *
     * <p>示例：{@code scatter.port=9700}</p>
     */
    private int port = 0;

    /**
     * 组网绑定地址；缺省取工程探测到的本机地址（{@code Project.applicationHost}），
     * 仍取不到时回落 {@code 127.0.0.1}。
     *
     * <p>多网卡 / 容器环境建议显式指定为本机对内可达的 IPv4（组网服务端绑定该地址）；
     * 绑全部网卡用 {@code 0.0.0.0}。</p>
     *
     * <p>示例：{@code scatter.host=10.0.0.10}</p>
     */
    private String host;

    /**
     * 对外通告地址；缺省与 {@code host} 一致。
     *
     * <p>绑定地址与「对端实际能连到的地址」不同时配置（NAT、端口映射、容器 published 地址、
     * 多网卡选外部网卡）。该地址写入成员表，供对端回连组网端口与拼转发地址。</p>
     *
     * <p>示例：本机绑定 {@code 0.0.0.0}、对外是 {@code 10.0.0.10} 时：
     * {@code scatter.host=0.0.0.0}、{@code scatter.announce-host=10.0.0.10}。</p>
     */
    private String announceHost;

    /**
     * 节点唯一标识；缺省 {@code {spring.application.name}@{announceHost}:{port}}。
     *
     * <p>组网 self 条目的 {@code serverId} 与之同值；业务端点条目的 {@code serverId} 为
     * {@code nodeId + ":http"}。集群内必须唯一，重名节点的成员表会互相覆盖。</p>
     *
     * <p>示例：{@code scatter.node-id=demo-service@10.0.0.10:9700}</p>
     */
    private String nodeId;

    /**
     * 服务分组（发现路径）；缺省 {@code /{spring.application.name}}，自动补前导 {@code /}。
     *
     * <p>同 {@code servicePath} + 同 {@code group} 的节点互为同一个服务池；self-balance
     * Filter 只从该路径的成员里选转发目标。不同服务用不同 path 隔离。</p>
     *
     * <p>示例：{@code scatter.service-path=/demo-service}</p>
     */
    private String servicePath;

    /**
     * 组网范围（scatterId）；同组节点互相同步成员与负载，缺省 {@code default}。
     *
     * <p>不同 group 的节点即使端口互通也不会合并成员表，可用于同一批主机上划分多套隔离集群。</p>
     *
     * <p>示例：{@code scatter.group=order-cluster}</p>
     */
    private String group = "default";

    /**
     * 业务上下文路径；缺省取 {@code server.servlet.context-path}。
     *
     * <p>登记进业务端点 metadata（{@code context-path} 键），供对端拼转发地址；
     * 两节点 context-path 不一致会导致转发 404，此时用本项显式统一。</p>
     *
     * <p>示例：{@code scatter.context-path=/api}</p>
     */
    private String contextPath;

    /**
     * 种子地址列表（{@code host:port}，指向对端<b>组网端口</b>）；非空即进入 seed 引导模式
     * （优先于自动发现）。
     *
     * <p>seed 模式下节点只与 seed 同步成员表、经 seed 扩散、参与最小 nodeId 选举，不扫网段，
     * 适合跨网段 / 跨机房。首个引导节点 {@code seeds} 留空即可。</p>
     *
     * <p>示例（B 节点指向 A 的组网端口入网）：</p>
     * <pre>{@code
     * scatter:
     *   seeds:
     *     - 10.0.0.10:9700
     * }</pre>
     */
    private List<String> seeds = new ArrayList<>();

    /**
     * 子网过滤（CIDR），仅 tcp 自动发现使用；缺省取本机非回环 IPv4 所在网段
     * （如 {@code 192.168.1.0/24}，推断失败则告警并跳过扫描）。
     *
     * <p>网段内所有地址都会被按 {@code scatter.port} 构造节点做 gossip 试探，连不上的地址
     * 每轮超时跳过；网段很大时建议显式收窄，减少无效试探。udp 模式不使用本项（走多播/广播）。</p>
     *
     * <p>示例：{@code scatter.subnet=192.168.50.0/24}</p>
     */
    private String subnet;

    /**
     * 是否开启自动发现，缺省 {@code true}。与 {@code seeds} 二选一，{@code seeds} 非空时优先
     * seed 模式。
     *
     * <ul>
     *   <li>{@code true} + {@code protocol=tcp}：网段 gossip 扫描（见 {@code subnet}）；</li>
     *   <li>{@code true} + {@code protocol=udp}：多播 + 广播宣告；</li>
     *   <li>{@code false} 且无 {@code seeds}：退化为独立节点，不发现对端（仅登记自身）。</li>
     * </ul>
     *
     * <p>示例：固定用 seed 引导时 {@code scatter.auto-discovery=false} + {@code scatter.seeds=...}。</p>
     */
    private boolean autoDiscovery = true;

    /**
     * 自动发现周期（毫秒）；&le;0 用 utils 层缺省值 {@code 30000}（30 秒一轮）。
     *
     * <p>tcp gossip 模式入网前 3 轮全量探测，之后每 3 轮做一次全量、其余轮次随机选
     * {@code gossipTargetCount}（缺省 4）个节点交换成员表。</p>
     *
     * <p>示例：{@code scatter.auto-discovery-interval=15000}</p>
     */
    private long autoDiscoveryInterval = 0;

    /**
     * 心跳周期（毫秒）；&le;0 用 utils 层缺省值 {@code 30000}。
     *
     * <p>心跳连续失败 {@code fail-remove-count} 次后，对端把本节点从成员表剔除。</p>
     *
     * <p>示例：{@code scatter.heartbeat-interval=10000}</p>
     */
    private long heartbeatInterval = 0;

    /**
     * 心跳连续失败多少次后剔除节点；&le;0 用 utils 层缺省值 {@code 3}。
     *
     * <p>调小可加快摘除死节点，但网络抖动时容易误剔；调大更稳但摘除更慢。</p>
     *
     * <p>示例：{@code scatter.fail-remove-count=5}</p>
     */
    private int failRemoveCount = 0;

    /**
     * 网络 / 同步超时（毫秒）；&le;0 用 utils 层缺省值 {@code 2000}。
     *
     * <p>tcp gossip 试探、udp 宣告等待、seed 同步共用该超时；高延迟链路适当调大。</p>
     *
     * <p>示例：{@code scatter.timeout-millis=5000}</p>
     */
    private long timeoutMillis = 0;

    /**
     * 是否持久化成员表；Spring 宿主缺省 {@code false}（utils 层原始缺省为 {@code true}，
     * 这里按宿主形态改为无状态：重启后靠重新发现 / seed 重建成员表）。
     *
     * <p>节点地址固定、重启后希望立即拿到上次成员表（跳过发现等待）时再开启。</p>
     *
     * <p>示例：{@code scatter.persistence-enabled=true}</p>
     */
    private boolean persistenceEnabled = false;

    /**
     * 成员表持久化文件路径；仅 {@code persistence-enabled=true} 时生效。
     * 未配置时用 utils 层缺省文件名 {@code .scatter-nodes.json}（落在进程工作目录）。
     *
     * <p>示例：{@code scatter.persistence-file=/var/lib/demo/scatter-nodes.json}</p>
     */
    private String persistenceFile;

    /**
     * self-balance Filter 开关；缺省 {@code true}。开启后本节点收到请求时按负载均衡选中
     * 其它实例则反向代理转发，选中自己 / 池内仅自己 / 命中跳过条件时本地放行。
     *
     * <p>只需要组网与成员发现、不要自动转发时设为 {@code false}（不注册 Filter）。</p>
     *
     * <p>示例：{@code scatter.self-balance=false}</p>
     */
    private boolean selfBalance = true;

    /**
     * self-balance 负载均衡策略：{@code weight} / {@code random} / {@code round}，
     * 缺省 {@code weight}；也可指向自定义 {@code LoadBalance} SPI 名。
     *
     * <p>注意：本 starter 登记的业务端点固定 {@code weight=1.0}；{@code weight} 策略语义是
     * 「选中即权重减半」，长程分布近均匀。</p>
     *
     * <p>示例：{@code scatter.balance-strategy=round}</p>
     */
    private String balanceStrategy = "weight";

    /**
     * 转发请求体大小上限（字节），超过则放弃转发、本地放行；&le;0 表示不限制。
     * 缺省 {@code 10485760}（10MB）。
     *
     * <p>大文件上传本就不走转发（multipart 命中跳过条件），该上限兜底其它大请求体；
     * 需要转发更大请求体时调大，同时注意下游容器自身的请求体限制。</p>
     *
     * <p>示例：{@code scatter.max-forward-body-bytes=20971520}（20MB）</p>
     */
    private long maxForwardBodyBytes = 10L * 1024 * 1024;

    /**
     * self-balance Filter 注册顺序（值越小越靠前），缺省 {@code 0}。
     *
     * <p>需要在其它 Filter（如鉴权、编码）之前抢先转发时调成负值；需要在某些 Filter
     * 之后转发时调大。Filter 链与跳过条件见 {@code SelfBalanceFilter}。</p>
     *
     * <p>示例：{@code scatter.filter-order=-100}</p>
     */
    private int filterOrder = 0;
}
