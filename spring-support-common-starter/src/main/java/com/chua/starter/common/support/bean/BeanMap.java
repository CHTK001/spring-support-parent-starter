package com.chua.starter.common.support.bean;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.util.Collections;
import java.util.Map;

/**
* Bean 转 映射 工具。
* @author CH
* @since 4.0.0
 */
public final class BeanMap {

    /** 创建 Bean映射 实例 */
    private BeanMap() {
    }

    @SuppressWarnings("unchecked")
    /**
    * 的
    *
    * @param bean Bean
    * @param ignoreNull ignore空
    * @return 的的结果
    */
    public static Map<String, Object> of(Object bean, boolean ignoreNull) {
        if (bean == null) {
            return Collections.emptyMap();
        }
        BeanWrapper wrapper = new BeanWrapperImpl(bean);
        java.beans.PropertyDescriptor[] descriptors = wrapper.getPropertyDescriptors();
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (java.beans.PropertyDescriptor pd : descriptors) {
            String name = pd.getName();
            if ("class".equals(name)) {
                continue;
            }
            Object value = wrapper.getPropertyValue(name);
            if (ignoreNull && value == null) {
                continue;
            }
            map.put(name, value);
        }
        return map;
    }
}

