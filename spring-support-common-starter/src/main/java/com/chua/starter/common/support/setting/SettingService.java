package com.chua.starter.common.support.setting;

import java.util.List;

/**
 * 设置服务
 *
 * @author CH
 */
public interface SettingService {
    /**
     * 列表
     *
     * @return {@link List}<{@link SettingItem}>
     */
    List<SettingItem> list();
}
