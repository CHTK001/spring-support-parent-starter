package com.chua.report.server.starter.job.route.strategy;


import com.chua.common.support.annotations.Spi;
import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.report.client.starter.job.TriggerParam;
import com.chua.report.server.starter.job.route.ExecutorRouter;

import java.util.Collections;
import java.util.Set;

/**
 * Created by xuxueli on 17/3/10.
 */
@Spi("last")
public class ExecutorRouteLast extends ExecutorRouter {

    @Override
    public Set<Discovery> route(TriggerParam triggerParam, Set<Discovery> addressList) {
        return Collections.singleton(CollectionUtils.findLast(addressList));
    }

}
