package com.chua.starter.common.support.service.impl;

import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.geo.GeoSetting;
import com.chua.common.support.geo.IpPosition;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.properties.IpProperties;
import com.chua.starter.common.support.service.IptablesService;
import org.springframework.stereotype.Service;

/**
 * IP地址服务实现类，提供IP地址转换为地理位置信息的功能
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/19
 */
@Service
public class IptablesServiceImpl implements IptablesService {

    private final IpProperties ipProperties;
    private volatile IpPosition ipPosition;
    private volatile boolean isLoaded = false;

    /**
     * 构造方法，注入IP配置属性
     *
     * @param ipProperties IP配置属性对象，包含数据库文件路径和IP解析类型等配置信息
     */
    public IptablesServiceImpl(IpProperties ipProperties) {
        this.ipProperties = ipProperties;
    }

    /**
     * 将IP地址转换为地理位置信息
     *
     * @param address IP地址字符串，例如: "114.114.114.114" �?"8.8.8.8"
     *                如果是包含端口的格式如 "192.168.1.1:8080"，会自动提取IP部分
     * @return 返回包含地理位置信息的封装结果
     * 成功时返回 {@code ReturnResult.ok(GeoCity)} 包含城市、省份、国家等信息
     * 失败时返回 {@code ReturnResult.failed("错误信息")} 包含具体的错误原因
     */
    @Override
    public ReturnResult<GeoCity> transferAddress(String address) {
        if (StringUtils.isEmpty(address)) {
            return ReturnResult.illegal("地址不能为空");
        }

        // 如果地址包含端口号，只取IP部分
        if (address.contains(":")) {
            address = address.split(":")[0];
        }

        // 延迟初始化IP位置解析器
        if (!isLoaded) {
            synchronized (this) {
                if (!isLoaded) {
                    isLoaded = true;
                    GeoSetting geoSetting = GeoSetting.builder()
                            .databaseFile(ipProperties.getDatabaseFile())
                            .build();
                    ipPosition = ServiceProvider.of(IpPosition.class).getNewExtension(ipProperties.getIpType(), geoSetting);
                    ipPosition.afterPropertiesSet();
                }
            }
        }

        if (null == ipPosition) {
            return ReturnResult.illegal("解析失败");
        }

        return ReturnResult.ok(ipPosition.getCity(address));
    }
}

