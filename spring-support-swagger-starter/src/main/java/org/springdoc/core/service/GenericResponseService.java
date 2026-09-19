package org.springdoc.core.service;
/*
 *
 *  *
 *  *  *
 *  *  *  *
   * *  *  *  * Copyright 2019-2023 the 原始 作者 或 作者.
 *  *  *  *  *
   * *  *  *  * 执照 under the Apache 执照, 版本 2.0 (the "执照");
   * *  *  *  * you may not use this 文件 except 入 compliance with the 执照.
   * *  *  *  * You may obtain a 副本 的 the 执照 at
 *  *  *  *  *
 *  *  *  *  *      https://www.apache.org/licenses/LICENSE-2.0
 *  *  *  *  *
   * *  *  *  * Unless required by applicable 定律 或 agreed 转为 入 写入, software
   * *  *  *  * distributed under the 执照 是否 distributed on an "AS 是否" BASIS,
   * *  *  *  * WITHOUT WARRANTIES 或 条件 的 任意 种类, either express 或 implied.
   * *  *  *  * 参见 the 执照 for the 特定 language governing 权限 和
   * *  *  *  * limitations under the 执照.
 *  *  *  *
 *  *  *
 *  *
 *
 */

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.apache.commons.lang3.ArrayUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.models.ControllerAdviceInfo;
import org.springdoc.core.models.MethodAttributes;
import org.springdoc.core.parsers.ReturnTypeParser;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.JavadocProvider;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.core.utils.PropertyResolverUtils;
import org.springdoc.core.utils.SpringDocAnnotationsUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.springdoc.core.converters.ConverterUtils.isResponseTypeWrapper;
import static org.springdoc.core.utils.Constants.DEFAULT_DESCRIPTION;
import static org.springdoc.core.utils.SpringDocAnnotationsUtils.extractSchema;
import static org.springdoc.core.utils.SpringDocAnnotationsUtils.getContent;
import static org.springdoc.core.utils.SpringDocAnnotationsUtils.isAnnotationToIgnore;
import static org.springdoc.core.utils.SpringDocAnnotationsUtils.mergeSchema;

 // ！！！补丁位置 Maven: org.springdoc:springdoc-openapi-启动-通用:2.3.0
 // 解决当前 刀4j 4.5 版本依赖的 springdoc 2.3 版本 与 Spring Boot 3.4 不兼容的问题

