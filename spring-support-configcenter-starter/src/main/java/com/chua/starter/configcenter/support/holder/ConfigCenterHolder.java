package com.chua.starter.configcenter.support.holder;

import com.chua.common.support.config.center.ConfigCenter;
import lombok.extern.slf4j.Slf4j;

/**
* 配置center 持有者
* <p>
* 统一管理 配置center 实例，确保整个应用使用同一个实例。
* 解决 环境post处理器 和 auto配置 创建不同实例的问题。
* </p>
*
* @since 2024/12/07
* @author CH
 */
@Slf4j
public class ConfigCenterHolder {

    /**
    * 全局 配置center 实例
     */
    private static volatile ConfigCenter instance;

    /**
    * 设置 配置center 实例
    *
    * @param configCenter 配置中心实例
     */
    public static void setInstance(ConfigCenter configCenter) {
        if (instance == null) {
            synchronized (ConfigCenterHolder.class) {
                if (instance == null) {
                    instance = configCenter;
                    log.info("[配置中心]ConfigCenter 实例已设置: {}", 
                            configCenter != null ? configCenter.getClass().getSimpleName() : "null");
                }
            }
        }
    }

    /**
    * 获取 配置center 实例
    *
    * @return 配置中心实例，可能为 空
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
    * 关闭 配置center
     */
    public static void shutdown() {
        if (instance != null) {
            try {
                instance.close();
                log.info("[配置中心]ConfigCenter 已关闭");
            } catch (Exception e) {
                log.error("[配置中心]关闭 ConfigCenter 失败", e);
            }
            instance = null;
        }
    }
}
