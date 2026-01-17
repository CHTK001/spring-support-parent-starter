package com.chua.starter.common.support.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 跨域/版本控制/统一响应
 *
 * @author CH
 */
@ConfigurationProperties(prefix = IpProperties.PRE, ignoreInvalidFields = true)
public class IpProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "plugin.ip";


    /**
     * 数据库文件路径
     */
    private String databaseFile = "classpath:qqwry.dat";

    /**
     * ip翻译实现方式
     */
    private String ipType = "qqwry";

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 设置 enable
     *
     * @param enable enable
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 获取 databaseFile
     *
     * @return databaseFile
     */
    public String getDatabaseFile() {
        return databaseFile;
    }

    /**
     * 设置 databaseFile
     *
     * @param databaseFile databaseFile
     */
    public void setDatabaseFile(String databaseFile) {
        this.databaseFile = databaseFile;
    }

    /**
     * 获取 ipType
     *
     * @return ipType
     */
    public String getIpType() {
        return ipType;
    }

    /**
     * 设置 ipType
     *
     * @param ipType ipType
     */
    public void setIpType(String ipType) {
        this.ipType = ipType;
    }
}
