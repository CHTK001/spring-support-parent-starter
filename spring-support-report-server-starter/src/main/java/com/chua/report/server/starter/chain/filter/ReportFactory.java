package com.chua.report.server.starter.chain.filter;

import com.chua.report.server.starter.entity.MonitorProxyPluginLimit;
import com.chua.report.server.starter.entity.MonitorProxyPluginList;
import lombok.Data;
import lombok.Setter;

import java.util.List;

/**
 * 监控工厂
 * @author CH
 * @since 2024/7/30
 */
@Data
@Setter
public class ReportFactory {

    private ReportUrlLimitFactory reportUrlLimitFactory;
    private ReportIpLimitFactory reportIpLimitFactory;
    private ReportBlackLimitFactory reportBlackLimitFactory;
    private ReportWhiteLimitFactory reportWhiteLimitFactory;


    /**
     * 升级
     * @param list list
     */
    public void upgrade(List<MonitorProxyPluginLimit> list) {
        if(null != reportUrlLimitFactory) {
            reportUrlLimitFactory.refresh(list);
        }

        if(null != reportIpLimitFactory) {
            reportIpLimitFactory.refresh(list);
        }
    }

    /**
     * 升级
     * @param list list
     */
    public void upgradeList(List<MonitorProxyPluginList> list) {
        if(null != reportBlackLimitFactory) {
            reportBlackLimitFactory.refresh(list);
        }

        if(null != reportWhiteLimitFactory) {
            reportWhiteLimitFactory.refresh(list);
        }
    }
}
