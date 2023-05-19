package com.chua.starter.script.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * script
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = ScriptProperties.PRE)
public class ScriptProperties {

    public static final String PRE = "plugin.script";

    private List<Config> config;

    private Map<Type, String> watchdog;

    private int timeout = 10;


    @Data
    public static class Config {

        /**
         * 类型
         */
        private Type type = Type.FILE;

        /**
         * 目录/数据源
         */
        private String source;


    }


    public static enum Type {
        /**
         * 文件夹
         */
        FILE,
        /**
         * mysql
         */
        MYSQL
    }
}
