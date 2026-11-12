package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerConfig;

import java.util.List;
import java.util.Map;

/**
 * 服务器系统配置服务
 * @author CH
 * @since 4.0.0
 */
public interface ServerConfigService {

    /**
     * 查询全部配置
     *
     * @return 配置列表
     */
    ReturnResult<List<MsServerConfig>> listAll();

    /**
     * 读取全部配置为扁平 映射（缺省合并默认值）
     *
     * @return key -> 值 映射
     */
    ReturnResult<Map<String, Object>> getConfigMap();

    /**
     * 保存配置（批量 upsert）
     *
     * @param map 配置键值
     * @return 结果
     */
    ReturnResult<Void> saveConfig(Map<String, Object> map);

    /**
     * 读取单个配置值（带默认）
     *
     * @param key      配置键
     * @param defValue 默认值
     * @return 配置值
     */
    String getString(String key, String defValue);

    /**
     * 读取布尔配置
     *
     * @param key      配置键
     * @param defValue 默认值
     * @return 布尔值
     */
    boolean getBoolean(String key, boolean defValue);

    /**
     * 读取整数配置
     *
     * @param key      配置键
     * @param defValue 默认值
     * @return 整数值
     */
    int getInt(String key, int defValue);

    /**
     * 删除单个配置（键不存在时视为成功，幂等）。
     *
     * <p>只删一行、不广播配置变更事件：调用方删的是「终端配色预设」这类
     * 与定时任务/连接参数无关的键，没有监听器需要据此重排。</p>
     *
     * @param key 配置键
     * @return 结果
     */
    ReturnResult<Void> deleteConfig(String key);
}
