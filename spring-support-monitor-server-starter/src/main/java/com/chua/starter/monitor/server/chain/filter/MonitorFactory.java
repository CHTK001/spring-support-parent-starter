package com.chua.starter.monitor.server.chain.filter;

import com.chua.starter.monitor.server.entity.MonitorProxyLimit;
import com.chua.starter.monitor.server.entity.MonitorProxyLimitList;
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
public class MonitorFactory {

    private UrlLimitFactory urlLimitFactory;
    private IpLimitFactory ipLimitFactory;
    private BlackLimitFactory blackLimitFactory;
    private WhiteLimitFactory whiteLimitFactory;


    /**
     * 升级
     * @param list list
     */
    public void upgrade(List<MonitorProxyLimit> list) {
        if(null != urlLimitFactory) {
            urlLimitFactory.refresh(list);
        }

        if(null != ipLimitFactory) {
            ipLimitFactory.refresh(list);
        }
    }

    /**
     * 升级
     * @param list list
     */
    public void upgradeList(List<MonitorProxyLimitList> list) {
        if(null != blackLimitFactory) {
            blackLimitFactory.refresh(list);
        }

        if(null != whiteLimitFactory) {
            whiteLimitFactory.refresh(list);
        }
    }
}
