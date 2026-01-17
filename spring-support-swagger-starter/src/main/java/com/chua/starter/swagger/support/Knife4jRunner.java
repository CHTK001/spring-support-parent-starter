package com.chua.starter.swagger.support;

import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;

/**
 * Knife4j日志
 * @author CH
 */
@Slf4j
public class Knife4jRunner implements CommandLineRunner {

    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    private final Environment environment;

    /**
     * 构造函数
     *
     * @param environment 环境配置
     */
    public Knife4jRunner(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        if (contextPath.isEmpty()) {
            contextPath = "/";
        }
        String port = environment.getProperty("server.port", "8080");
        String basePath = StringUtils.removeEnd(StringUtils.removeStart(contextPath, "/"), "/");
        String docUrl = StringUtils.isNotBlank(basePath) 
                ? "http://127.0.0.1:" + port + "/" + basePath + "/doc.html"
                : "http://127.0.0.1:" + port + "/doc.html";
        String actuatorUrl = StringUtils.isNotBlank(basePath)
                ? "http://127.0.0.1:" + port + "/" + basePath + "/actuator"
                : "http://127.0.0.1:" + port + "/actuator";
        log.info("[Swagger] 文档地址: {}  健康检查: {}",
                CYAN + docUrl + RESET,
                CYAN + actuatorUrl + RESET);
    }
}
