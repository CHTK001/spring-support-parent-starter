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
    private final Environment environment;

    @Override
    public void run(String... args) throws Exception {
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        if (contextPath.isEmpty()) {
            contextPath = contextPath + "/";
        }
        String port = environment.getProperty("server.port", "8080");
        log.info("\r\n当前swagger文档地址      " + "http://127.0.0.1:" + port + "/" + StringUtils.removeEnd(StringUtils.removeStart(contextPath, "/"), "/") + "/doc.html"
                + "\r\n健康检查               " + "http://127.0.0.1:" + port + "/" + StringUtils.removeEnd(StringUtils.removeStart(contextPath, "/"), "/") + "/actuator");
    }
}