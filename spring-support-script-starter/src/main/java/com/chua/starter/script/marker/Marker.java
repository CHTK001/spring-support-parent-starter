package com.chua.starter.script.marker;

/**
 * 编译器
 *
 * @author CH
 */
public interface Marker {
    /**
     * 编译器
     *
     * @param suffix 类型
     * @return 编译器
     */
    static Marker create(String suffix) {
        return new DelegateMarker(suffix).getMarker();
    }

    /**
     * bean
     *
     * @param source 源码
     * @return bean
     */
    Object getBean(String source);
}
