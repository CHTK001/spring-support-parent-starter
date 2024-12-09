package com.chua.starter.common.support.scheduler;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * @author CH
 * @since 2024/12/9
 */
@Endpoint(id = "scheduler")
public class SchedulerEndpoint {


    @ReadOperation
    public String ssss() {
        return "";
    }
}
