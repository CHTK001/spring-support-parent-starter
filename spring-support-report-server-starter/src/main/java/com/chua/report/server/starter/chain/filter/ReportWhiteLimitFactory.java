package com.chua.report.server.starter.chain.filter;

import com.chua.common.support.function.Initializable;
import com.chua.common.support.range.IpRange;
import com.chua.report.server.starter.entity.MonitorProxyPluginList;

import java.util.LinkedList;
import java.util.List;

/**
 * 限制工厂类
 *
 * 该类作为一个工厂，负责生产具有特定限制的物体或服务。
 * 这里的“限制”可以理解为对某些资源的访问限制、数量限制或其他形式的约束。
 * 该类的设计符合工厂模式，旨在通过提供不同的工厂方法来创建具有不同限制的对象。
 *
 * @author CH
 * @since 2024/6/26
 */
public class ReportWhiteLimitFactory implements Initializable {
    private final List<MonitorProxyPluginList> list;
    private final List<IpRange> ranges = new LinkedList<>();
    public ReportWhiteLimitFactory(List<MonitorProxyPluginList> list) {
        this.list = new LinkedList<>(list);
    }
    /**
     * 刷新
     * @param list list
     */
    public synchronized void refresh(List<MonitorProxyPluginList> list) {
        this.list.clear();
        ranges.clear();
        this.list.addAll(list);
        initialize();
    }
    @Override
    public void initialize() {
        for (MonitorProxyPluginList monitorProxyLimitList : list) {
            if(monitorProxyLimitList.getProxyConfigListDisabled() == 1
                    || "WHITE".equalsIgnoreCase(monitorProxyLimitList.getProxyConfigListType())) {
                continue;
            }

            ranges.add(new IpRange(monitorProxyLimitList.getProxyConfigList()));
        }
    }


    /**
     * 尝试获取
     * @param hostname url
     * @return 是否获取
     */
    public boolean tryAcquire(String hostname) {
        if(ranges.isEmpty()) {
            return true;
        }
        for (IpRange range : ranges) {
            if(range.inRange(hostname)) {
                return true;
            }
        }

        return false;
    }


}

