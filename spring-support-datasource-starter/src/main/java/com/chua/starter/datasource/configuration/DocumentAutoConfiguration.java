/**
 * 数据源文档预览自动配置。
 * <p>
 * 所有 Bean 都是无条件注册（依赖检测在构造函数内做），
 * 避免 Spring Boot 的 {@code @ConditionalOnBean} 在同类配置中求值失败的问题。
 * 顶级 {@code @ConditionalOnClass} 保证核心类存在；{@code @ConditionalOnWebApplication}
 * 保证仅 Web 上下文生效。
 * </p>
 *
 * @author CH
 * @since 2026/8/5
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = {
        "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping",
        "com.chua.common.support.lang.document.DocumentParser"
})
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = DocumentProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DocumentProperties.class)
public class DocumentAutoConfiguration