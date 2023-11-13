package com.chua.starter.unified.client.support.event;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.constant.Projects;
import com.chua.oshi.support.Oshi;
import com.chua.starter.unified.client.support.configuration.StartupTimeListener;
import io.vertx.core.http.HttpServerResponse;

import javax.annotation.Resource;

/**
 * 事件
 *
 * @author CH
 * @since 2023/09/10
 */
@Spi("oshi")
public class OshiEvent implements Event{
    @Resource
    private StartupTimeListener startupTimeListener;
    @Override
    public void onListener(HttpServerResponse response) {

        response.end(new JSONObject()
                .fluentPut("cpu", Oshi.newCpu(1000))
                .fluentPut("mem", Oshi.newMem())
                .fluentPut("jvm", Oshi.newJvm())
                .fluentPut("time", System.currentTimeMillis() / 1000)
//                .fluentPut("process", Oshi.newProcess())
                .fluentPut("startupTime", startupTimeListener.getStartupTime())
                .fluentPut("pid", Projects.getPid())
                .fluentPut("sys", Oshi.newSys()).toString());
    }
}
