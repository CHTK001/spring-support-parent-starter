package com.chua.common.support.objects.definition;

/**
 * Bean 定义接口，描述 Bean 的元数据信息
 *
 * @author CH
 */
public interface BeanDefinition {
    /**
     * 获取 Bean 的名称
     *
     * @return Bean 名称
     */
    String getName();

    /**
     * 获取 Bean 的类型
     *
     * @return Bean 的 Class 对象
     */
    Class<?> getType();
}
