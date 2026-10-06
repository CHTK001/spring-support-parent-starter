package com.chua.starter.datasourcehub.support.spi;

/**
 * Nginx 系统配置读取 SPI（可选依赖）。
 *
 * <p>原实现直接依赖 server-starter 的 {@code ServerConfigService#getString}，
 * 为避免 hub 引入 server-starter 重依赖，此处定义最小读取契约：
 * 仅需 {@code getString(key, defValue)}。未装配实现时降级为默认值，
 * 不影响其它功能。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface NginxConfigLookup {

    /**
     * 读取单个配置值（带默认）。
     *
     * @param key      配置键
     * @param defValue 默认值
     * @return 配置值
     */
    String getString(String key, String defValue);
}
