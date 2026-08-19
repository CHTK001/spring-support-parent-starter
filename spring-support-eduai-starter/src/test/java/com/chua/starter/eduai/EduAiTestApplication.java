/**
 * 教育 AI 后端测试启动类。
 *
 * <p>在 {@code @SpringBootApplication} 启动模式下，手动 {@code @Import}
 * 第三方 starter 的 Configuration（部分需要显式启用），并直接定义
 * {@link com.chua.starter.queue.MessageTemplate} bean 确保调试控制器可注入。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@SpringBootApplication(scanBasePackageClasses = EduQuestionController.class)
@Import({
        EduAiAutoConfiguration.class,
        com.chua.starter.queue.configuration.QueueAutoConfiguration.class
})
/**
 * public class EduAiTestApplication
 *
 * @author CH
 * @since 4.0.0.42
 */
public class EduAiTestApplication