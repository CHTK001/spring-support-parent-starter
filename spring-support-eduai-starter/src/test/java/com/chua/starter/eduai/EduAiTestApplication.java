package com.chua.starter.eduai;

import com.chua.starter.eduai.configuration.EduAiAutoConfiguration;
import com.chua.starter.eduai.controller.EduQuestionController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 教育 AI 后端测试启动类。
 * <p>仅用于本地自检，启动后访问 {@code http://localhost:19091/v2/edu/question/page?pageNum=1&pageSize=20} 验证。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@SpringBootApplication(scanBasePackageClasses = {
        EduQuestionController.class,
        EduAiAutoConfiguration.class
})
public class EduAiTestApplication {

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
