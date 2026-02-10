package com.chua.starter.sync.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SPI 配置适配器管理器
 * 集中管理所有 SPI 配置适配器
 *
 * @author CH
 * @since 2024/12/21
 */
@Slf4j
@Component
public class SpiConfigAdapterManager {

    /**
     * 适配器映射: type#name -> adapter
     */
    private final Map<String, SpiConfigAdapter<?>> adapterMap = new HashMap<>();

    @Autowired(required = false)
    private List<SpiConfigAdapter<?>> adapters;

    @PostConstruct
    public void init() {
        if (adapters != null) {
            for (SpiConfigAdapter<?> adapter : adapters) {
                String key = buildKey(adapter.getSpiType(), adapter.getSpiName());
                adapterMap.put(key, adapter);
                if (log.isDebugEnabled()) {
                    log.debug("注册 SPI 配置适配器: {} -> {}", key, adapter.getClass().getSimpleName());
                }
            }
        }
        log.info("SPI 配置适配器管理器初始化完成，共注册 {} 个适配器", adapterMap.size());
    }

    /**
     * 获取适配器
     *
     * @param spiType SPI 类型
     * @param spiName SPI 名称
     * @return 适配器实例
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<SpiConfigAdapter<T>> getAdapter(String spiType, String spiName) {
        String key = buildKey(spiType, spiName);
        SpiConfigAdapter<?> adapter = adapterMap.get(key);
        return Optional.ofNullable((SpiConfigAdapter<T>) adapter);
    }

    /**
     * 判断是否存在适配器
     *
     * @param spiType SPI 类型
     * @param spiName SPI 名称
     * @return 是否存在
     */
    public boolean hasAdapter(String spiType, String spiName) {
        return adapterMap.containsKey(buildKey(spiType, spiName));
    }

    /**
     * 创建 SPI 实例
     *
     * @param spiType SPI 类型
     * @param spiName SPI 名称
     * @param config  配置参数
     * @return SPI 实例，如果没有适配器则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T create(String spiType, String spiName, Map<String, Object> config) {
        Optional<SpiConfigAdapter<T>> adapter = getAdapter(spiType, spiName);
        if (adapter.isPresent()) {
            return adapter.get().create(config);
        }
        log.warn("未找到 SPI 配置适配器: type={}, name={}", spiType, spiName);
        return null;
    }

    /**
     * 测试配置
     *
     * @param spiType SPI 类型
     * @param spiName SPI 名称
     * @param config  配置参数
     * @return 测试结果
     */
    public String test(String spiType, String spiName, Map<String, Object> config) {
        String key = buildKey(spiType, spiName);
        SpiConfigAdapter<?> adapter = adapterMap.get(key);
        if (adapter != null) {
            return adapter.test(config);
        }
        return "未找到对应的配置适配器";
    }

    /**
     * 验证配置
     *
     * @param spiType SPI 类型
     * @param spiName SPI 名称
     * @param config  配置参数
     * @return 验证错误信息，成功返回 null
     */
    public String validate(String spiType, String spiName, Map<String, Object> config) {
        String key = buildKey(spiType, spiName);
        SpiConfigAdapter<?> adapter = adapterMap.get(key);
        if (adapter != null) {
            return adapter.validate(config);
        }
        return null;
    }

    /**
     * 构建缓存键
     */
    private String buildKey(String spiType, String spiName) {
        return (spiType != null ? spiType.toUpperCase() : "") + "#" +
                (spiName != null ? spiName.toLowerCase() : "");
    }
}
