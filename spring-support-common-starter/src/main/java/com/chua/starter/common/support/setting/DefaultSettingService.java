package com.chua.starter.common.support.setting;

import com.chua.common.support.config.ConfigItem;
import com.chua.common.support.config.ConfigProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 设置服务
 *
 * @author CH
 */
public class DefaultSettingService implements SettingService{
    @Override
    public List<SettingItem> list() {
        ConfigProvider configProvider = ConfigProvider.of("application-setting");
        List<SettingItem> rs = new ArrayList<>();
        Set<String> strings = configProvider.listSetting();
        for (String string : strings) {
            ConfigItem configItem = configProvider.getConfigItem(string);
            rs.add(new SettingItem(string, configItem.getValue(), configItem.getDesc()));
        }
        return rs;
    }
}
