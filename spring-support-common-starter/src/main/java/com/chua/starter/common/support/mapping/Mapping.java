package com.chua.starter.common.support.mapping;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.reflect.Method;

/**
 * mapping
 *
 * @author CH
 */
@Data
@AllArgsConstructor
public class Mapping {
    /**
     * 方法
     */
    private Method method;
    /**
     * 类型
     */
    private Class<?> handlerType;
    /**
     * 请求信息
     */
    private RequestMappingInfo requestMappingInfo;

}
