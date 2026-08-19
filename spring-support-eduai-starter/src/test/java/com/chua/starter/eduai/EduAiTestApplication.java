package com.chua.starter.eduai;

import com.chua.starter.eduai.configuration.EduAiAutoConfiguration;
import com.chua.starter.eduai.controller.EduQuestionController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * 教育 AI 后端测试启动类。
 *
 * <p>在 {@code @SpringBootApplication} 启动模式下，手动 {@code @Import}
 * 第三方 starter 的 Configuration（部分需要显式启用），并直接定义
 * {@link com.chua.starter.queue.MessageTemplate} bean 确保调试控制器可注入。</p>
 *
 * @since 4.0.0.42
 */
@Slf4j
@SpringBootApplication(scanBasePackageClasses = EduQuestionController.class)
@Import({
        EduAiAutoConfiguration.class,
        com.chua.starter.queue.configuration.QueueAutoConfiguration.class
})
public class EduAiTestApplication {

    /**
     * 显式提供 {@link com.chua.starter.queue.MessageTemplate} bean。
     *
     * <p>当第三方 starter 的自动配置因为环境差异未生效时，提供本地内存版作为兜底，
     * 确保 EduAiDebugController 的依赖注入可以满足。</p>
     */
    @Bean(destroyMethod = "close")
    @Primary
    public com.chua.starter.queue.MessageTemplate localMessageTemplate() {
        com.chua.starter.queue.properties.QueueProperties properties = new com.chua.starter.queue.properties.QueueProperties();
        properties.setType("local");
        properties.setDefaultDestination("edu.queue");
        return new com.chua.starter.queue.template.LocalMessageTemplate(properties.getLocal());
    }

    /**
     * 测试入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(EduAiTestApplication.class, args);
        log.info("教育 AI 后端启动成功 — 端口: {}", ctx.getEnvironment().getProperty("server.port"));
    }
}
