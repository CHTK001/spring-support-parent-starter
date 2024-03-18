package com.chua.starter.monitor.server.service.impl;

import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.geo.IpPosition;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.properties.IpProperties;
import com.chua.starter.monitor.server.service.IptablesService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * ip
 * @author CH
 * @version 1.0.0
 * @since 2024/01/19
 */
@Service
public class IptablesServiceImpl implements IptablesService {

    @Resource
    private IpProperties ipProperties;
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

        IpPosition ipPosition = ServiceProvider.of(IpPosition.class).getExtension(ipProperties.getIpType());
        return null == ipPosition ? ReturnResult.illegal("解析失败") : ReturnResult.ok(ipPosition.getCity(address));
    }
}
