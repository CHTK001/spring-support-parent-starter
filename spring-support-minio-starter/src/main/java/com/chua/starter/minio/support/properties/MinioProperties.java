package com.chua.starter.minio.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * minio
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = MinioProperties.PRE)
public class MinioProperties {

    public static final String PRE = "plugin.spring.minio";
    /**
     * 默认bucket
     */
    private String bucket = "default";
    /**
     * 服务器地址
     */
    private String address;
    /**
     * 账号
     */
    private String username = "minioadmin";

    /**
     * 密码
     */
    private String password = "minioadmin";

    /**
     * 默认bucket
     * @return nb
     */
    public String getDefaultBucketName() {
        return bucket;
    }
}
