package com.chua.starter.job.support;

import com.chua.starter.job.support.log.JobFileAppender;

/**
 * 仅用于触发 {@link JobFileAppender#initLogPath(String)} 的初始化器。
 *
 * @author CH
 */
public class JobFileAppenderInitializer {

    public JobFileAppenderInitializer(JobProperties jobProperties) {
        JobFileAppender.initLogPath(jobProperties.getLogPath());
    }
}
