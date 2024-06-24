package com.chua.starter.common.support.interceptor.address;

/**
 * AddressRecorder接口用于定义地址记录行为。
 *
 * 该接口的主要作用是提供一个标准的方法来记录特定URL对应的地址信息。
 * 实现这个接口的类需要提供具体的方法来执行地址记录操作。
 *
 * @author CH
 * @since 2024/6/21
 */
public interface AddressRecorder {

    /**
     * 记录给定URL对应的地址。
     *
     * 此方法用于将一个URL和其对应的地址信息关联起来。具体的关联方式由实现类决定。
     *
     * @param url 需要记录地址的URL字符串。
     * @param address 与URL对应的地址字符串。
     * @return 如果地址记录成功，则返回true；否则返回false。
     */
    boolean record(String url, String address);

    /**
     * 默认的AddressRecorder实现类。
     *
     * 此类实现了AddressRecorder接口，但实际功能是空的。
     * 它主要用于在需要记录地址信息的场景下，提供默认的空实现。
     *
     * 如果没有其他实现类实现了AddressRecorder接口，则默认使用此实现类。
     */
    class DefaultAddressRecorder implements AddressRecorder {
        @Override
        public boolean record(String url, String address) {
            return false;
        }
    }
}
