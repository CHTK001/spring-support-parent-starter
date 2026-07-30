package com.chua.starter.protocol.support.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotation.Spi;
import com.chua.common.support.network.protocol.filter.FirewallServletFilter;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.protocol.support.entity.SystemServerSetting;
import com.chua.starter.protocol.support.entity.SystemServerSettingItem;
import com.chua.starter.protocol.support.service.server.SystemServerSettingItemService;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Spi("firewall")
public class FirewallServletFilterHandler implements ServletFilterHandler {

    private final SystemServerSettingItemService itemService;

    public FirewallServletFilterHandler() {
        this.itemService = SpringBeanUtils.getBean(SystemServerSettingItemService.class);
    }

    @Override
    public ServletFilter handle(SystemServerSetting setting, ConfigureObjectContext objectContext) {
        FirewallServletFilter filter = new FirewallServletFilter();
        filter.upgradeConfigObject(toConfig(setting));
        return filter;
    }

    @Override
    public void update(ServletFilter filter, SystemServerSetting setting) {
        if (filter instanceof FirewallServletFilter firewallFilter) {
            firewallFilter.upgradeConfigObject(toConfig(setting));
        }
    }

    private FirewallServletFilter.FirewallConfig toConfig(SystemServerSetting setting) {
        FirewallServletFilter.FirewallConfig config = new FirewallServletFilter.FirewallConfig();
        Map<String, String> values = loadSettingItems(setting.getSystemServerSettingId());

        config.setEnabled(toBoolean(values.get("enabled"), true));
        config.setStorageEnabled(toBoolean(values.get("storageEnabled"), true));
        config.setStorageType(toString(values.get("storageType"), "protocol-firewall-storage"));
        config.setRuleCacheSeconds(toLong(values.get("ruleCacheSeconds"), 30L));
        config.setLimiterType(toString(values.get("limiterType"), "token"));
        config.setDefaultRatePerSecond(toDouble(values.get("defaultRatePerSecond"), 200D));
        config.setRateDimension(parseRateDimension(values.get("rateDimension")));
        config.setTempBlacklistSeconds(toLong(values.get("tempBlacklistSeconds"), 60L));
        config.setForwardMode(parseForwardMode(values.get("forwardMode")));
        config.setWhitelistIps(toStringSet(values.get("whitelistIps")));
        config.setBlacklistIps(toStringSet(values.get("blacklistIps")));
        config.setPathRateLimits(toDoubleMap(values.get("pathRateLimits")));
        config.setRedirectMappings(toStringMap(values.get("redirectMappings")));
        config.setProxyPassMappings(toStringMap(values.get("proxyPassMappings")));
        config.setLogEnabled(toBoolean(values.get("logEnabled"), true));
        config.setMetricsEnabled(toBoolean(values.get("metricsEnabled"), true));
        return config;
    }

    private Map<String, String> loadSettingItems(Integer settingId) {
        Map<String, String> values = new LinkedHashMap<>();
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

    private String toString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private boolean toBoolean(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private long toLong(String value, long fallback) {
        try {
            return value == null || value.trim().isEmpty() ? fallback : Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double toDouble(String value, double fallback) {
        try {
            return value == null || value.trim().isEmpty() ? fallback : Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private FirewallServletFilter.RateDimension parseRateDimension(String value) {
        try {
            return value == null || value.isBlank()
                    ? FirewallServletFilter.RateDimension.IP_PATH
                    : FirewallServletFilter.RateDimension.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return FirewallServletFilter.RateDimension.IP_PATH;
        }
    }

    private FirewallServletFilter.ForwardMode parseForwardMode(String value) {
        try {
            return value == null || value.isBlank()
                    ? FirewallServletFilter.ForwardMode.ATTRIBUTE
                    : FirewallServletFilter.ForwardMode.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return FirewallServletFilter.ForwardMode.ATTRIBUTE;
        }
    }

    private Set<String> toStringSet(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
          return result;
        }
        String text = raw.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        for (String item : text.split("[,\n\r]")) {
            String value = item.trim().replace("\"", "");
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private Map<String, Double> toDoubleMap(String raw) {
        Map<String, Double> result = new LinkedHashMap<>();
        parseObjectText(raw).forEach((key, value) -> {
            try {
                result.put(key, Double.parseDouble(value));
            } catch (Exception ignored) {
            }
        });
        return result;
    }

    private Map<String, String> toStringMap(String raw) {
        return new LinkedHashMap<>(parseObjectText(raw));
    }

    private Map<String, String> parseObjectText(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String text = raw.trim();
        if (text.startsWith("{") && text.endsWith("}")) {
            text = text.substring(1, text.length() - 1);
        }
        for (String entry : text.split("[,\n\r]")) {
            String line = entry.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] pair = line.split("[:=]", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = pair[0].trim().replace("\"", "");
            String value = pair[1].trim().replace("\"", "");
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }
}
