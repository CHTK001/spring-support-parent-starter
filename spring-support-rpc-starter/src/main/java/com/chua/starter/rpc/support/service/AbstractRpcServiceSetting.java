package com.chua.starter.rpc.support.service;

/**
 * RPC 服务设置基类
 *
 * @since 2024/04/08
 * @author CH
 */
public abstract class AbstractRpcServiceSetting<T> {

    /**
     * 服务唯一标识
     */
    private String id;

    /**
     * 接口名称
     */
    private String interfaceName;

    /**
     * 接口类
     */
    private Class<?> interfaceClass;

    /**
     * 引用对象
     */
    private T ref;

    /**
     * 服务版本号
     */
    private String version;

    /**
     * 服务分组
     */
    private String group;

    /**
     * 服务安全令牌
     */
    private String token;

    /**
     * 延迟暴露时间（毫秒）
     */
    private int delay;

    /**
     * 服务端重试次数
     */
    private int retry;

    /**
     * 是否异步执行
     */
    private boolean async;

    /**
     * 服务端调用超时（毫秒）
     */
    private int timeout;

    /**
     * 缓存策略名称
     */
    private String cache;

    /**
     * 获取服务唯一标识
     *
     * @return 服务唯一标识
     */
    public String getId() {
        return id;
    }

    /**
     * 设置服务唯一标识
     *
     * @param id 服务唯一标识
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取接口名称
     *
     * @return 接口名称
     */
    public String getInterfaceName() {
        return interfaceName;
    }

    /**
     * 设置接口名称
     *
     * @param interfaceName 接口名称
     */
    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    /**
     * 获取接口类
     *
     * @return 接口类
     */
    public Class<?> getInterfaceClass() {
        return interfaceClass;
    }

    /**
     * 设置接口类
     *
     * @param interfaceClass 接口类
     */
    public void setInterfaceClass(Class<?> interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    /**
     * 获取引用对象
     *
     * @return 引用对象
     */
    public T getRef() {
        return ref;
    }

    /**
     * 设置引用对象
     *
     * @param ref 引用对象
     */
    public void setRef(T ref) {
        this.ref = ref;
    }

    /**
     * 获取服务版本号
     *
     * @return 服务版本号
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置服务版本号
     *
     * @param version 服务版本号
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 获取服务分组
     *
     * @return 服务分组
     */
    public String getGroup() {
        return group;
    }

    /**
     * 设置服务分组
     *
     * @param group 服务分组
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * 获取服务安全令牌
     *
     * @return 服务安全令牌
     */
    public String getToken() {
        return token;
    }

    /**
     * 设置服务安全令牌
     *
     * @param token 服务安全令牌
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * 获取延迟暴露时间（毫秒）
     *
     * @return 延迟暴露时间
     */
    public int getDelay() {
        return delay;
    }

    /**
     * 设置延迟暴露时间（毫秒）
     *
     * @param delay 延迟暴露时间
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    /**
     * 获取服务端重试次数
     *
     * @return 重试次数
     */
    public int getRetry() {
        return retry;
    }

    /**
     * 设置服务端重试次数
     *
     * @param retry 重试次数
     */
    public void setRetry(int retry) {
        this.retry = retry;
    }

    /**
     * 获取是否异步执行
     *
     * @return 是否异步执行
     */
    public boolean isAsync() {
        return async;
    }

    /**
     * 设置是否异步执行
     *
     * @param async 是否异步执行
     */
    public void setAsync(boolean async) {
        this.async = async;
    }

    /**
     * 获取服务端调用超时（毫秒）
     *
     * @return 调用超时
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * 设置服务端调用超时（毫秒）
     *
     * @param timeout 调用超时
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * 获取缓存策略名称
     *
     * @return 缓存策略名称
     */
    public String getCache() {
        return cache;
    }

    /**
     * 设置缓存策略名称
     *
     * @param cache 缓存策略名称
     */
    public void setCache(String cache) {
        this.cache = cache;
    }
}