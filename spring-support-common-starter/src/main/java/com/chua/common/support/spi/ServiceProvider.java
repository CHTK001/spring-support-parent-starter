package com.chua.common.support.spi;

import java.util.*;

/**
 * SPI 服务提供者类，管理 SPI 扩展点的加载和实例化
 *
 * @param <T> 服务类型
 * @author CH
 */
public class ServiceProvider<T> {

    /**
     * 服务类型
     */
    private final Class<T> type;

    /**
     * 服务名称
     */
    private final String name;

    /**
     * 全局缓存，用于缓存已创建的各类型 ServiceProvider 实例
     */
    private static final Map<Object, ServiceProvider<?>> CACHE = new HashMap<>();

    /**
     * 构造 ServiceProvider 实例
     *
     * @param type 服务类型
     * @param name 服务名称
     */
    public ServiceProvider(Class<T> type, String name) {
        this.type = type;
        this.name = name;
    }

    /**
     * 根据服务类型获取或创建 ServiceProvider 实例（优先从缓存获取）
     *
     * @param <T>  服务类型泛型
     * @param type 服务类型的 Class 对象
     * @return ServiceProvider 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> ServiceProvider<T> of(Class<T> type) {
        return (ServiceProvider<T>) CACHE.computeIfAbsent(type, k -> new ServiceProvider<>(type, null));
    }

    /**
     * 根据服务名称获取或创建 ServiceProvider 实例（优先从缓存获取）
     *
     * @param <T>  服务类型泛型
     * @param name 服务名称
     * @return ServiceProvider 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> ServiceProvider<T> of(String name) {
        return (ServiceProvider<T>) CACHE.computeIfAbsent(name, k -> new ServiceProvider<>(null, name));
    }

    /**
     * 获取该 SPI 的所有扩展选项列表
     *
     * @return 扩展选项列表，当前实现返回空集合
     */
    public List<SpiOption> options() {
        return Collections.emptyList();
    }

    /**
     * 获取该 SPI 所有已注册的扩展实例（名称 -> 实例）
     *
     * @return 名称到扩展实例的映射，当前实现返回空映射
     */
    public Map<String, T> list() {
        return Collections.emptyMap();
    }

    /**
     * 根据名称获取 SPI 扩展实例（单例）
     *
     * @param name 扩展名称
     * @return 扩展实例，未找到时返回 null
     */
    public T getExtension(String name) {
        return null;
    }

    /**
     * 获取默认 SPI 扩展实例（无名称）
     *
     * @return 扩展实例，未找到时返回 null
     */
    public T getExtension() {
        return null;
    }

    /**
     * 根据名称获取新的 SPI 扩展实例（每次调用创建新实例）
     *
     * @param name 扩展名称
     * @param args 构造参数
     * @return 新的扩展实例，未找到时返回 null
     */
    public T getNewExtension(String name, Object... args) {
        return null;
    }

    /**
     * 初始化回调，在属性设置完成后执行自定义初始化逻辑
     * <p>当前实现为空，供子类覆写使用。</p>
     */
    public void afterPropertiesSet() {
    }
}
