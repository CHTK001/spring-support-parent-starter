package com.chua.starter.protocol.support.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotation.Spi;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.network.protocol.filter.Socks5ForwardServletFilter;
import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.protocol.support.entity.SystemServerSetting;
import com.chua.starter.protocol.support.entity.SystemServerSettingItem;
import com.chua.starter.protocol.support.service.server.SystemServerSettingItemService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SOCKS5 固定转发过滤器处理器。
  * @author CH
 */
@Spi("socks5forward")
public class Socks5ForwardServletFilterHandler implements ServletFilterHandler {

    private final SystemServerSettingItemService itemService;
    private final ObjectMapper objectMapper;

    public Socks5ForwardServletFilterHandler() {
        this.itemService = SpringBeanUtils.getBean(SystemServerSettingItemService.class);
        this.objectMapper = SpringBeanUtils.getBean(ObjectMapper.class);
    }

    @Override
    public ServletFilter handle(SystemServerSetting setting, ConfigureObjectContext objectContext) {
        Socks5ForwardServletFilter filter = new Socks5ForwardServletFilter();
        filter.upgrade(toConfig(setting));
        return filter;
    }

    @Override
    /**
 * 处理update相关逻辑。
 */
    public void update(ServletFilter filter, SystemServerSetting setting) {
        if (filter instanceof Socks5ForwardServletFilter forwardFilter) {
            forwardFilter.upgrade(toConfig(setting));
        }
    }

    /**
 * 处理toconfig相关逻辑。
 */
    private Map<String, Object> toConfig(SystemServerSetting setting) {
        Map<String, Object> config = readJsonConfig(setting);
        if (!config.isEmpty()) {
            return config;
        }
        return loadSettingItems(setting == null ? null : setting.getSystemServerSettingId());
    }

    /**
 * 处理readjsonconfig相关逻辑。
 */
    private Map<String, Object> readJsonConfig(SystemServerSetting setting) {
        if (setting == null || setting.getSystemServerSettingConfig() == null || setting.getSystemServerSettingConfig().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(setting.getSystemServerSettingConfig(), Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
 * 处理loadsettingitems相关逻辑。
 */
    private Map<String, Object> loadSettingItems(Integer settingId) {
        if (settingId == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        List<SystemServerSettingItem> items = itemService.list(
                Wrappers.<SystemServerSettingItem>lambdaQuery()
                        .eq(SystemServerSettingItem::getSystemServerSettingItemSettingId, settingId)
        );
        for (SystemServerSettingItem item : items) {
            if (item.getSystemServerSettingItemName() == null) {
                continue;
            }
            values.put(item.getSystemServerSettingItemName(), item.getSystemServerSettingItemValue());
        }
        return values;
    }
}
