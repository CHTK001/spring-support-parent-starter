package com.chua.starter.rpc.support.properties;

import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.common.support.network.rpc.RpcProtocolConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.network.rpc.RpcType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * RPC 模块配置，配置前缀 {@code plugin.rpc}（见 {@link #PRE}）。
 *
 * <p>绑定方式：{@code RpcServiceAnnotationBeanPostProcessor} 经
 * {@code @EnableConfigurationProperties} 启用本配置类；两个后置处理器另经
 * {@code Binder} 从环境绑定同一前缀（{@code plugin.rpc}）持有配置。
 * 绑定开启 {@code ignoreInvalidFields} 与 {@code ignoreUnknownFields}，非法或未知键不阻断启动。</p>
 *
 * <p>总开关 {@code plugin.rpc.enable} 默认 {@code false}：为 {@code false} 时仅
 * {@code rpcServiceInfoContributor} 照常登记服务信息标志，扫描、导出、引用、代理全部关闭。</p>
 *
 * <p>完整配置示例（各项含义与默认值见对应字段说明）：</p>
 *
 * <pre>{@code
 * plugin:
 *   rpc:
 *     enable: true                      # 总开关，默认 false，必须显式打开
 *     type: DUBBO                       # 协议类型：DUBBO / SOFA / JSON / SPRINGBEAN / NONE，默认 DUBBO
 *     application-name: order-service   # 应用名，默认 ${spring.application.name:app}
 *     scan:                             # @RpcService 扫描包，缺省扫主类所在包
 *       - com.example.order.api
 *     access-key: your-access-key       # AK，严禁硬编码，建议从配置中心/环境变量注入
 *     secret-key: your-secret-key       # SK，严禁硬编码，建议从配置中心/环境变量注入
 *     service-key: your-service-key     # 服务序列，生产必须覆盖内置默认值
 *     crypto: aes                       # 加密算法，默认 aes
 *     registry:                         # 注册中心列表
 *       - address: zookeeper://localhost:2181
 *         protocol: zookeeper
 *         timeout: 3000
 *     protocols:                        # 服务端协议列表，装配时只取第一个
 *       - name: dubbo
 *         host: 0.0.0.0
 *         port: 20880
 *     consumer:                         # 消费端全局配置，auto 默认 true
 *       timeout: 3000
 *       retries: 2
 * }</pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = RpcProperties.PRE, ignoreInvalidFields = true, ignoreUnknownFields = true)
public class RpcProperties {

    /**
     * 配置前缀。
     */
    public static final String PRE = "plugin.rpc";
    /**
     * 默认序列化协议。
     */
    public static final String DEFAULT_SERIALIZATION = "hessian";

    /**
     * RPC 总开关。
     *
     * <p>默认 {@code false}。两个后置处理器的所有入口都先检查本开关：关闭时仅
     * {@code rpcServiceInfoContributor} 照常登记，扫描、导出、引用、代理全部关闭。</p>
     *
     * <pre>{@code
     * plugin:
     *   rpc:
     *     enable: true   # 默认 false
     * }</pre>
     */
    private boolean enable = false;

    /**
     * {@code @RpcService} 扫描包集合。
     *
     * <p>默认（未配置或为空）：回退为扫描 {@code @SpringBootApplication} 主类所在包
     * （见 {@code RpcServiceAnnotationBeanPostProcessor#afterPropertiesSet}）。支持占位符。</p>
     *
     * <pre>{@code
     * plugin:
     *   rpc:
     *     scan:
     *       - com.example.order.api
     *       - com.example.user.api
     * }</pre>
     */
    private Set<String> scan;

    /**
     * 访问 AK（Access Key），参与派生消费端 {@code consumer.token}。
     *
     * <p>默认空串。{@code consumer.token} 未配置时按 {@code md5(accessKey:secretKey:serviceKey)} 派生。
     * 严禁硬编码，建议从配置中心或环境变量注入。</p>
     *
     * <pre>{@code
     * plugin:
     *   rpc:
     *     access-key: your-access-key
     * }</pre>
     */
    private String accessKey = "";

    /**
     * 访问 SK（Secret Key），参与派生消费端 {@code consumer.token}。
     *
     * <p>默认空串。严禁硬编码，建议从配置中心或环境变量注入。</p>
     *
     * <pre>{@code
     * plugin:
     *   rpc:
     *     secret-key: your-secret-key
     * }</pre>
     */
    private String secretKey = "";

    /**
     * 服务序列，参与派生消费端 {@code consumer.token}。
     *
     * <p>默认 {@code 98bb5008c946bdce065af13070241e10}（内置示例值）。生产环境必须外部覆盖，
     * 避免多应用共用同一派生令牌。</p>
     *
     * <pre>{@code
     * plugin:
     *   rpc:
     *     service-key: your-service-key
     * }</pre>
     */
    private String serviceKey = "98bb5008c946bdce065af13070241e10";

    /**
     * 加密算法。
     *
     * <p>默认 {@code aes}。</p>
     *
     * <pre>{@code
     * plugin:
     *   rpc:
     *     crypto: aes
     * }</pre>
     */
    private String crypto = "aes";

