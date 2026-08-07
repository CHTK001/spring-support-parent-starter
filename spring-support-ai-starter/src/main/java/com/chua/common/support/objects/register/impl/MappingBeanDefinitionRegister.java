package com.chua.common.support.objects.register.impl;

import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.protocol.request.ServletRequest;
import com.chua.common.support.network.protocol.server.ServletHandlerMappingDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * 映射 Bean 定义注册器。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class MappingBeanDefinitionRegister {

    /**
     * 处理器映射集合
     */
    private final List<ServletHandlerMappingDefinition> mappings = new ArrayList<>();

    /**
     * 注册处理器映射。
     *
     * @param definition 处理器映射定义
     */
    public void registerMapping(ServletHandlerMappingDefinition definition) {
        mappings.add(definition);
    }

    /**
     * 获取全部处理器映射。
     *
     * @return 处理器映射列表
     */
    public List<ServletHandlerMappingDefinition> getMappings() {
        return mappings;
    }
}
