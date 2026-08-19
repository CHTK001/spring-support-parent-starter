/**
 * 设备管理后端测试启动类。
 * <p>仅用于本地自检，启动后访问 {@code http://localhost:19091/api/device/page?pageNum=1&pageSize=20} 验证。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@SpringBootApplication(scanBasePackageClasses = {
        DeviceController.class,
        DeviceAutoConfiguration.class
})
public class DeviceTestApplication