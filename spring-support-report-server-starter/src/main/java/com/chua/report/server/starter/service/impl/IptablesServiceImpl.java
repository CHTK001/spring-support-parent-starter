package com.chua.report.server.starter.service.impl;

import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.geo.GeoSetting;
import com.chua.common.support.geo.IpPosition;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.server.starter.service.IptablesService;
import com.chua.starter.common.support.properties.IpProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ip
 * @author CH
 * @version 1.0.0
 * @since 2024/01/19
 */
@Service("IptablesServiceNew")
@RequiredArgsConstructor
public class IptablesServiceImpl implements IptablesService {

    private final IpProperties ipProperties;
    private volatile IpPosition ipPosition;
    private volatile boolean isLoaded = false;
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

        if(!isLoaded) {
            synchronized (this) {
                if(!isLoaded) {
                    isLoaded = true;
                    GeoSetting geoSetting = GeoSetting.builder()
                            .databaseFile(ipProperties.getDatabaseFile())
                            .build();
                    ipPosition = ServiceProvider.of(IpPosition.class).getNewExtension(ipProperties.getIpType(), geoSetting);
                    ipPosition.afterPropertiesSet();
                }
            }
        }

        if(null == ipPosition) {
            return ReturnResult.illegal("解析失败");
        }
        return ReturnResult.ok(ipPosition.getCity(address));
    }
}
