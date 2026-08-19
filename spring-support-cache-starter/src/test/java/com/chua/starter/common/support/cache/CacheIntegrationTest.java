/**
 * 真实 Spring 容器集成测试 —— 验证缓存与业务集成行为。
 *
 * @author CH
 */
@SpringBootTest(classes = TestCacheApplication.class)
@Import(CacheIntegrationTest.CacheTestService.class)
@TestPropertySource(properties = {
        "plugin.cache.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:cache-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
public class CacheIntegrationTest