package com.chua.starter.monitor.server.job.route.strategy;


import com.chua.common.support.annotations.Spi;
import com.chua.starter.monitor.job.TriggerParam;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.job.route.ExecutorRouter;

import java.util.List;

/**
 * 单个JOB对应的每个执行器，最久为使用的优先被选举
 * Created by xuxueli on 17/3/10.
 */
@Spi("all")
public class ExecutorRouteAll extends ExecutorRouter {

    @Override
    public List<MonitorRequest> route(TriggerParam triggerParam, List<MonitorRequest> addressList) {
        return addressList;
    }
}
