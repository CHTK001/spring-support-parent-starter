package com.chua.starter.spider;

import com.chua.starter.spider.configuration.SpiderAutoConfiguration;
import com.chua.starter.spider.controller.SpiderCenterController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 爬虫中心后端测试启动类。
 * <p>仅用于本地自检，启动后访问 {@code http://localhost:19091/spider/definitions/page} 验证。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@SpringBootApplication(scanBasePackages = {
        "com.chua.starter.spider"
})
public class SpiderTestApplication {

    /**
     * 测试入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(SpiderTestApplication.class, args);
        log.info("爬虫中心后端启动成功 — 端口: {}", ctx.getEnvironment().getProperty("server.port"));
    }
}

