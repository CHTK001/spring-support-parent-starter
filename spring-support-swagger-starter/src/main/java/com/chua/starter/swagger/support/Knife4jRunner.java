package com.chua.starter.swagger.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;

/**
 * Knife4j日志
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class Knife4jRunner implements CommandLineRunner {

    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    private final Environment environment;

    @Override
    public void run(String... args) throws Exception {
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        if (contextPath.isEmpty()) {
            contextPath = contextPath + "/";
        }
        String port = environment.getProperty("server.port", "8080");
        String basePath = StringUtils.removeEnd(StringUtils.removeStart(contextPath, "/"), "/");
        log.info("[Swagger] 文档地址: {}  健康检查: {}",
                CYAN + "http://127.0.0.1:" + port + "/" + basePath + "/doc.html" + RESET,
                CYAN + "http://127.0.0.1:" + port + "/" + basePath + "/actuator" + RESET);
    }
}
