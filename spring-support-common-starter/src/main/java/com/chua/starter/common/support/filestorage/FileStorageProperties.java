package com.chua.starter.common.support.filestorage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件存储
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = FileStorageProperties.PRE, ignoreInvalidFields = true)
public class FileStorageProperties {

    public static final String PRE = "plugin.file.storage";


    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 配置
     */
    private List<Setting> properties;

    @Data
    public static class Setting {

        /**
         * 文件类型
         */
        private String type = "filesystem";

        /**
         * 端口,多个端口逗号分割
         */
        private String port;

        /**
         * OSS存储桶的名称。
         */
        private String bucket;

        /**
         * OSS存储桶所在的区域。
         */
        private String region;
        /**
         * 访问OSS所使用的Access Key ID。
         * Access Key是访问OSS的身份验证密钥的一部分。
         */
        private String accessKeyId;


        /**
         * 访问OSS所使用的Access Key Secret。
         * Access Key Secret是访问OSS的身份验证密钥的另一部分。
         */
        private String accessKeySecret;

        /**
         * OSS的Endpoint，即访问存储桶的区域特定URL。
         * 例如，华�?（杭州）的Endpoint为https://oss-cn-hangzhou.aliyuncs.com。
         */
        private String endpoint;

        /**
         * 连接超时时间，单位为毫秒，默认为10秒。
         * 指定客户端在尝试建立连接时等待的时间。
         */
        private long connectionTimeoutMills = 10 * 1000;

        /**
         * 会话超时时间，单位为毫秒，默认为10秒。
         * 指定客户端在保持连接活动状态时等待的时间。
         */
        private long sessionTimeoutMills = 10 * 1000;

        /**
         * 字符集编码，默认为UTF-8。
         */
        private Charset charset = StandardCharsets.UTF_8;

    }
}

