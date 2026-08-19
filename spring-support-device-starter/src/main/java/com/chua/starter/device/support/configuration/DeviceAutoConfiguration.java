/**
 * 设备模块自动配置类
 * <p>负责组件扫描与 Mapper 扫描，并启用定时调度。</p>
 * <p>设备厂商 Provider 由 {@link DeviceProvider} 子类通过 {@code @Component} 自动注册为 Spring Bean，</p>
 * <p>业务方添加新厂商只需新增 Provider 实现即可，SPI 文件不再使用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@AutoConfiguration
@ComponentScan({
        "com.chua.starter.device.support.controller",
        "com.chua.starter.device.support.service",
        "com.chua.starter.device.support.scheduler",
        "com.chua.starter.device.support.util",
        "com.chua.starter.device.provider"
})
@MapperScan("com.chua.starter.device.support.mapper")
@EnableScheduling
public class DeviceAutoConfiguration