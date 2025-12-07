package com.chua.starter.configcenter.support.holder;

import com.chua.common.support.config.ConfigCenter;
import lombok.extern.slf4j.Slf4j;

/**
 * ConfigCenter 持有者
 * <p>
 * 统一管理 ConfigCenter 实例，确保整个应用使用同一个实例。
 * 解决 EnvironmentPostProcessor 和 AutoConfiguration 创建不同实例的问题。
 * </p>
 *
 * @author CH
 * @since 2024/12/07
 */
@Slf4j
public class ConfigCenterHolder {

    /**
     * 全局 ConfigCenter 实例
     */
    private static volatile ConfigCenter instance;

    /**
     * 设置 ConfigCenter 实例
     *
     * @param configCenter 配置中心实例
     */
    public static void setInstance(ConfigCenter configCenter) {
        if (instance == null) {
            synchronized (ConfigCenterHolder.class) {
                if (instance == null) {
                    instance = configCenter;
                    log.info("【配置中心】ConfigCenter 实例已设置: {}", 
                            configCenter != null ? configCenter.getClass().getSimpleName() : "null");
                }
            }
        }
    }

    /**
     * 获取 ConfigCenter 实例
     *
     * @return 配置中心实例，可能为 null
     */
    public static ConfigCenter getInstance() {
        return instance;
    }

    /**
     * 检查是否已初始化
     *
     * @return true-已初始化
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * 检查是否支持监听
     *
     * @return true-支持监听
     */
    public static boolean isSupportListener() {
        return instance != null && instance.isSupportListener();
    }

    /**
     * 关闭 ConfigCenter
     */
    public static void shutdown() {
        if (instance != null) {
            try {
                instance.close();
                log.info("【配置中心】ConfigCenter 已关闭");
            } catch (Exception e) {
                log.error("【配置中心】关闭 ConfigCenter 失败", e);
            }
            instance = null;
        }
    }
}
