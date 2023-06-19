package com.chua.starter.task.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.task.support.properties.TaskProperties.PRE;

/**
 * 配置
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class TaskProperties {

    public static final String PRE = "plugin.task";
    /**
     * 定时任务检测器（min）
     */
    private long checkTime = 5L;
}
