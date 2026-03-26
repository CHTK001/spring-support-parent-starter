package com.chua.starter.proxy.support.handler;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.core.annotation.SpiDefault;
import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.network.protocol.server.UpgradeServletFilter;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import com.chua.starter.proxy.support.entity.SystemServerSettingItem;
import com.chua.starter.proxy.support.service.server.SystemServerSettingItemService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认处理器：按 SPI 类型创建对应的 ServletFilter，并应用通用配置项
 *
 * @author CH
 * @since 2025/8/11 17:01
 */
@Spi("default")
@SpiDefault
public class DefaultServletFilterHandler implements ServletFilterHandler {
    final SystemServerSettingItemService systemServerSettingItemService;

    public DefaultServletFilterHandler() {
        this.systemServerSettingItemService = SpringBeanUtils.getBean(SystemServerSettingItemService.class);
    }

    /**
     * 注册地址限流
     *
     * @param setting       设置
     * @param objectContext
     */
    @Override
    public ServletFilter handle(SystemServerSetting setting, ConfigureObjectContext objectContext) {
        ServletFilter servletFilter = ServiceProvider.of(ServletFilter.class).getNewExtension(setting.getSystemServerSettingType(), objectContext);
        if (servletFilter instanceof UpgradeServletFilter upgradeServletFilter) {
            upgradeServletFilter.upgrade(createConfig(setting));
        }
        return servletFilter;
    }

    /**
     * 创建配置
     *
     * @param setting 设置
     * @return {@link Map}<{@link String}, {@link Object}>
     */
    private Map<String, Object> createConfig(SystemServerSetting setting) {
        List<SystemServerSettingItem> list = systemServerSettingItemService.list(
                Wrappers.<SystemServerSettingItem>lambdaQuery()
                        .eq(SystemServerSettingItem::getSystemServerSettingItemSettingId, setting.getSystemServerSettingId())
        );
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        Map<String, Object> config = new HashMap<>(list.size());
        for (SystemServerSettingItem item : list) {
            config.put(item.getSystemServerSettingItemName(), item.getSystemServerSettingItemValue());
        }
        return config;
    }

    @Override
    public void update(ServletFilter filter, SystemServerSetting setting) {

    }

}




