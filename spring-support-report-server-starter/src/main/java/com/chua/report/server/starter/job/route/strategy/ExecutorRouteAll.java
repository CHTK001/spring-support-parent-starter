package com.chua.report.server.starter.job.route.strategy;


import com.chua.common.support.annotations.Spi;
import com.chua.common.support.discovery.Discovery;
import com.chua.report.client.starter.job.TriggerParam;
import com.chua.report.server.starter.job.route.ExecutorRouter;

import java.util.Set;

/**
 * 单个JOB对应的每个执行器，最久为使用的优先被选举
 * Created by xuxueli on 17/3/10.
 */
@Spi("all")
public class ExecutorRouteAll extends ExecutorRouter {

    @Override
    public Set<Discovery> route(TriggerParam triggerParam, Set<Discovery> addressList) {
        return addressList;
    }
}
