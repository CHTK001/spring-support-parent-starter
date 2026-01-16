package com.chua.starter.mybatis.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static com.chua.starter.mybatis.properties.MybatisPlusProperties.PRE;

/**
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE)
public class MybatisPlusProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;



    public static final String PRE = "plugin.mybatis";

    /**
     * xml是否可热加载
     */
    private boolean openXmlReload = true;

    /**
     * 是否开启只读模式
     * 开启后所有更新、插入、删除操作都会抛出只读异常
     */
    private boolean readOnly = false;

    /**
     * mapper加载源
     */
    private List<SqlMethodProperties> sqlMethod;
    /**
     * 自动热加载方式
     */
    private ReloadType reloadType = ReloadType.AUTO;
    /**
     * 自动热加载时间
     */
    private int reloadTime = 3_000;

    /**
     * 热重载监听目录配置
     * 支持本地文件系统路径和classpath路径
     * 本地路径示例: /path/to/mapper, D:/mappers
     * classpath路径示例: classpath:mapper, classpath*:mapper/**
     */
    private List<ReloadDirectory> reloadDirectories;


    @Data
    public static class SqlMethodProperties {
        /**
         * sql来源
         */
        private SqlMethodType type;

        /**
         * 源信息
         *
         * @see SqlMethodType#FILE 文件路径
         * @see SqlMethodType#MYSQL mysql datasource bean
         */
        private String source;
        /**
         * watchdog超时时间
         */
        private int timeout = 3_000;
        /**
         * 是否开启检测
         */

        private boolean watchdog;
    }

    /**
     * 方法类型
     */
    public enum SqlMethodType {
        /**
         * 文件
         */
        FILE,
        /**
         * mysql
         */
        MYSQL
    }

    /**
     * 类型
     */
    public enum ReloadType {
        /**
         * 不刷新
         */
        NONE,
        /**
         * 自动刷新
         */
        AUTO
    }

    /**
     * 热重载目录配置
     */
    @Data
    public static class ReloadDirectory {
        /**
         * 目录路径
         * 支持格式：
         * - 本地文件系统路径: /path/to/mapper, D:/mappers
         * - classpath路径: classpath:mapper, classpath*:mapper/**
         */
        private String path;

        /**
         * 是否启用监听
         * 本地路径默认启用，classpath路径在jar包中无法监听
         */
        private boolean watchEnabled = true;

        /**
         * 文件匹配模式，默认为 **/*Mapper.xml
         */
        private String pattern = "**/*Mapper.xml";
    }
}
