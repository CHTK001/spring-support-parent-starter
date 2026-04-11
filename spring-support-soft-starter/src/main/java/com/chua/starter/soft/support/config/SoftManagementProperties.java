package com.chua.starter.soft.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 软件管理配置。
 *
 * @author CH
 * @since 2026/04/04
 */
@Data
@ConfigurationProperties(prefix = SoftManagementProperties.PREFIX)
public class SoftManagementProperties {

    public static final String PREFIX = "soft.management";

    private boolean enable = true;
    private long logPollIntervalMillis = 2000L;
    private long operationHeartbeatMillis = 1000L;
    private int defaultLogTailLines = 200;
    private long repositoryTimeoutMillis = 30000L;
    private String winSwDownloadUrl = "https://github.com/winsw/winsw/releases/latest/download/WinSW-x64.exe";
    private String artifactUploadRoot = System.getProperty("java.io.tmpdir") + "/soft-repository-artifacts";
}
