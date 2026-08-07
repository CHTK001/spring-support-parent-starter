package com.chua.starter.job.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Job 模块配置属性 (骨架版)。
 * <p>
 * 仅保留当前保留类所需要的字段:模块开关、日志路径。
 * 与 sys_job 表 / 远程执行器 / 注解同步等历史能力相关的配置已移除。
 * </p>
 *
 * @author CH
 * @see JobConfiguration
 */
@Data
@ConfigurationProperties(prefix = JobProperties.PRE)
public class JobProperties {

    /** 配置前缀 */
    public static final String PRE = "plugin.job";

    /** 是否启用 Job 模块 */
    private boolean enable = false;

    /** 日志文件存储路径 (复用保留类 JobFileAppender 初始化) */
    private String logPath = "/data/applogs/job/jobhandler";
}
