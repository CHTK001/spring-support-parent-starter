package com.chua.report.server.starter.job.route.strategy;


import com.chua.common.support.annotations.Spi;
import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.report.client.starter.job.TriggerParam;
import com.chua.report.server.starter.job.route.ExecutorRouter;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Set;

/**
 * Created by xuxueli on 17/3/10.
 */
@Spi("random")
public class ExecutorRouteRandom extends ExecutorRouter {

    private static final SecureRandom localRandom = new SecureRandom();
    @Override
    public Set<Discovery> route(TriggerParam triggerParam, Set<Discovery> addressList) {
        return Collections.singleton(CollectionUtils.find(addressList, localRandom.nextInt(addressList.size())));
    }

}
