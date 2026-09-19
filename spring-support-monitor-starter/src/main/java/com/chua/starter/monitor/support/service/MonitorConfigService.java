package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitorConfig;

import java.util.List;
import java.util.Map;

/** @作者 CH */
public interface MonitorConfigService {
    /**
     * findby键。
     * @param key 键
     * @return findby键的结果
     */
    ReturnResult<MsMonitorConfig> findByKey(String key);
    /**
     * 获取字符串。
     * @param key 键
     * @return 获取字符串的结果
     */
    String getString(String key);
    /**
     * 获取字符串。
     * @param key 键
     * @param defaultValue 默认值
     * @return 获取字符串的结果
     */
    String getString(String key, String defaultValue);
    /**
     * 获取int。
     * @param key 键
     * @param defaultValue 默认值
     * @return 获取int的结果
     */
    int getInt(String key, int defaultValue);
    /**
     * 获取long。
     * @param key 键
     * @param defaultValue 默认值
     * @return 获取long的结果
     */
    long getLong(String key, long defaultValue);
    /**
     * 获取布尔值。
     * @param key 键
     * @param defaultValue 默认值
     * @return 获取布尔值的结果
     */
    boolean getBoolean(String key, boolean defaultValue);
    /**
     * 获取全部。
     * @return 获取全部的结果
     */
    ReturnResult<Map<String, String>> getAll();
    /**
     * 列表全部。
     * @return 列表全部的结果
     */
    List<MsMonitorConfig> listAll();
    /**
     * upsert。
     * @param entity 实体
     * @return upsert的结果
     */
    ReturnResult<MsMonitorConfig> upsert(MsMonitorConfig entity);

    /**
     * 批量保存配置（页面"保存全部"使用）。
     *
     * @param entities 配置列表
     * @return 保存数量
     */
    ReturnResult<Integer> upsertAll(List<MsMonitorConfig> entities);
}
