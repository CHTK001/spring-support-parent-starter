package com.chua.starter.common.support.filestorage;

import com.chua.common.support.oss.options.FileStorageOption;
import com.chua.common.support.oss.plugin.FileStorageConversionPlugin;
import com.chua.common.support.oss.plugin.FileStorageMonitorPlugin;
import com.chua.common.support.oss.plugin.FileStorageNamingPlugin;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.chua.starter.common.support.filestorage.FileStorageProperties.PRE;

/**
 * 文件存储属性
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE)
public class FileStorageProperties {

    public static final String PRE = "plugin.store";


    /**
     * 是否开启
     */
    private boolean enable;

    /**
     * 配置
     */
    private List<FileStorageConfig> config;


    @Data
    public static class FileStorageConfig {

        /**
         * Impl
         */
        private String impl;
        /**
         * 存储路径
         */
        private String storagePath;

        /**
         * 桶
         */
        private String bucket;


        /**
         * 端点- 远程服务器地址
         */
        private String endpoint;

        /**
         * 主机
         */
        private String host;
        /**
         * 端口
         */
        private int port;
        /**
         * 用户名
         */
        private String accessKeyId;
        /**
         * 密码，默认空
         */
        private String accessKeySecret;

        /**
         * 编码，默认UTF-8
         */
        private Charset charset = StandardCharsets.UTF_8;

        /**
         * 连接超时时长，单位毫秒，默认10秒
         */
        private long connectionTimeoutMills = 10 * 1000;
        /**
         * Socket连接超时时长，单位毫秒，默认10秒
         */
        private long sessionTimeoutMills = 10 * 1000;


        /**
         * 访问的资源服务器域名
         */
        private String domain = "";


        /**
         * 文件存储转换插件
         */
        private FileStorageConversionPlugin fileStorageConversionPlugin;


        private FileStorageNamingPlugin fileStorageNamingPlugin;

        /**
         * 文件存储监视器插件
         */
        private FileStorageMonitorPlugin fileStorageMonitorPlugin;
        /**
         * 其它自定义配置
         */
        private Map<String,Object> attr;
    }

}
