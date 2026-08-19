package com.chua.starter.rpc.support.service;

/**
 * RPC 服务设置基类
 *
 * @since 2024/04/08
 */
public abstract class AbstractRpcServiceSetting<T> {

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
}