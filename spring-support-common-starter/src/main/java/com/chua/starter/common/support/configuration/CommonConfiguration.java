/**
 * 通用配置
 *
 * @author CH
 * @since 2023-07-01
 */
@EnableConfigurationProperties({
        ActuatorProperties.class,
        IpProperties.class,
        NonceSignProperties.class,
})
/**
 * public class CommonConfiguration
 *
 * @author CH
 * @since 4.0.0.42
 */
@ComponentScan("com.chua.starter.common.support.service")
public class CommonConfiguration