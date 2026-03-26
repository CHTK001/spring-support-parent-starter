package com.chua.starter.proxy.support.handler;

import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.network.protocol.server.ProtocolServer;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import lombok.extern.slf4j.Slf4j;

/**
 * @author CH
 * @since 2025/8/11 20:14
 */
@Slf4j
public class HotloadingDeleteServletFilterHandler {


    private final ProtocolServer serverInstance;

    public HotloadingDeleteServletFilterHandler(ProtocolServer serverInstance, String systemServerSettingType) {
        this.serverInstance = serverInstance;
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
            return;
        }
        ServletFilter filter = serverInstance.getFilter(systemServerSettingFilterId);
        if (null == filter) {
            log.warn("未找到过滤器ID: {}", systemServerSettingFilterId);
            return;
        }
        serverInstance.removeFilter(filter);
    }
}




