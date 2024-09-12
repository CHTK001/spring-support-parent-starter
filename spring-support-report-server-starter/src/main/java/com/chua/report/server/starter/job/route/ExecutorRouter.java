package com.chua.report.server.starter.job.route;

import com.chua.common.support.discovery.Discovery;
import com.chua.report.client.starter.job.TriggerParam;

import java.util.Set;

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
    public abstract Set<Discovery> route(TriggerParam triggerParam, Set<Discovery> addressList);

}
