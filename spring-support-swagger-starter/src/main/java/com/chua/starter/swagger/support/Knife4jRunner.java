package com.chua.starter.swagger.support;

import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.env.Environment;

/**
 * Knife4j日志
 * @author CH
 */
@Slf4j
public class Knife4jRunner implements CommandLineRunner {

    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

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
        // 判断是否为web环境
        if (!isWebApplication()) {
            return;
        }

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
        
        // 美化日志输出
        var borderColor = CYAN;
        var titleColor = BOLD + YELLOW;
        var labelColor = GREEN;
        var urlColor = CYAN + BOLD;
        var reset = RESET;
        
        var logMessage = "\n" +
                borderColor + "╔═══════════════════════════════════════════════════════════════╗" + reset + "\n" +
                borderColor + "║" + reset + "                    " + titleColor + "Swagger 文档信息" + reset + "                    " + borderColor + "║" + reset + "\n" +
                borderColor + "╠═══════════════════════════════════════════════════════════════╣" + reset + "\n" +
                borderColor + "║" + reset + "  " + labelColor + "文档地址:" + reset + " " + urlColor + docUrl + reset + "  " + borderColor + "║" + reset + "\n" +
                borderColor + "║" + reset + "  " + labelColor + "健康检查:" + reset + " " + urlColor + actuatorUrl + reset + "  " + borderColor + "║" + reset + "\n" +
                borderColor + "╚═══════════════════════════════════════════════════════════════╝" + reset;
        
        log.info(logMessage);
    }

    /**
     * 判断是否为web应用
     *
     * @return 如果是web应用返回true，否则返回false
     */
    private boolean isWebApplication() {
        // 检查spring.main.web-application-type配置
        String webApplicationType = environment.getProperty("spring.main.web-application-type");
        if (StringUtils.isNotBlank(webApplicationType)) {
            return !WebApplicationType.NONE.name().equalsIgnoreCase(webApplicationType);
        }
        
        // 检查是否有server.port配置（非web应用通常不会配置）
        String port = environment.getProperty("server.port");
        if (StringUtils.isBlank(port)) {
            return false;
        }
        
        // 检查类路径中是否有servlet相关的类
        try {
            Class.forName("jakarta.servlet.Servlet");
            return true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("javax.servlet.Servlet");
                return true;
            } catch (ClassNotFoundException ex) {
                return false;
            }
        }
    }
}
