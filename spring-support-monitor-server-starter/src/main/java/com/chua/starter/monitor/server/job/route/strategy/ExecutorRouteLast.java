package com.chua.starter.monitor.server.job.route.strategy;


import com.chua.common.support.annotations.Spi;
import com.chua.starter.monitor.job.TriggerParam;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.job.route.ExecutorRouter;

import java.util.Collections;
import java.util.List;

/**
 * Created by xuxueli on 17/3/10.
 */
@Spi("last")
public class ExecutorRouteLast extends ExecutorRouter {

    @Override
    public List<MonitorRequest> route(TriggerParam triggerParam, List<MonitorRequest> addressList) {
        return Collections.singletonList(addressList.get(addressList.size() - 1));
    }

}
