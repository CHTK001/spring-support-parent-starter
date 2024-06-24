package com.chua.starter.monitor.server.service.impl;

import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.geo.GeoSetting;
import com.chua.common.support.geo.IpPosition;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.properties.IpProperties;
import com.chua.starter.monitor.server.service.IptablesService;
import jakarta.annotation.Resource;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

/**
 * ip
 * @author CH
 * @version 1.0.0
 * @since 2024/01/19
 */
@Service
public class IptablesServiceImpl implements IptablesService, ApplicationContextAware {

    @Resource
    private IpProperties ipProperties;

    private IpPosition ipPosition;

    /**
     * 翻译地址
     * @param address 地址
     * @return 结果
     */
    @Override
    public ReturnResult<GeoCity> transferAddress(String address) {
        if(StringUtils.isEmpty(address)) {
            return ReturnResult.illegal("地址不能为空");
        }

        return null == ipPosition ? ReturnResult.illegal("解析失败") : ReturnResult.ok(ipPosition.getCity(address));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ipProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate("plugin.ip", IpProperties.class);

        if(!ipProperties.isEnable()) {
            return;
        }
        ipPosition = ServiceProvider.of(IpPosition.class).getNewExtension(ipProperties.getIpType(), GeoSetting.builder()
                        .databaseFile(ipProperties.getDatabaseFile())
                        .build()
                );
    }
}
