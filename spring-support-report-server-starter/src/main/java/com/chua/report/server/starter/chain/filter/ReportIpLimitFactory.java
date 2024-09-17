package com.chua.report.server.starter.chain.filter;

import com.chua.common.support.function.Initializable;
import com.chua.common.support.matcher.AntPathMatcher;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.common.support.task.limit.Limiter;
import com.chua.common.support.task.limit.LimiterProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.server.starter.entity.MonitorProxyPluginLimit;

import java.util.LinkedList;
import java.util.List;

/**
 * 限制工厂类
 * <p>
 * 该类作为一个工厂，负责生产具有特定限制的物体或服务。
 * 这里的“限制”可以理解为对某些资源的访问限制、数量限制或其他形式的约束。
 * 该类的设计符合工厂模式，旨在通过提供不同的工厂方法来创建具有不同限制的对象。
 *
 * @author CH
 * @since 2024/6/26
 */
public class ReportIpLimitFactory implements Initializable {
    private final List<MonitorProxyPluginLimit> list;
    private final LimiterProvider limitProvider = new LimiterProvider();
    private final List<String> black = new LinkedList<>();
    private final List<String> black2 = new LinkedList<>();

    private final PathMatcher pathMatcher = new AntPathMatcher();

    public ReportIpLimitFactory(List<MonitorProxyPluginLimit> list) {
        this.list = new LinkedList<>(list);
    }

    /**
     * 刷新
     * @param list list
     */
    public synchronized void refresh(List<MonitorProxyPluginLimit> list) {
        limitProvider.clear();
        black.clear();
        black2.clear();
        this.list.clear();
        this.list.addAll(list);
        initialize();
    }

    @Override
    public void initialize() {
        for (MonitorProxyPluginLimit monitorProxyLimit : list) {
            if (monitorProxyLimit.getProxyConfigLimitDisabled() == 1 || "IP".equalsIgnoreCase(monitorProxyLimit.getProxyConfigLimitType()) ||
                    StringUtils.isEmpty(monitorProxyLimit.getProxyConfigLimitPathOrIp())) {
                continue;
            }

            limitProvider.addLimiter(monitorProxyLimit.getProxyConfigLimitPathOrIp(), Limiter.of(monitorProxyLimit.getProxyConfigLimitPerSeconds()));
        }
    }


/**
 * 尝试获取
 * @param url url
 * @return 是否获取
 */
public boolean tryAcquire(String url) {
    if (limitProvider.hasLimiter(url)) {
        return limitProvider.tryAcquire(url);
    }

    return true;
}


}