    /**
     * RPC 协议实现类型，按名称匹配协议 SPI（忽略大小写）。
     *
     * <p>默认 {@link RpcType#DUBBO}。可选值：</p>
     * <ul>
     *   <li>{@code DUBBO}：Apache Dubbo 3.x（随本模块传递引入）</li>
     *   <li>{@code SOFA}：SOFA-RPC Bolt（需自行引入 utils-support-rpc-sofa-starter）</li>
     *   <li>{@code JSON}：JSON-RPC 2.0 over HTTP（需自行引入 utils-support-rpc-json-starter，会监听端口）</li>
     *   <li>{@code SPRINGBEAN}：同进程 Bean 直调，无网络传输，天然降级目标</li>
     *   <li>{@code NONE}：未指定的空值占位</li>
     * </ul>
     *
     * <pre>{@code
     * plugin:
     *   rpc:
     *     type: DUBBO
     * }</pre>
     */
    private RpcType type = RpcType.DUBBO;

    /**
     * 服务端协议列表。
     *
     * <p>默认空列表（{@link #getProtocols()} 对 {@code null} 回落为空列表）。装配时只取第一个元素
     * （见 {@code RpcServiceAnnotationBeanPostProcessor#setEnvironment}）。字段集见
     * {@link RpcProtocolConfig}（name/host/port/threads/serialization 等），可用
     * {@code RpcProtocolConfig.auto(name, port)} 按 CPU/堆生成调优配置。</p>
     *
     * <pre>{@code
     * plugin:
     *   rpc:
     *     protocols:
     *       - name: dubbo
     *         host: 0.0.0.0
     *         port: 20880
     *         threads: 200
     *         serialization: hessian2
     * }</pre>
     */
    private List<RpcProtocolConfig> protocols;

    /**
     * 应用名，透传给底层协议实现。
     *
     * <p>默认 {@code ${spring.application.name:app}}：优先取 {@code spring.application.name}，
     * 未配置时回落为 {@code app}；装配时经 {@code environment.resolvePlaceholders} 解析占位符。</p>
     *
     * <pre>{@code
     * plugin:
     *   rpc:
     *     application-name: order-service
     * }</pre>
     */
    private String applicationName = "${spring.application.name:app}";

    /**
     * 消费端全局配置。
     *
     * <p>默认 {@code new RpcConsumerConfig()}（{@code auto=true}）。各项均为包装类型，未配置时为
     * {@code null}，由 {@code autoConfig()} 或底层协议兜底；字段含义见 {@link RpcConsumerConfig}。</p>
     *
     * <pre>{@code
     * plugin:
     *   rpc:
     *     consumer:
     *       auto: true             # 自动调优开关，默认 true
     *       timeout: 3000          # 调用超时（毫秒）
     *       connect-timeout: 1000  # 连接超时（毫秒）
     *       retries: 2             # 重试次数
     *       retry-enabled: true    # 重试开关（仅 spring 协议调用链消费）
     *       retry-delay: 500       # 重试初始间隔（毫秒）
     *       load-balance: random   # random/roundrobin/leastactive/consistenthash/shortestresponse
     *       cluster: failover      # failover/failfast/failsafe/failback/forking/broadcast
     *       connections: 64        # 最大连接数
     *       serialization: hessian
     * }</pre>
     */
    private RpcConsumerConfig consumer = new RpcConsumerConfig();

    /**
     * 注册中心列表。
     *
     * <p>默认空列表（{@link #getRegistry()} 对 {@code null} 回落为空列表）。字段集见
     * {@link RpcRegistryConfig}（address/protocol/username/password/timeout 等），{@code protocol}
     * 可选 {@code zookeeper} / {@code nacos} / {@code redis} / {@code multicast} / {@code direct}。</p>
     *
     * <pre>{@code
     * plugin:
     *   rpc:
     *     registry:
     *       - address: zookeeper://localhost:2181
     *         protocol: zookeeper
     *         timeout: 3000
     *         username: your-username
     *         password: your-password
     * }</pre>
     */
    private List<RpcRegistryConfig> registry;

    /**
     * 获取Registry
     *
     * @return 获取registry的结果
     */
    public List<RpcRegistryConfig> getRegistry() {
        if(null == registry) {
            return Collections.emptyList();
        }
        return registry;
    }

    /**
     * 获取协议
     *
     * @return 获取协议的结果
     */
    public List<RpcProtocolConfig> getProtocols() {
        if(null == protocols) {
            return Collections.emptyList();
        }
        return protocols;
    }

    /**
     * 获取 RPC 类型
     *
     * @return RPC 类型
     */
    public RpcType getType() {
        return type;
    }

    /**
     * 是否启用
     *
     * @return 是否启用
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * 设置是否启用
     *
     * @param enable 是否启用
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 获取消费者配置
     *
     * @return 消费者配置
     */
    public RpcConsumerConfig getConsumer() {
        return consumer;
    }

    /**
     * 设置消费者配置
     *
     * @param consumer 消费者配置
     */
    public void setConsumer(RpcConsumerConfig consumer) {
        this.consumer = consumer;
    }

    /**
     * 获取应用名称
     *
     * @return 应用名称
     */
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * 设置应用名称
     *
     * @param applicationName 应用名称
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * 获取扫描包
     *
     * @return 扫描包
     */
    public Set<String> getScan() {
        return scan;
    }

    /**
     * 设置扫描包
     *
     * @param scan 扫描包
     */
    public void setScan(Set<String> scan) {
        this.scan = scan;
    }
}
