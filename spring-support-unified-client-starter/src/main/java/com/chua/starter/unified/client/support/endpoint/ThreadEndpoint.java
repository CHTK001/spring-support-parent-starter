package com.chua.starter.unified.client.support.endpoint;


import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import java.util.Map;

@WebEndpoint(id = "trace")
public class ThreadEndpoint {


    @ReadOperation
    public Map<Thread, StackTraceElement[]> read() {
        return Thread.getAllStackTraces();
    }
}
