package com.chua.starter.project.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 发布项目管理模块配置
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "project.support")
public class ProjectProperties {

    /** 部署包归档目录（默认工作目录下 project-archives，生产建议配置到独立存储盘） */
    private String archiveDir = "./project-archives";

    /** 上传部署包大小上限（字节，默认 512MB） */
    private long maxUploadBytes = 512L * 1024 * 1024;

    /** 允许的部署包扩展名（小写，含点前缀） */
    private String[] allowedExtensions = {
            ".tar.gz", ".tgz", ".zip", ".tar", ".jar", ".war", ".bin", ".sh"
    };

    /** 单主机脚本执行超时（秒，默认 60） */
    private int scriptTimeoutSeconds = 60;

    /** 健康检查单次探测超时（秒，默认 15） */
    private int healthTimeoutSeconds = 15;
}