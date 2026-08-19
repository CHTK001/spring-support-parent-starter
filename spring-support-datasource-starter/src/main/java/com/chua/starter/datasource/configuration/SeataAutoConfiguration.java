/**
 * Seata 分布式事务自动配置
 * <p>
 * 当类路径下存在 Seata 的核心类时自动启用。
 * 为所有已注册的动态数据源自动包装 {@code DataSourceProxy}，
 * 以便 Seata AT 模式或 XA 模式能够拦截数据库操作。
 * </p>
 *
 * <p>
 * <b>前置条件：</b>用户需要在 pom.xml 中自行引入 Seata 依赖：
 * <pre>{@code
 * <dependency>
 *     <groupId>org.apache.seata</groupId>
 *     <artifactId>seata-spring-boot-starter</artifactId>
 *     <version>2.6.0</version>
 * </dependency>
 * }</pre>
 * </p>
 *
 * <p>
 * <b>配置：</b>
 * <pre>{@code
 * seata:
 *   enabled: true                    # 默认开启
 *   application-id: my-app
 *   tx-service-group: my_tx_group
 *   enable-auto-data-source-proxy: false  # 我们自行代理，避免冲突
 *   data-source-proxy-mode: AT       # AT 或 XA
 * }</pre>
 * </p>
 *
 * @author CH
 * @since 2026/07/14
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnClass(name = {
        "io.seata.core.context.RootContext",
        "io.seata.rm.datasource.DataSourceProxy"
})
@ConditionalOnProperty(prefix = "seata", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeataAutoConfiguration