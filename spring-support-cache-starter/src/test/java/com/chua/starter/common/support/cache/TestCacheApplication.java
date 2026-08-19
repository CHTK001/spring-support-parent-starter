/**
 * 缓存模块测试启动器 — 启用缓存自动配置
 *
 * @author CH
 * @since 2026/8/2
 */
@SpringBootApplication(exclude = {
        DataRedisAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class
})
/**
 * public class TestCacheApplication
 *
 * @author CH
 * @since 4.0.0.42
 */
public class TestCacheApplication