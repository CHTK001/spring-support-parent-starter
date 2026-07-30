package com.chua.starter.filesystem.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件系统分片上传配置
 *
 * @author CH
 * @since 1.0
 */
@Data
@ConfigurationProperties(prefix = FileStorageUploadProperties.PREFIX)
public class FileStorageUploadProperties {

    public static final String PREFIX = "plugin.filesystem.upload";

    /**
     * 分片大小（字节），默认 5MB
     */
    private long chunkSize = 5 * 1024 * 1024;

    /**
     * 临时目录路径
     */
    private String tempDir = System.getProperty("java.io.tmpdir") + "/filesystem-upload";
}