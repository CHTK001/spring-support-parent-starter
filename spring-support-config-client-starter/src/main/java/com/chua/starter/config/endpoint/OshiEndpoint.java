package com.chua.starter.config.endpoint;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.constant.Projects;
import com.chua.oshi.support.*;
import com.chua.starter.config.configuration.StartupTimeListener;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import javax.annotation.Resource;

/**
 * oshi
 * @author CH
 */
@WebEndpoint(id = "oshi")
public class OshiEndpoint {
    private static final int OSHI_WAIT_SECOND = 1000;

    @Resource
    private StartupTimeListener startupTimeListener;
    @ReadOperation
    public JSONObject oshi() {
        return new JSONObject()
                .fluentPut("cpu", Oshi.newCpu(1000))
                .fluentPut("mem", Oshi.newMem())
                .fluentPut("jvm", Oshi.newJvm())
//                .fluentPut("process", Oshi.newProcess())
                .fluentPut("startupTime", startupTimeListener.getStartupTime())
                .fluentPut("pid", Projects.getPid())
                .fluentPut("sys", Oshi.newSys());
    }
}
