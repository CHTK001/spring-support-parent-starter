package com.chua.shell.support.command.spring;

import com.alibaba.fastjson2.JSON;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.google.common.base.Strings;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ApplicationContext;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * invoke
 *
 * @author CH
 */
public class LoggerSpring {
    private static final String GET = "GET";
    private static final String SET = "SET";

    public static final PathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 执行
     *
     * @param command 命令
     * @return 结果
     */
    public static String execute(List<String> command) {
        if (command.size() == 3) {
            if (GET.equalsIgnoreCase(command.get(0)) && Strings.isNullOrEmpty(command.get(2))) {
                command = command.subList(0, command.size() - 1);
            }
        }

        if (command.size() == 1) {
            return get(GET, command.get(0));
        }

        //GET
        if (command.size() == 2) {
            String s = command.get(0);
            if (GET.equalsIgnoreCase(s)) {
                return get(GET, command.get(1));
            }
            return set(SET, command.get(0), command.get(1));
        }

        if (command.size() == 3) {
            return set(command.get(0), command.get(1), command.get(2));
        }

        return "命令无法解析";
    }

    /**
     * set
     *
     * @param s  set
     * @param s1 參數
     * @param s2 值
     * @return 結果
     */
    private static String set(String s, String s1, String s2) {
        if (!SET.equalsIgnoreCase(s)) {
            return "命令异常是否是 set";
        }
        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        LoggingSystem loggingSystem = applicationContext.getBean(LoggingSystem.class);
        LoggerConfiguration loggerConfiguration = loggingSystem.getLoggerConfiguration(s1);
        if (null == loggerConfiguration) {
            return s1 + "日志不存在";
        }
        try {
            loggingSystem.setLogLevel(s1, LogLevel.valueOf(s2.toUpperCase()));
        } catch (IllegalArgumentException ignored) {
        }

        return "";

    }

    /**
     * get
     *
     * @param s  get
     * @param s1 參數
     * @return 結果
     */
    private static String get(String s, String s1) {
        if (!GET.equalsIgnoreCase(s)) {
            return "命令异常是否是 get";
        }


        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        LoggingSystem loggingSystem = applicationContext.getBean(LoggingSystem.class);
        Map<String, Object> rs = new LinkedHashMap<>();
        rs.put("head", new String[]{"序列", "名称", "日志级别"});
        List<Object[]> tpl = new LinkedList<>();
        rs.put("rows", tpl);

        AtomicInteger index = new AtomicInteger(1);
        List<LoggerConfiguration> loggerConfigurations = loggingSystem.getLoggerConfigurations();
        for (LoggerConfiguration loggerConfiguration : loggerConfigurations) {
            String name = loggerConfiguration.getName();
            if (PATH_MATCHER.match(s1, name)) {
                tpl.add(new Object[]{index.getAndIncrement(), name, loggerConfiguration.getEffectiveLevel().name()});
            }
        }

        return "@table " + JSON.toJSONString(rs);
//            PATH_MATCHER.match(s1, )

    }
}
