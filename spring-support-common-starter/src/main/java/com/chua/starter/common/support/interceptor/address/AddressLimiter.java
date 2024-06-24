package com.chua.starter.common.support.interceptor.address;

/**
 * 地址限制器接口。
 * 该接口定义了检查特定地址是否被允许访问的方法。
 * 实现这个接口的类需要提供具体的逻辑来判断地址是否被允许。
 * @author CH
 * @since 2024/6/21
 */
public interface AddressLimiter {

    /**
     * 检查给定的地址是否被允许访问。
     *
     * @param address 要检查的地址，通常是一个IP地址或者域名。
     * @return 如果地址被允许访问，则返回true；否则返回false。
     */
    boolean isAllow(String address);
}
