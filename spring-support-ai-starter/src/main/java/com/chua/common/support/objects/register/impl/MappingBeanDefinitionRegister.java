package com.chua.common.support.objects.register.impl;

import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.protocol.request.ServletRequest;
import com.chua.common.support.network.protocol.server.ServletHandlerMappingDefinition;

import java.util.ArrayList;
import java.util.List;

public class MappingBeanDefinitionRegister {

    private final List<ServletHandlerMappingDefinition> mappings = new ArrayList<>();

    public void registerMapping(ServletHandlerMappingDefinition definition) {
        mappings.add(definition);
    }

    public List<ServletHandlerMappingDefinition> getMappings() {
        return mappings;
    }
}