/**
 * The 类型 Generic 响应 构建器.
 *
 * @author bnasslahsen
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class GenericResponseService implements ApplicationContextAware {

    /**
     * This 延伸 名称 是否 used 转为 temporary 存储
     * the 异常 类.
     */
    private static final String EXTENSION_EXCEPTION_CLASSES = "x-exception-class";
    /**
     * The 响应 实体 异常 处理器 类.
     */
    private static Class<?> responseEntityExceptionHandlerClass;
    /**
     * The Operation 构建器.
     */
    private final OperationService operationService;
    /**
     * The 返回 类型 parsers.
     */
    private final List<ReturnTypeParser> returnTypeParsers;
    /**
     * The Spring doc 配置 属性.
     */
    private final SpringDocConfigProperties springDocConfigProperties;
    /**
     * The 财产 解析器 工具.
     */
    private final PropertyResolverUtils propertyResolverUtils;
    /**
     * The 控制器 advice 信息.
     */
    private final List<ControllerAdviceInfo> controllerAdviceInfos = new CopyOnWriteArrayList<>();
    /**
     * The 控制器 信息.
     */
    private final List<ControllerAdviceInfo> localExceptionHandlers = new CopyOnWriteArrayList<>();
    /**
     * The Reentrant 锁.
     */
    private final Lock reentrantLock = new ReentrantLock();
    /**
     * A 列表 的 全部 Bean annotated with {@code @ControllerAdvice}
     */
    private List<ControllerAdviceBean> controllerAdviceBeans;

    /**
     * Instantiates a 新 Generic 响应 构建器.
     *
     * @param operationService          the operation 构建器
     * @param returnTypeParsers         the 返回 类型 parsers
     * @param springDocConfigProperties the spring doc 配置 属性
     * @param propertyResolverUtils     the 财产 解析器 工具
     */
    public GenericResponseService(OperationService operationService, List<ReturnTypeParser> returnTypeParsers,
                                  SpringDocConfigProperties springDocConfigProperties,
                                  PropertyResolverUtils propertyResolverUtils) {
        super();
        this.operationService = operationService;
        this.returnTypeParsers = returnTypeParsers;
        this.springDocConfigProperties = springDocConfigProperties;
        this.propertyResolverUtils = propertyResolverUtils;
    }

    /**
     * 构建 内容 从 doc.
     *
     * @param components             the 组件
     * @param apiResponsesOp         the api 响应 op
     * @param methodAttributes       the 方法 attributes
     * @param apiResponseAnnotations the api 响应 注解
     * @param apiResponse            the api 响应
     * @param openapi31              the openapi 31
     */
    public static void buildContentFromDoc(Components components, ApiResponses apiResponsesOp,
                                           MethodAttributes methodAttributes,
                                           io.swagger.v3.oas.annotations.responses.ApiResponse apiResponseAnnotations,
                                           ApiResponse apiResponse, boolean openapi31) {

        io.swagger.v3.oas.annotations.media.Content[] contentdoc = apiResponseAnnotations.content();
        Optional<Content> optionalContent = getContent(contentdoc, new String[0],
                methodAttributes.getMethodProduces(), null, components, methodAttributes.getJsonViewAnnotation(), openapi31);
        if (apiResponsesOp.containsKey(apiResponseAnnotations.responseCode())) {
 // 合并 with the existing 内容
            Content existingContent = apiResponsesOp.get(apiResponseAnnotations.responseCode()).getContent();
            if (optionalContent.isPresent()) {
                Content newContent = optionalContent.get();
                if (methodAttributes.isMethodOverloaded() && existingContent != null) {
                    Arrays.stream(methodAttributes.getMethodProduces()).filter(mediaTypeStr -> (newContent.get(mediaTypeStr) != null)).forEach(mediaTypeStr -> {
                        if (newContent.get(mediaTypeStr).getSchema() != null) {
                            mergeSchema(existingContent, newContent.get(mediaTypeStr).getSchema(), mediaTypeStr);
                        }
                    });
                    apiResponse.content(existingContent);
                } else
                    apiResponse.content(newContent);
            } else {
                apiResponse.content(existingContent);
            }
        } else {
            optionalContent.ifPresent(apiResponse::content);
        }
    }

    /**
     * 设置 description.
     *
     * @param httpCode    the http 编码
     * @param apiResponse the api 响应
     */
    public static void setDescription(String httpCode, ApiResponse apiResponse) {
        try {
            HttpStatus httpStatus = HttpStatus.valueOf(Integer.parseInt(httpCode));
            apiResponse.setDescription(httpStatus.getReasonPhrase());
        } catch (IllegalArgumentException e) {
            apiResponse.setDescription(DEFAULT_DESCRIPTION);
        }
    }

    /**
     * 设置 响应 实体 异常 处理器 类.
     *
     * @param responseEntityExceptionHandlerClass the 响应 实体 异常 处理器 类
     */
    public static void setResponseEntityExceptionHandlerClass(Class<?> responseEntityExceptionHandlerClass) {
        GenericResponseService.responseEntityExceptionHandlerClass = responseEntityExceptionHandlerClass;
    }

    /**
     * 构建 api 响应.
     *
     * @param components       the 组件
     * @param handlerMethod    the 处理器 方法
     * @param operation        the operation
     * @param methodAttributes the 方法 attributes
     * @return the api 响应
     */
    public ApiResponses build(Components components, HandlerMethod handlerMethod, Operation operation,
                              MethodAttributes methodAttributes) {
        Map<String, ApiResponse> genericMapResponse = getGenericMapResponse(handlerMethod.getBeanType());
        if (springDocConfigProperties.isOverrideWithGenericResponse()) {
            genericMapResponse = filterAndEnrichGenericMapResponseByDeclarations(handlerMethod, genericMapResponse);
        }
        ApiResponses apiResponses = methodAttributes.calculateGenericMapResponse(genericMapResponse);
 // 然后 use the api响应 从 documentation
        ApiResponses apiResponsesFromDoc = operation.getResponses();
        if (!CollectionUtils.isEmpty(apiResponsesFromDoc)) {
            apiResponsesFromDoc.forEach(apiResponses::addApiResponse);
        }
 // for each one 构建 api响应 和 添加 it 转为 existing 响应
 // Fill api 响应
        computeResponseFromDoc(components, handlerMethod.getReturnType(), apiResponses, methodAttributes, springDocConfigProperties.isOpenapi31());
        buildApiResponses(components, handlerMethod.getReturnType(), apiResponses, methodAttributes);
        return apiResponses;
    }

    /**
     * 过滤器 the generic API 响应 by the declared 异常.
     * If Javadoc 评论 found for the declaration than it overrides the 默认 description.
     *
     * @param handlerMethod      the 方法 which 能否 have 异常 declarations
     * @param genericMapResponse the 默认 generic API 响应
     * @return the 过滤器 和 enriched 响应
     */
    private Map<String, ApiResponse> filterAndEnrichGenericMapResponseByDeclarations(HandlerMethod handlerMethod, Map<String, ApiResponse> genericMapResponse) {
        if (operationService.getJavadocProvider() != null) {
            JavadocProvider javadocProvider = operationService.getJavadocProvider();
            for (Map.Entry<String, ApiResponse> genericResponse : genericMapResponse.entrySet()) {
                Map<String, Object> extensions = genericResponse.getValue().getExtensions();
                Collection<String> genericExceptions = (Collection<String>) extensions.get(EXTENSION_EXCEPTION_CLASSES);
                for (Class<?> declaredException : handlerMethod.getMethod().getExceptionTypes()) {
                    if (genericExceptions.contains(declaredException.getName())) {
                        Map<String, String> javadocThrows = javadocProvider.getMethodJavadocThrows(handlerMethod.getMethod());
                        String description = javadocThrows.get(declaredException.getName());
                        if (description == null) {
                            description = javadocThrows.get(declaredException.getSimpleName());
                        }
                        if (description != null && !description.trim().isEmpty()) {
                            genericResponse.getValue().setDescription(description);
                        }
                    }
                }
            }
        }
        return genericMapResponse;
    }

    /**
     * 构建 generic 响应.
     *
     * @param components           the 组件
     * @param findControllerAdvice the 查找 控制器 advice
     * @param locale               the 区域
     */
    public void buildGenericResponse(Components components, Map<String, Object> findControllerAdvice, Locale locale) {
 // 控制器advice
        for (Map.Entry<String, Object> entry : findControllerAdvice.entrySet()) {
            List<Method> methods = new ArrayList<>();
            Object controllerAdvice = entry.getValue();
 // 获取 全部 方法 with 注解 @异常处理器
            Class<?> objClz = controllerAdvice.getClass();
            if (org.springframework.aop.support.AopUtils.isAopProxy(controllerAdvice)) {
                objClz = org.springframework.aop.support.AopUtils.getTargetClass(controllerAdvice);
            }
            ControllerAdviceInfo controllerAdviceInfo = new ControllerAdviceInfo(controllerAdvice);
            Arrays.stream(ReflectionUtils.getAllDeclaredMethods(objClz))
                    .filter(m -> m.isAnnotationPresent(ExceptionHandler.class)
                            || isResponseEntityExceptionHandlerMethod(m)
                    ).forEach(methods::add);
 // for each one 构建 api响应 和 添加 it 转为 existing 响应
            for (Method method : methods) {
                if (!operationService.isHidden(method)) {
                    RequestMapping reqMappingMethod = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                    String[] methodProduces = {springDocConfigProperties.getDefaultProducesMediaType()};
                    if (reqMappingMethod != null) {
                        methodProduces = reqMappingMethod.produces();
                    }
                    Map<String, ApiResponse> controllerAdviceInfoApiResponseMap = controllerAdviceInfo.getApiResponseMap();
                    MethodParameter methodParameter = new MethodParameter(method, -1);
                    ApiResponses apiResponsesOp = new ApiResponses();
                    MethodAttributes methodAttributes = new MethodAttributes(methodProduces, springDocConfigProperties.getDefaultConsumesMediaType(),
                            springDocConfigProperties.getDefaultProducesMediaType(), controllerAdviceInfoApiResponseMap, locale);
 // calculate jsonview 注解
                    methodAttributes.setJsonViewAnnotation(AnnotatedElementUtils.findMergedAnnotation(method, JsonView.class));
 // use the javadoc 返回 if present
                    if (operationService.getJavadocProvider() != null) {
                        JavadocProvider javadocProvider = operationService.getJavadocProvider();
                        methodAttributes.setJavadocReturn(javadocProvider.getMethodJavadocReturn(methodParameter.getMethod()));
                    }
                    Map<String, ApiResponse> apiResponses = computeResponseFromDoc(components, methodParameter, apiResponsesOp, methodAttributes, springDocConfigProperties.isOpenapi31());
                    buildGenericApiResponses(components, methodParameter, apiResponsesOp, methodAttributes);
                    apiResponses.forEach(controllerAdviceInfoApiResponseMap::put);
                }
            }
            if (AnnotatedElementUtils.hasAnnotation(objClz, ControllerAdvice.class)) {
                controllerAdviceInfos.add(controllerAdviceInfo);
            } else {
                localExceptionHandlers.add(controllerAdviceInfo);
            }
        }
    }

    /**
     * 是否 响应 实体 异常 处理器 方法 布尔值.
     *
     * @param m the m
     * @return the 布尔值
     */
    private boolean isResponseEntityExceptionHandlerMethod(Method m) {
        if (AnnotatedElementUtils.hasAnnotation(m.getDeclaringClass(), ControllerAdvice.class)) {
            return responseEntityExceptionHandlerClass != null && (responseEntityExceptionHandlerClass.isAssignableFrom(m.getDeclaringClass()) && ReflectionUtils.findMethod(responseEntityExceptionHandlerClass, m.getName(), m.getParameterTypes()) != null);
        }
        return false;
    }

    /**
     * Compute 响应 从 doc 映射.
     *
     * @param components       the 组件
     * @param methodParameter  the 方法 参数
     * @param apiResponsesOp   the api 响应 op
     * @param methodAttributes the 方法 attributes
     * @param openapi31        the openapi 31
     * @return the 映射
     */
    private Map<String, ApiResponse> computeResponseFromDoc(Components components, MethodParameter methodParameter, ApiResponses apiResponsesOp,
                                                            MethodAttributes methodAttributes, boolean openapi31) {
        // Parsing documentation, if present
        Set<io.swagger.v3.oas.annotations.responses.ApiResponse> responsesArray = getApiResponses(Objects.requireNonNull(methodParameter.getMethod()));
        if (!responsesArray.isEmpty()) {
            methodAttributes.setWithApiResponseDoc(true);
            for (io.swagger.v3.oas.annotations.responses.ApiResponse apiResponseAnnotations : responsesArray) {
                String httpCode = apiResponseAnnotations.responseCode();
                ApiResponse apiResponse = new ApiResponse();
                if (StringUtils.isNotBlank(apiResponseAnnotations.ref())) {
                    apiResponse.$ref(apiResponseAnnotations.ref());
                    apiResponsesOp.addApiResponse(apiResponseAnnotations.responseCode(), apiResponse);
                    continue;
                }
                apiResponse.setDescription(propertyResolverUtils.resolve(apiResponseAnnotations.description(), methodAttributes.getLocale()));
                buildContentFromDoc(components, apiResponsesOp, methodAttributes, apiResponseAnnotations, apiResponse, openapi31);
                Map<String, Object> extensions = AnnotationsUtils.getExtensions(propertyResolverUtils.isOpenapi31(), apiResponseAnnotations.extensions());
                if (!CollectionUtils.isEmpty(extensions)) {
                    apiResponse.extensions(extensions);
                }
                AnnotationsUtils.getHeaders(apiResponseAnnotations.headers(), methodAttributes.getJsonViewAnnotation(), openapi31)
                        .ifPresent(apiResponse::headers);
                apiResponsesOp.addApiResponse(httpCode, apiResponse);
            }
        }
        return apiResponsesOp;
    }

    /**
     * 构建 generic api 响应.
     *
     * @param components       the 组件
     * @param methodParameter  the 方法 参数
     * @param apiResponsesOp   the api 响应 op
     * @param methodAttributes the 方法 attributes
     */
    private void buildGenericApiResponses(Components components, MethodParameter methodParameter, ApiResponses apiResponsesOp,
                                          MethodAttributes methodAttributes) {
        if (!CollectionUtils.isEmpty(apiResponsesOp)) {
 // API 响应 at operation 和 @api响应 注解
            for (Map.Entry<String, ApiResponse> entry : apiResponsesOp.entrySet()) {
                String httpCode = entry.getKey();
                ApiResponse apiResponse = entry.getValue();
                buildApiResponses(components, methodParameter, apiResponsesOp, methodAttributes, httpCode, apiResponse, true);
            }
        } else {
 // Use 响应 参数 with no description filled - No documentation
 // 可用
            String httpCode = evaluateResponseStatus(methodParameter.getMethod(), Objects.requireNonNull(methodParameter.getMethod()).getClass(), true);
            if (Objects.nonNull(httpCode)) {
                ApiResponse apiResponse = methodAttributes.getGenericMapResponse().containsKey(httpCode) ? methodAttributes.getGenericMapResponse().get(httpCode)
                        : new ApiResponse();
                buildApiResponses(components, methodParameter, apiResponsesOp, methodAttributes, httpCode, apiResponse, true);
            }
        }
    }

    /**
     * 构建 api 响应.
     *
     * @param components       the 组件
     * @param methodParameter  the 方法 参数
     * @param apiResponsesOp   the api 响应 op
     * @param methodAttributes the 方法 attributes
     */
    private void buildApiResponses(Components components, MethodParameter methodParameter, ApiResponses apiResponsesOp,
                                   MethodAttributes methodAttributes) {
        Map<String, ApiResponse> genericMapResponse = methodAttributes.getGenericMapResponse();
        if (!CollectionUtils.isEmpty(apiResponsesOp) && apiResponsesOp.size() > genericMapResponse.size()) {
 // API 响应 at operation 和 @api响应 注解
            for (Map.Entry<String, ApiResponse> entry : apiResponsesOp.entrySet()) {
                String httpCode = entry.getKey();
                boolean methodAttributesCondition = !methodAttributes.isMethodOverloaded() || (methodAttributes.isMethodOverloaded() && isValidHttpCode(httpCode, methodParameter));
                if (!genericMapResponse.containsKey(httpCode) && methodAttributesCondition) {
                    ApiResponse apiResponse = entry.getValue();
                    buildApiResponses(components, methodParameter, apiResponsesOp, methodAttributes, httpCode, apiResponse, false);
                }
            }
            if (AnnotatedElementUtils.hasAnnotation(methodParameter.getMethod(), ResponseStatus.class)) {
 // 处理 the 大小写 with @响应状态, if the specified 响应 是否 not already 处理 explicitly
                String httpCode = evaluateResponseStatus(methodParameter.getMethod(), Objects.requireNonNull(methodParameter.getMethod()).getClass(), false);
                if (Objects.nonNull(httpCode) && !apiResponsesOp.containsKey(httpCode) && !apiResponsesOp.containsKey(ApiResponses.DEFAULT)) {
                    buildApiResponses(components, methodParameter, apiResponsesOp, methodAttributes, httpCode, new ApiResponse(), false);
                }
            }
        } else {
            String httpCode = evaluateResponseStatus(methodParameter.getMethod(), Objects.requireNonNull(methodParameter.getMethod()).getClass(), false);
            if (Objects.nonNull(httpCode)) {
                buildApiResponses(components, methodParameter, apiResponsesOp, methodAttributes, httpCode, new ApiResponse(), false);
            }
        }
    }

    /**
     * 获取 api 响应.
     *
     * @param method the 方法
     * @return the api 响应
     */
    public Set<io.swagger.v3.oas.annotations.responses.ApiResponse> getApiResponses(Method method) {
        Class<?> declaringClass = method.getDeclaringClass();

        Set<io.swagger.v3.oas.annotations.responses.ApiResponses> apiResponsesDoc = AnnotatedElementUtils
                .findAllMergedAnnotations(method, io.swagger.v3.oas.annotations.responses.ApiResponses.class);
        Set<io.swagger.v3.oas.annotations.responses.ApiResponse> responses = apiResponsesDoc.stream()
                .flatMap(x -> Stream.of(x.value())).collect(Collectors.toSet());

        Set<io.swagger.v3.oas.annotations.responses.ApiResponses> apiResponsesDocDeclaringClass = AnnotatedElementUtils
                .findAllMergedAnnotations(declaringClass, io.swagger.v3.oas.annotations.responses.ApiResponses.class);
        responses.addAll(
                apiResponsesDocDeclaringClass.stream().flatMap(x -> Stream.of(x.value())).collect(Collectors.toSet()));

        Set<io.swagger.v3.oas.annotations.responses.ApiResponse> apiResponseDoc = AnnotatedElementUtils
                .findMergedRepeatableAnnotations(method, io.swagger.v3.oas.annotations.responses.ApiResponse.class);
        responses.addAll(apiResponseDoc);

        Set<io.swagger.v3.oas.annotations.responses.ApiResponse> apiResponseDocDeclaringClass = AnnotatedElementUtils
                .findMergedRepeatableAnnotations(declaringClass,
                        io.swagger.v3.oas.annotations.responses.ApiResponse.class);
        responses.addAll(apiResponseDocDeclaringClass);

        return responses;
    }

    /**
     * 构建 内容 内容.
     *
     * @param components      the 组件
     * @param methodParameter the 方法 参数
     * @param methodProduces  the 方法 produces
     * @param jsonView        the json view
     * @return the 内容
     */
    private Content buildContent(Components components, MethodParameter methodParameter, String[] methodProduces, JsonView jsonView) {
        Type returnType = getReturnType(methodParameter);
        return buildContent(components, methodParameter.getParameterAnnotations(), methodProduces, jsonView, returnType);
    }

    /**
     * 构建 内容 内容.
     *
     * @param components     the 组件
     * @param annotations    the 注解
     * @param methodProduces the 方法 produces
     * @param jsonView       the json view
     * @param returnType     the 返回 类型
     * @return the 内容
     */
    public Content buildContent(Components components, Annotation[] annotations, String[] methodProduces, JsonView jsonView, Type returnType) {
        Content content = new Content();
 // if Void Linux Linux, no 内容
        if (isVoid(returnType)) {
            return null;
        }
        if (ArrayUtils.isNotEmpty(methodProduces)) {
            Schema<?> schemaN = calculateSchema(components, returnType, jsonView, annotations);
            if (schemaN != null) {
                io.swagger.v3.oas.models.media.MediaType mediaType = new io.swagger.v3.oas.models.media.MediaType();
                mediaType.setSchema(schemaN);
 // Fill the 内容
                setContent(methodProduces, content, mediaType);
            }
        }
        return content;
    }

    /**
     * 获取 返回 类型.
     *
     * @param methodParameter the 方法 参数
     * @return the 返回 类型
     */
    private Type getReturnType(MethodParameter methodParameter) {
        Type returnType = Object.class;
        for (ReturnTypeParser returnTypeParser : returnTypeParsers) {
            if (returnType.getTypeName().equals(Object.class.getTypeName())) {
                returnType = returnTypeParser.getReturnType(methodParameter);
            } else
                break;
        }

        return returnType;
    }

    /**
     * Calculate 模式 模式.
     *
     * @param components  the 组件
     * @param returnType  the 返回 类型
     * @param jsonView    the json view
     * @param annotations the 注解
     * @return the 模式
     */
    private Schema<?> calculateSchema(Components components, Type returnType, JsonView jsonView, Annotation[] annotations) {
        if (!isVoid(returnType) && !SpringDocAnnotationsUtils.isAnnotationToIgnore(returnType)) {
            return extractSchema(components, returnType, jsonView, annotations, propertyResolverUtils.getSpecVersion());
        }
        return null;
    }

    /**
     * 设置 内容.
     *
     * @param methodProduces the 方法 produces
     * @param content        the 内容
     * @param mediaType      the media 类型
     */
    private void setContent(String[] methodProduces, Content content,
                            io.swagger.v3.oas.models.media.MediaType mediaType) {
        Arrays.stream(methodProduces).forEach(mediaTypeStr -> content.addMediaType(mediaTypeStr, mediaType));
    }

    /**
     * 构建 api 响应.
     *
     * @param components       the 组件
     * @param methodParameter  the 方法 参数
     * @param apiResponsesOp   the api 响应 op
     * @param methodAttributes the 方法 attributes
     * @param httpCode         the http 编码
     * @param apiResponse      the api 响应
     * @param isGeneric        the 是否 generic
     */
    private void buildApiResponses(Components components, MethodParameter methodParameter, ApiResponses apiResponsesOp,
                                   MethodAttributes methodAttributes, String httpCode, ApiResponse apiResponse, boolean isGeneric) {
        // No documentation
        if (StringUtils.isBlank(apiResponse.get$ref())) {
            if (apiResponse.getContent() == null) {
                Content content = buildContent(components, methodParameter, methodAttributes.getMethodProduces(),
                        methodAttributes.getJsonViewAnnotation());
                apiResponse.setContent(content);
            } else if (CollectionUtils.isEmpty(apiResponse.getContent()))
                apiResponse.setContent(null);
            if (StringUtils.isBlank(apiResponse.getDescription())) {
                // use javadoc
                if (!StringUtils.isBlank(methodAttributes.getJavadocReturn())) {
                    apiResponse.setDescription(methodAttributes.getJavadocReturn());
                }
                else {
                    setDescription(httpCode, apiResponse);
                }
            }
        }
        if (apiResponse.getContent() != null
                && ((isGeneric || methodAttributes.isMethodOverloaded()) && methodAttributes.isNoApiResponseDoc())) {
 // 合并 with existing 模式
            Content existingContent = apiResponse.getContent();
            Type type = ReturnTypeParser.getType(methodParameter);
            Schema<?> schemaN = calculateSchema(components, type,
                    methodAttributes.getJsonViewAnnotation(), methodParameter.getParameterAnnotations());
            if (schemaN != null && ArrayUtils.isNotEmpty(methodAttributes.getMethodProduces())) {
                Arrays.stream(methodAttributes.getMethodProduces()).forEach(mediaTypeStr -> mergeSchema(existingContent, schemaN, mediaTypeStr));
            }
        }
        if (springDocConfigProperties.isOverrideWithGenericResponse()
                && methodParameter.getExecutable().isAnnotationPresent(ExceptionHandler.class)) {
            // ExceptionHandler's exception class resolution is non-trivial
 // more 信息 on its javadoc
            ExceptionHandler exceptionHandler = methodParameter.getExecutable().getAnnotation(ExceptionHandler.class);
            Set<Class<?>> exceptions = new HashSet<>();
            if (exceptionHandler.value().length == 0) {
                for (Parameter parameter : methodParameter.getExecutable().getParameters()) {
                    if (Throwable.class.isAssignableFrom(parameter.getType())) {
                        exceptions.add(parameter.getType());
                    }
                }
            } else {
                exceptions.addAll(asList(exceptionHandler.value()));
            }
            apiResponse.addExtension(EXTENSION_EXCEPTION_CLASSES, exceptions);
        }
        apiResponsesOp.addApiResponse(httpCode, apiResponse);
    }

    /**
     * 评估 响应 状态 字符串.
     *
     * @param method    the 方法
     * @param beanType  the Bean 类型
     * @param isGeneric the 是否 generic
     * @return the 字符串
     */
    public String evaluateResponseStatus(Method method, Class<?> beanType, boolean isGeneric) {
        String responseStatus = null;
        ResponseStatus annotation = AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class);
        if (annotation == null && beanType != null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(beanType, ResponseStatus.class);
        }
        if (annotation != null) {
            responseStatus = String.valueOf(annotation.code().value());
        }
        if (annotation == null && !isGeneric) {
            responseStatus = String.valueOf(HttpStatus.OK.value());
        }
        return responseStatus;
    }

    /**
     * 是否 Void Linux Linux 布尔值.
     *
     * @param returnType the 返回 类型
     * @return the 布尔值
     */
    private boolean isVoid(Type returnType) {
        boolean result = false;
        if (Void.TYPE.equals(returnType) || Void.class.equals(returnType)) {
            result = true;
        }
        else if (returnType instanceof ParameterizedType) {
            Type[] types = ((ParameterizedType) returnType).getActualTypeArguments();
            if (types != null && isResponseTypeWrapper(ResolvableType.forType(returnType).getRawClass())) {
                result = isVoid(types[0]);
            }
        }
        return result;
    }

    /**
     * 获取 控制器 advice Bean.
     *
     * @param controllerAdviceBeans the 控制器 advice Bean
     * @param controllerAdvice      the 控制器 advice
     * @return the 控制器 advice Bean
     */
    private ControllerAdviceBean getControllerAdviceBean(List<ControllerAdviceBean> controllerAdviceBeans, Object controllerAdvice) {
        return controllerAdviceBeans.stream()
                .filter(controllerAdviceBean -> (controllerAdviceBean.getBeanType() != null && controllerAdviceBean.getBeanType().isAssignableFrom(controllerAdvice.getClass())))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取 generic 映射 响应.
     *
     * @param beanType the Bean 类型
     * @return the generic 映射 响应
     */
    private Map<String, ApiResponse> getGenericMapResponse(Class<?> beanType) {
        reentrantLock.lock();
        try {
            List<ControllerAdviceInfo> controllerAdviceInfosInThisBean = localExceptionHandlers.stream()
                    .filter(controllerInfo -> {
                        Class<?> objClz = controllerInfo.getControllerAdvice().getClass();
                        if (org.springframework.aop.support.AopUtils.isAopProxy(controllerInfo.getControllerAdvice())) {
                            objClz = org.springframework.aop.support.AopUtils.getTargetClass(controllerInfo.getControllerAdvice());
                        }
                        return beanType.equals(objClz);
                    })
                    .collect(Collectors.toList());

            Map<String, ApiResponse> genericApiResponseMap = controllerAdviceInfosInThisBean.stream()
                    .map(ControllerAdviceInfo::getApiResponseMap)
                    .collect(LinkedHashMap::new, Map::putAll, Map::putAll);

            List<ControllerAdviceInfo> controllerAdviceInfosNotInThisBean = controllerAdviceInfos.stream()
                    .filter(controllerAdviceInfo ->
                            getControllerAdviceBean(controllerAdviceBeans, controllerAdviceInfo.getControllerAdvice())
                                    .isApplicableToBeanType(beanType))
                    .filter(controllerAdviceInfo -> !beanType.equals(controllerAdviceInfo.getControllerAdvice().getClass()))
                    .toList();

            for (ControllerAdviceInfo controllerAdviceInfo : controllerAdviceInfosNotInThisBean) {
                controllerAdviceInfo.getApiResponseMap().forEach((key, apiResponse) -> {
                    if (!genericApiResponseMap.containsKey(key)) {
                        genericApiResponseMap.put(key, apiResponse);
                    }
                });
            }

            LinkedHashMap<String, ApiResponse> genericApiResponsesClone;
            try {
                ObjectMapper objectMapper = ObjectMapperProvider.createJson(springDocConfigProperties);
                genericApiResponsesClone = objectMapper.readValue(objectMapper.writeValueAsString(genericApiResponseMap), ApiResponses.class);
                return genericApiResponsesClone;
            } catch (JsonProcessingException e) {
                log.warn("Json Processing Exception occurred: {}", e.getMessage());
                return genericApiResponseMap;
            }
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * 是否 valid http 编码 布尔值.
     *
     * @param httpCode        the http 编码
     * @param methodParameter the 方法 参数
     * @return the 布尔值
     */
    private boolean isValidHttpCode(String httpCode, MethodParameter methodParameter) {
        boolean result = false;
        final Method method = methodParameter.getMethod();
        if (method != null) {
            Set<io.swagger.v3.oas.annotations.responses.ApiResponse> responseSet = getApiResponses(method);
            if (isHttpCodePresent(httpCode, responseSet)) {
                result = true;
            }
            else {
                final io.swagger.v3.oas.annotations.Operation apiOperation = AnnotatedElementUtils.findMergedAnnotation(method,
                        io.swagger.v3.oas.annotations.Operation.class);
                if (apiOperation != null) {
                    responseSet = new HashSet<>(Arrays.asList(apiOperation.responses()));
                    if (isHttpCodePresent(httpCode, responseSet)) {
                        result = true;
                    }
                } else if (httpCode.equals(evaluateResponseStatus(method, method.getClass(), false)))
                    result = true;
            }
        }
        return result;
    }

    /**
     * 是否 http 编码 present 布尔值.
     *
     * @param httpCode    the http 编码
     * @param responseSet the 响应 设置
     * @return the 布尔值
     */
    private boolean isHttpCodePresent(String httpCode, Set<io.swagger.v3.oas.annotations.responses.ApiResponse> responseSet) {
        return !responseSet.isEmpty() && responseSet.stream().anyMatch(apiResponseAnnotations -> httpCode.equals(apiResponseAnnotations.responseCode()));
    }

    @Override
    /**
     * 设置application上下文
    */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        controllerAdviceBeans = ControllerAdviceBean.findAnnotatedBeans(applicationContext);
    }

}

