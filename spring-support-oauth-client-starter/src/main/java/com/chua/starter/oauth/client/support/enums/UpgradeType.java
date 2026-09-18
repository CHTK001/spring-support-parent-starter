package com.chua.starter.oauth.client.support.enums;

/**
* upgrade类型 枚举类定义了升级的类型。
* 这个枚举用于指定升级是基于版本号还是基于时间戳。
* @since 2024/5/29 表示该枚举类是从 2024 年 5 月 29 日开始提供的。
* @author CH
 */
public enum UpgradeType {

    /**
    * 表示基于版本号的升级。(升级账号信息)
    */
    VERSION,

    /**
    * 表示基于时间戳的升级。(升级账号信息， 并重置时长)
    */
    TIMESTAMP,


    /**
    * 表示刷新升级。返回新令牌
    */
    REFRESH,

    ;
    /**
    * 根据给定的升级类型字符串，返回对应的 upgrade类型 枚举值。
    * 如果给定的升级类型字符串为 空，则返回 时间戳 枚举值。
    * 否则，遍历 upgrade类型 枚举值，如果找到与给定字符串相等的枚举值，则返回该枚举值。
    * 如果找不到匹配的枚举值，则返回 时间戳 枚举值。
    *
    * @param upgradeType 给定的升级类型字符串
    * @return 对应的 upgrade类型 枚举值
    * @since 2024/5/29
    */
    public static UpgradeType getUpgradeType(String upgradeType) {
        if (upgradeType == null) {
            return TIMESTAMP;
        }
        for (UpgradeType value : UpgradeType.values()) {
            if (value.name().equals(upgradeType)) {
                return value;
            }
        }
        return TIMESTAMP;
    }
}

