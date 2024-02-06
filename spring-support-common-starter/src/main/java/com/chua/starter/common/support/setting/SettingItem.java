package com.chua.starter.common.support.setting;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 设置项目
 *
 * @author CH
 */
@Data
@AllArgsConstructor
public class SettingItem {
    private String name;

    private Object value;

    private String desc;

}
