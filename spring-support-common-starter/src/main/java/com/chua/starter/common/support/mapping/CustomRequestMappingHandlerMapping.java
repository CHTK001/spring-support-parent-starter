package com.chua.starter.common.support.mapping;

import com.chua.common.support.eventbus.EventbusType;
import com.chua.starter.common.support.eventbus.EventbusTemplate;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 自定义
 *
 * @author CH
 */
public class CustomRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private final EventbusTemplate eventbusTemplate;

    public static final String SUBSCRIBE = "MAPPING";

    private List<Mapping> mappingList = new LinkedList<>();

    public CustomRequestMappingHandlerMapping(EventbusTemplate eventbusTemplate) {
        this.eventbusTemplate = eventbusTemplate;
    }

    @Override
    protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
        RequestMappingInfo mappingForMethod = super.getMappingForMethod(method, handlerType);
        Mapping mapping = new Mapping(method, handlerType, mappingForMethod);
        this.mappingList.add(mapping);
        eventbusTemplate.post(EventbusType.GUAVA, SUBSCRIBE, mapping);
        return mappingForMethod;
    }

    public void forEach(Consumer<Mapping> mappingConsumer) {
        mappingList.forEach(mappingConsumer);
    }
}
