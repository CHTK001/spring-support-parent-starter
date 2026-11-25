package com.chua.starter.common.support.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IP 归属地解析配置属性。
 *
 * <p>前缀 {@code plugin.ip}。用于 IP 地理位置查询（归属地翻译）的离线库配置，
 * 供日志、登录审计等场景将 IP 翻译为地区信息。</p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * plugin:
 *   ip:
 *     enable: false                        # 是否启用（默认 false）
 *     database-file: classpath:qqwry.dat   # 离线数据库文件路径（默认 classpath:qqwry.dat）
 *     ip-type: ip2region                   # 解析实现（默认 ip2region）
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@ConfigurationProperties(prefix = IpProperties.PRE, ignoreInvalidFields = true)
public class IpProperties {
    /**
     * 是否启用 IP 归属地解析。
     * <p>YAML：{@code plugin.ip.enable}，默认 {@code false}。</p>
     */
    private boolean enable = false;


    /**
     * PRE
    */
    public static final String PRE = "plugin.ip";


    /**
     * 离线数据库文件路径。
     * <p>YAML：{@code plugin.ip.database-file}，默认 {@code classpath:qqwry.dat}。</p>
     */
    private String databaseFile = "classpath:qqwry.dat";

    /**
     * IP 归属地解析实现方式。
     * <p>YAML：{@code plugin.ip.ip-type}，默认 {@code ip2region}。</p>
     */
    private String ipType = "ip2region";

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
     * 获取 database文件
     *
     * @return databaseFile
     */
    public String getDatabaseFile() {
        return databaseFile;
    }

    /**
     * 设置 database文件
     *
     * @param databaseFile database文件
     */
    public void setDatabaseFile(String databaseFile) {
        this.databaseFile = databaseFile;
    }

    /**
     * 获取 ip类型
     *
     * @return ipType
     */
    public String getIpType() {
        return ipType;
    }

    /**
     * 设置 ip类型
     *
     * @param ipType ip类型
     */
    public void setIpType(String ipType) {
        this.ipType = ipType;
    }
}
