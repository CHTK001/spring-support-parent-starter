package com.chua.starter.swagger.support;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;

import javax.annotation.Resource;

/**
 * @author CH
 */
@Slf4j
public class Knife4jRunner implements CommandLineRunner {
    @Resource
    private Environment environment;


    @Override
    public void run(String... args) throws Exception {
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        if (!Strings.isNullOrEmpty(contextPath)) {
            contextPath = contextPath + "/";
        }
        String port = environment.getProperty("server.port", "8080");
        log.info("\r\n当前swagger文档地址      http://127.0.0.1:{}{}doc.html"
                + "\r\n健康检查               http://127.0.0.1:{}{}actuator", port, contextPath, port, contextPath);
    }
}