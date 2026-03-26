package com.chua.starter.proxy.support.handler;

import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.network.protocol.server.ProtocolServer;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import lombok.extern.slf4j.Slf4j;

/**
 * @author CH
 * @since 2025/8/11 20:14
 */
@Slf4j
public class HotloadingServletFilterHandler {


    private final ProtocolServer serverInstance;
    private final String systemServerSettingType;

    public HotloadingServletFilterHandler(ProtocolServer serverInstance, String systemServerSettingType) {
        this.serverInstance = serverInstance;
        this.systemServerSettingType = systemServerSettingType;
    }

    /**
     * 更新
     *
     * @param setting
     */
    public void update(SystemServerSetting setting) {
        String systemServerSettingFilterId = setting.getSystemServerSettingFilterId();
        if (StringUtils.isEmpty(systemServerSettingFilterId)) {
            log.warn("未配置过滤器ID");
            //新增filter
            ServletFilterHandler servletFilterHandler = ServiceProvider.of(ServletFilterHandler.class).getNewExtension(
                    systemServerSettingType
            );
            ServletFilter servletFilter = servletFilterHandler.handle(setting, serverInstance.getObjectContext());
            setting.setSystemServerSettingFilterId(servletFilter.getFilterId());
            serverInstance.addFilter(servletFilter);
            return;
        }
        ServletFilter filter = serverInstance.getFilter(systemServerSettingFilterId);
        if (null == filter) {
            log.warn("未找到过滤器ID: {}", systemServerSettingFilterId);
            return;
        }
        ServletFilterHandler servletFilterHandler = ServiceProvider.of(ServletFilterHandler.class).getNewExtension(
                systemServerSettingType
        );

        if (null != servletFilterHandler) {
            servletFilterHandler.update(filter, setting);
        }
    }
}




