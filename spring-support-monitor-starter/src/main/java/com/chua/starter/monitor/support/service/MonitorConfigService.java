package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.entity.MsMonitorConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 监控全局配置 Service
 *
 * @author CH
 * @since 2026/8/4
 */
public interface MonitorConfigService {

    /**
     * 通过 key 查询
     *
     * @param key 配置项
     * @return Optional
     */
    Optional<MsMonitorConfig> findByKey(String key);

    /**
     * 通过 key 获取字符串值(不存在返回空)
     *
     * @param key 配置项
     * @return 值
     */
    String getString(String key);

    /**
     * 通过 key 获取字符串值(带默认值)
     *
     * @param key          配置项
     * @param defaultValue 默认值
     * @return 值
     */
    String getString(String key, String defaultValue);

    /**
     * 通过 key 获取整数(带默认值)
     *
     * @param key          配置项
     * @param defaultValue 默认值
     * @return 值
     */
    int getInt(String key, int defaultValue);

    /**
     * 通过 key 获取长整数(带默认值)
     *
     * @param key          配置项
     * @param defaultValue 默认值
     * @return 值
     */
    long getLong(String key, long defaultValue);

    /**
     * 通过 key 获取布尔(带默认值)
     *
     * @param key          配置项
     * @param defaultValue 默认值
     * @return 值
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * 查询全部配置
     *
     * @return 全量 Map
     */
    Map<String, String> getAll();

    /**
     * 查询全部实体
     *
     * @return 列表
     */
    List<MsMonitorConfig> listAll();

    /**
     * 写入或更新
     *
     * @param entity 配置实体
     * @return 保存后的实体
     */
    MsMonitorConfig upsert(MsMonitorConfig entity);
}