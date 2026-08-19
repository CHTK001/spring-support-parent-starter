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
@ComponentScan("com.chua.starter.common.support.service")
public class CommonConfiguration