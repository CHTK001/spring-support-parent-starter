/**
 * 教育AI模块自动配置
 * <p>
 * 自动扫描教育AI模块的 Controller 与 Service，并注册 Mapper，
 * 提供题库管理、AI组卷、AI阅卷、知识库、错题库等 AI 教学能力。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@AutoConfiguration
@MapperScan({
        "com.chua.starter.eduai.mapper",
        "com.chua.starter.eduai.dimension.mapper"
})
@ComponentScan({
        "com.chua.starter.eduai.controller",
        "com.chua.starter.eduai.dimension.controller",
        "com.chua.starter.eduai.service.impl",
        "com.chua.starter.eduai.dimension.service",
        "com.chua.starter.eduai.ai"
})
/**
 * public class EduAiAutoConfiguration
 *
 * @author CH
 * @since 4.0.0.42
 */
@EnableConfigurationProperties(EduAiProperties.class)
@EnableAsync
public class EduAiAutoConfiguration