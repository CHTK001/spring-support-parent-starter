package com.chua.starter.server.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = ServerManagementProperties.PREFIX)
public class ServerManagementProperties {

    public static final String PREFIX = "plugin.server";

    private boolean enable = true;

    private final Metrics metrics = new Metrics();

    private final FileWatch fileWatch = new FileWatch();

    private final RemoteGateway remoteGateway = new RemoteGateway();

    private final Guacamole guacamole = new Guacamole();

    private final ServiceOperation serviceOperation = new ServiceOperation();

    @Data
    public static class Metrics {
        private boolean enable = true;
        private long refreshIntervalMs = 5000L;
        private int timeoutMs = 8000;
        private boolean cacheEnabled = true;
        private long cacheTtlSeconds = 3600L;
        private double cpuWarningPercent = 75D;
        private double cpuDangerPercent = 90D;
        private double memoryWarningPercent = 75D;
        private double memoryDangerPercent = 90D;
        private double diskWarningPercent = 80D;
        private double diskDangerPercent = 92D;
        private double ioWarningBytesPerSecond = 50 * 1024 * 1024D;
        private double ioDangerBytesPerSecond = 120 * 1024 * 1024D;
        private int latencyWarningMs = 120;
        private int latencyDangerMs = 300;
    }

    @Data
    public static class FileWatch {
        private boolean enable = true;
        private long pollIntervalMs = 2000L;
        private int maxReadBytes = 262144;
    }

    @Data
    public static class RemoteGateway {
        private boolean enable = false;
        private String defaultProvider = "guacamole";
    }

    @Data
    public static class Guacamole {
        private boolean enable = false;
        private String gatewayUrl;
        private String websocketPath = "/guacamole/websocket-tunnel";
        private String launchPath = "/#/client/";
        private String authMode = "connection";
        private String jsonSecretKey;
        private long jsonExpiresSeconds = 300L;
        private String jsonUsername = "server-console";
        private String defaultLinuxProtocol = "ssh";
        private String defaultWindowsProtocol = "rdp";
    }

    @Data
    public static class ServiceOperation {
        private boolean autoDetectEnabled = true;
        private int logRetentionDays = 7;
    }
}
