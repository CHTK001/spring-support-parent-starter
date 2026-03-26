package com.chua.starter.proxy.support.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.common.support.network.protocol.filter.IPRateLimitServletFilter;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import com.chua.starter.proxy.support.entity.SystemServerSettingIPRateLimit;
import com.chua.starter.proxy.support.mapper.SystemServerSettingIPRateLimitMapper;

import java.util.List;

/**
 * @author CH
 * @since 2025/8/11 17:02
 */
@Spi("ipRateLimit")
public class IPRateLimitServletFilterHandler implements ServletFilterHandler {

    private final SystemServerSettingIPRateLimitMapper systemServerSettingIPRateLimitMapper;

    public IPRateLimitServletFilterHandler() {
        this.systemServerSettingIPRateLimitMapper = SpringBeanUtils.getBean(SystemServerSettingIPRateLimitMapper.class);
    }

    /**
     * 注册IP限流
     */
    @Override
    public IPRateLimitServletFilter handle(SystemServerSetting setting, ConfigureObjectContext objectContext) {
        IPRateLimitServletFilter servletFilter = new IPRateLimitServletFilter();
        upgrade(servletFilter, setting);
        return servletFilter;
    }

    @Override
    public void update(ServletFilter filter, SystemServerSetting setting) {
        if (filter instanceof IPRateLimitServletFilter ipRateLimitServletFilter) {
            ipRateLimitServletFilter.clear();
            upgrade(ipRateLimitServletFilter, setting);
        }
    }

    /**
     * 升级
     *
     * @param servletFilter ipRateLimitServletFilter
     * @param setting       setting
     */
    private void upgrade(IPRateLimitServletFilter servletFilter, SystemServerSetting setting) {
        List<SystemServerSettingIPRateLimit> systemServerSettingIPRateLimits = systemServerSettingIPRateLimitMapper.selectList(
                Wrappers.<SystemServerSettingIPRateLimit>lambdaQuery()
                        .eq(SystemServerSettingIPRateLimit::getIpRateLimitSettingId, setting.getSystemServerSettingId())
        );
        for (SystemServerSettingIPRateLimit ipRateLimit : systemServerSettingIPRateLimits) {
            String ipRateLimitType = ipRateLimit.getIpRateLimitType();
            if ("BLACKLIST".equalsIgnoreCase(ipRateLimitType)) {
                servletFilter.addBlacklist(ipRateLimit.getIpRateLimitIp());
                continue;
            }
            if ("WHITELIST".equalsIgnoreCase(ipRateLimitType)) {
                servletFilter.addWhitelist(ipRateLimit.getIpRateLimitIp());
                continue;
            }
            servletFilter.getOrCreateRateLimiter(ipRateLimit.getIpRateLimitIp(), ipRateLimit.getIpRateLimitQps(), "guava");
        }
    }
}




