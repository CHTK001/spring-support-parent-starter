package com.chua.starter.common.support.configuration.resolver;

import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.annotations.RequestParamMapping;
import com.chua.starter.common.support.filter.CustomHttpServletRequestWrapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.support.MultipartResolutionDelegate;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author CH
 */
public class RequestParamsMapMethodArgumentResolver extends RequestParamMapMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        RequestParamMapping requestParam = parameter.getParameterAnnotation(RequestParamMapping.class);
        boolean has = (requestParam != null && Map.class.isAssignableFrom(parameter.getParameterType()) &&
                !ArrayUtils.isEmpty(requestParam.name()));
        if (has) {
            return true;
        }

        Class<?> type = parameter.getParameterType();
        if (!ClassUtils.isJavaType(type)) {
            List<Field> declaredFields = ClassUtils.getFields(type);
            for (Field declaredField : declaredFields) {
                if (!Modifier.isStatic(declaredField.getModifiers()) && declaredField.isAnnotationPresent(RequestParamMapping.class)) {
                    return true;
                }
            }
        }

        return parameter.hasParameterAnnotation(RequestParamMapping.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

        if (parameter.hasParameterAnnotation(RequestParamMapping.class)) {
            return resolveArgumentOne(parameter, mavContainer, webRequest, binderFactory);
        }
        return resolveArgumentObject(parameter, mavContainer, webRequest, binderFactory);
    }

    private Object resolveArgumentObject(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        Object argument = super.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
        Parameter parameter1 = parameter.getParameter();
        Class<?> type = parameter1.getType();
        Object instance = type.newInstance();
        ReflectionUtils.doWithFields(type, field -> {
            if (Modifier.isStatic(field.getModifiers())) {
                return;
            }
            ReflectionUtils.makeAccessible(field);
            doRegister(instance, field, argument);
        });

        return instance;
    }

    @SuppressWarnings("ALL")
    private void doRegister(Object instance, Field field, Object argument) {
        String name = field.getName();
        Object value = null;
        Map<String, Object> beanMap = Collections.emptyMap();
        if (argument instanceof Map map) {
            beanMap = (Map<String, Object>) map;
        } else {
            beanMap = BeanMap.of(argument, false);
        }
        value = beanMap.get(name);
        if (null == value) {
            if (field.isAnnotationPresent(RequestParamMapping.class)) {
                doRequestParamMappingRegister(instance, field, AnnotationUtils.getAnnotation(field, RequestParamMapping.class), beanMap);
                return;
            }
            return;
        }
        if (field.isAnnotationPresent(RequestParamMapping.class)) {
            doRequestParamMappingRegister(instance, field, AnnotationUtils.getAnnotation(field, RequestParamMapping.class), beanMap);
            return;
        }

        set(instance, field, value);
    }

    private void doRequestParamMappingRegister(Object instance, Field field, RequestParamMapping requestParamMapping, Map<String, Object> beanMap) {
        Map<String, Object> annotationAttributes = AnnotationUtils.getAnnotationAttributes(requestParamMapping);
        String defaultValue = requestParamMapping.defaultValue();
        String[] name = (String[]) annotationAttributes.getOrDefault("name", new String[0]);
        Object value = null;
        for (String s : name) {
            Object o = beanMap.get(s);
            if (null != o) {
                value = o;
                break;
            }
        }

        if (null == value) {
            value = defaultValue;
        }
        set(instance, field, value);
    }

    private void set(Object instance, Field field, Object o) {
        if (null == o) {
            return;
        }

        if (ValueConstants.DEFAULT_NONE.equals(o)) {
            return;
        }

        Object o1 = Converter.convertIfNecessary(o, field.getType());
        if (null == o1) {
            return;
        }
        try {
            field.set(instance, o1);
        } catch (IllegalAccessException ignored) {
        }
    }


    public Object resolveArgumentOne(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
                                     NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws ServletException, IOException {
        ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);

        if (MultiValueMap.class.isAssignableFrom(parameter.getParameterType())) {
            // MultiValueMap
            Class<?> valueType = resolvableType.as(MultiValueMap.class).getGeneric(1).resolve();
            if (valueType == MultipartFile.class) {
                MultipartRequest multipartRequest = MultipartResolutionDelegate.resolveMultipartRequest(webRequest);
                return (multipartRequest != null ? multipartRequest.getMultiFileMap() : new LinkedMultiValueMap<>(0));
            } else if (valueType == Part.class) {
                HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
                if (servletRequest != null && MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
                    Collection<Part> parts = servletRequest.getParts();
                    LinkedMultiValueMap<String, Part> result = new LinkedMultiValueMap<>(parts.size());
                    for (Part part : parts) {
                        result.add(part.getName(), part);
                    }
                    return result;
                }
                return new LinkedMultiValueMap<>(0);
            } else {
                Map<String, String[]> parameterMap = webRequest.getParameterMap();
                MultiValueMap<String, String> result = new LinkedMultiValueMap<>(parameterMap.size());
                parameterMap.forEach((key, values) -> {
                    for (String value : values) {
                        result.add(key, value);
                    }
                });
                return result;
            }
        } else {
            // Regular Map
            Class<?> valueType = resolvableType.asMap().getGeneric(1).resolve();
            if (valueType == MultipartFile.class) {
                MultipartRequest multipartRequest = MultipartResolutionDelegate.resolveMultipartRequest(webRequest);
                return (multipartRequest != null ? multipartRequest.getFileMap() : new LinkedHashMap<>(0));
            } else if (valueType == Part.class) {
                HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
                if (servletRequest != null && MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
                    Collection<Part> parts = servletRequest.getParts();
                    LinkedHashMap<String, Part> result = CollectionUtils.newLinkedHashMap(parts.size());
                    for (Part part : parts) {
                        if (!result.containsKey(part.getName())) {
                            result.put(part.getName(), part);
                        }
                    }
                    return result;
                }
                return new LinkedHashMap<>(0);
            } else {
                Map<String, String[]> parameterMap = webRequest.getParameterMap();
                Map<String, String> result = CollectionUtils.newLinkedHashMap(parameterMap.size());
                parameterMap.forEach((key, values) -> {
                    if (values.length > 0) {
                        result.put(key, values[0]);
                    }
                });
                if (result.isEmpty()) {
                    Object nativeRequest = webRequest.getNativeRequest();
                    if (nativeRequest instanceof CustomHttpServletRequestWrapper) {
                        String s = IoUtils.toString(((CustomHttpServletRequestWrapper) nativeRequest).getInputStream(), StandardCharsets.UTF_8);
                        if (s.startsWith(CommonConstant.SYMBOL_LEFT_BIG_PARENTHESES)) {
                            return Json.toMapStringObject(s);
                        }

                        if (s.startsWith(CommonConstant.SYMBOL_LEFT_SQUARE_BRACKET)) {
                            return result;
                        }

                        return MapUtils.asMap(s, "&", "=");
                    }

                    if(nativeRequest instanceof ContentCachingRequestWrapper contentCachingRequestWrapper) {
                        String s = IoUtils.toString(contentCachingRequestWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
                        if (s.startsWith(CommonConstant.SYMBOL_LEFT_BIG_PARENTHESES)) {
                            return Json.toMapStringObject(s);
                        }

                        if (s.startsWith(CommonConstant.SYMBOL_LEFT_SQUARE_BRACKET)) {
                            return result;
                        }

                        return MapUtils.asMap(s, "&", "=");
                    }
                }
                return result;
            }
        }
    }
}
