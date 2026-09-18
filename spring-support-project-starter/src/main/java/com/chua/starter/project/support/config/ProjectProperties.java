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

    /** 指标持久化 JDBC 连接串（默认写宿主自身 H2/MySQL，前缀 project.support.metric-jdbc-url 可覆盖） */
    private String metricJdbcUrl = "jdbc:h2:mem:project-metric;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=false;NON_KEYWORDS=VALUE";

    /** 指标持久化 JDBC 用户名（H2 内存库可空） */
    private String metricJdbcUser;

    /** 指标持久化 JDBC 密码（H2 内存库可空） */
    private String metricJdbcPassword;

    /** 指标保留天数（默认 7 天，超过自动清理；0=不清理） */
    private int metricRetentionDays = 7;

    /** 指标清理执行间隔（秒，默认 3600=每小时扫一次） */
    private int metricCleanupIntervalSeconds = 3600;
    /** 部署日志保留天数（默认 7 天，超过自动清理；0=不清理） */
    private int deployLogRetentionDays = 7;

    /** 部署日志清理执行间隔（秒，默认 3600=每小时扫一次） */
    private int deployLogCleanupIntervalSeconds = 3600;

    /** 实时日志流数量上限（默认 16） */
    private int logStreamMaxStreams = 16;

    /** 实时日志流观察者空闲超时（毫秒，默认 300000=5分钟未续订即剔除） */
    private long logStreamIdleMillis = 300_000L;

    /** 实时日志流采集间隔（毫秒，默认 5000） */
    private long logStreamIntervalMillis = 5_000L;

    /** 实时日志流单帧最大字符数（默认 8192，超出截断保留最新部分） */
    private int logStreamMaxChunkChars = 8192;

    /** 实时日志流首帧拉取行数（默认 500） */
    private int logStreamTailLines = 500;
}