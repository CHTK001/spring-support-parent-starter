package com.chua.starter.monitor.server.job.route;

import com.chua.starter.monitor.job.TriggerParam;
import com.chua.starter.monitor.request.MonitorRequest;

import java.util.List;

/**
 * Created by xuxueli on 17/3/10.
 */
public abstract class ExecutorRouter {

    /**
     * route address
     *
     * @param addressList
     * @return  ReturnT.content=address
     */
    public abstract List<MonitorRequest> route(TriggerParam triggerParam, List<MonitorRequest> addressList);

}
