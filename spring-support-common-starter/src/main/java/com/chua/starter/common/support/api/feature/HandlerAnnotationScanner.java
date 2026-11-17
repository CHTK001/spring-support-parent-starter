package com.chua.starter.common.support.api.feature;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 处理器注解扫描原语：遍历 Spring MVC 全部处理器，对每个处理器按
 * 「先方法级、再类级」的顺序解析指定注解，并产出其排序后的路径模式。
 *
 * <p>该原语是 {@code @ApiFeature} / {@code @ApiGray} 等开关注解的统一扫描入口，
 * 由 {@link ApiFeatureConfiguration} 与控制台扫描器等调用方复用，避免各调用方
 * 各自实现一份遍历 + 注解解析 + 路径排序的重复逻辑（R4：同一能力只保留一份实现）。</p>
 *
 * <p>查找顺序固定为「方法级注解优先，未命中回退类级注解」，与历史各调用方行为一致。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public final class HandlerAnnotationScanner {

    /**
     * Spring MVC 请求映射处理器
     */
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * 构造扫描器。
     *
     * @param requestMappingHandlerMapping Spring MVC 请求映射处理器（可 {@code @Lazy} 注入打破装配环）
     */
    public HandlerAnnotationScanner(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    /**
     * 扫描全部处理器，解析指定注解。
     *
     * @param annotationType 目标注解类型（如 {@code ApiFeature.class} / {@code ApiGray.class}）
     * @param <A>            注解泛型
     * @return 命中注解的处理器清单（含注解实例、处理器方法、映射信息与排序后的路径模式）
     */
    public <A extends Annotation> List<ScannedHandler<A>> scan(Class<A> annotationType) {
        List<ScannedHandler<A>> result = new ArrayList<>();
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            RequestMappingInfo mappingInfo = entry.getKey();

            // 先方法级注解，未命中再回退类级注解
            A annotation = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), annotationType);
            if (annotation == null) {
                annotation = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), annotationType);
            }
            if (annotation == null) {
                continue;
            }

            List<String> patterns = mappingInfo.getPatternValues().stream()
                    .sorted()
                    .collect(Collectors.toList());
            result.add(new ScannedHandler<>(annotation, handlerMethod, mappingInfo, patterns));
        }
        return result;
    }

    /**
     * 扫描结果：命中注解的单个处理器。
     *
     * @param annotation    命中的注解实例
     * @param handlerMethod 处理器方法
     * @param mappingInfo   请求映射信息
     * @param patterns      排序后的路径模式
     * @param <A>           注解泛型
     */
    public record ScannedHandler<A extends Annotation>(A annotation,
                                                       HandlerMethod handlerMethod,
                                                       RequestMappingInfo mappingInfo,
                                                       List<String> patterns) {
    }
}
