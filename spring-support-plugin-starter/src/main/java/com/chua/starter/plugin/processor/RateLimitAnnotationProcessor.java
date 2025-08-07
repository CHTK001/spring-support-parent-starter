//package com.chua.starter.plugin.processor;
//
//import com.chua.starter.plugin.annotation.RateLimit;
//import com.chua.starter.plugin.entity.PluginRateLimitConfig;
//import com.chua.starter.plugin.service.RateLimitConfigService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.config.BeanPostProcessor;
//import org.springframework.core.annotation.AnnotationUtils;
//import org.springframework.stereotype.Component;
//import org.springframework.util.ReflectionUtils;
//import org.springframework.web.bind.annotation.*;
//
//import java.lang.reflect.Method;
//
///**
// * 限流注解处理器 扫描和处理@RateLimit注解，将配置保存到数据库和内存
// *
// * @author CH
// * @since 2025/1/16
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class RateLimitAnnotationProcessor implements BeanPostProcessor {
//
//    private final RateLimitConfigService configService;
//
//    @Override
//    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
//        Class<?> clazz = bean.getClass();
//
//        // 检查类级别的@RateLimit注解
//        processClassAnnotation(clazz);
//
//        // 检查方法级别的@RateLimit注解
//        processMethodAnnotations(clazz);
//
//        return bean;
//    }
//
//    /**
//     * 处理类级别的@RateLimit注解
//     *
//     * @param clazz 类
//     */
//    private void processClassAnnotation(Class<?> clazz) {
//        RateLimit classRateLimit = AnnotationUtils.findAnnotation(clazz, RateLimit.class);
//        if (classRateLimit == null) {
//            return;
//        }
//
//        // 获取类的基础路径
//        String basePath = getBasePath(clazz);
//        if (basePath != null) {
//            createRateLimitConfig(classRateLimit, basePath, clazz.getSimpleName());
//        }
//    }
//
//    /**
//     * 处理方法级别的@RateLimit注解
//     *
//     * @param clazz 类
//     */
//    private void processMethodAnnotations(Class<?> clazz) {
//        ReflectionUtils.doWithMethods(clazz, method -> {
//            RateLimit methodRateLimit = AnnotationUtils.findAnnotation(method, RateLimit.class);
//            if (methodRateLimit == null) {
//                return;
//            }
//
//            // 获取方法的完整路径
//            String methodPath = getMethodPath(clazz, method);
//            if (methodPath != null) {
//                createRateLimitConfig(methodRateLimit, methodPath, clazz.getSimpleName() + "." + method.getName());
//            }
//        });
//    }
//
//    /**
//     * 创建限流配置
//     *
//     * @param rateLimit   注解
//     * @param path        API路径
//     * @param description 描述
//     */
//    private void createRateLimitConfig(RateLimit rateLimit, String path, String description) {
//        try {
//            // 确定限流键
//            String limitKey = rateLimit.key().isEmpty() ? path : rateLimit.key();
//
//            // 创建API限流配置
//            PluginRateLimitConfig config = createConfigFromAnnotation(rateLimit, limitKey, description);
//            saveConfigIfNotExists(config);
//
//            // 如果同时限制IP，创建IP限流配置
//            if (rateLimit.limitIp()) {
//                PluginRateLimitConfig ipConfig = new PluginRateLimitConfig();
//                ipConfig.setLimitType(PluginRateLimitConfig.LimitType.IP);
//                ipConfig.setLimitKey("*"); // 通配符表示所有IP
//                ipConfig.setQps(rateLimit.ipQps());
//                ipConfig.setBurstCapacity(rateLimit.ipQps() * 2);
//                ipConfig.setAlgorithmType(PluginRateLimitConfig.fromAnnotationAlgorithmType(rateLimit.algorithm()));
//                ipConfig.setOverflowStrategy(PluginRateLimitConfig.fromAnnotationOverflowStrategy(rateLimit.overflowStrategy()));
//                ipConfig.setWindowSizeSeconds(rateLimit.windowSizeSeconds());
//                ipConfig.setEnabled(rateLimit.enabled());
//                ipConfig.setDescription("IP限流 - " + description);
//
//                saveConfigIfNotExists(ipConfig);
//            }
//
//        } catch (Exception e) {
//            log.error("Failed to create rate limit config for path: {}", path, e);
//        }
//    }
//
//    /**
//     * 从注解创建配置对象
//     *
//     * @param rateLimit   注解
//     * @param limitKey    限流键
//     * @param description 描述
//     * @return 配置对象
//     */
//    private PluginRateLimitConfig createConfigFromAnnotation(RateLimit rateLimit, String limitKey, String description) {
//        PluginRateLimitConfig config = new PluginRateLimitConfig();
//        config.setLimitType(PluginRateLimitConfig.fromAnnotationLimitType(rateLimit.limitType()));
//        config.setLimitKey(limitKey);
//        config.setQps(rateLimit.qps());
//        config.setBurstCapacity(rateLimit.burstCapacity());
//        config.setAlgorithmType(PluginRateLimitConfig.fromAnnotationAlgorithmType(rateLimit.algorithm()));
//        config.setOverflowStrategy(PluginRateLimitConfig.fromAnnotationOverflowStrategy(rateLimit.overflowStrategy()));
//        config.setWindowSizeSeconds(rateLimit.windowSizeSeconds());
//        config.setEnabled(rateLimit.enabled());
//        config.setDescription(rateLimit.description().isEmpty() ? description : rateLimit.description());
//        config.setCreatedBy("ANNOTATION_PROCESSOR");
//        config.setUpdatedBy("ANNOTATION_PROCESSOR");
//
//        return config;
//    }
//
//    /**
//     * 如果配置不存在则保存
//     *
//     * @param config 配置
//     */
//    private void saveConfigIfNotExists(PluginRateLimitConfig config) {
//        try {
//            // 检查是否已存在
//            if (!configService.getConfig(config.getLimitType(), config.getLimitKey()).isPresent()) {
//                configService.saveConfig(config);
//                log.info("Created rate limit config from annotation: {} -> {} QPS", config.getUniqueKey(),
//                        config.getQps());
//            } else {
//                log.debug("Rate limit config already exists: {}", config.getUniqueKey());
//            }
//        } catch (Exception e) {
//            log.error("Failed to save rate limit config: {}", config.getUniqueKey(), e);
//        }
//    }
//
//    /**
//     * 获取类的基础路径
//     *
//     * @param clazz 类
//     * @return 基础路径
//     */
//    private String getBasePath(Class<?> clazz) {
//        RequestMapping classMapping = AnnotationUtils.findAnnotation(clazz, RequestMapping.class);
//        if (classMapping != null && classMapping.value().length > 0) {
//            return classMapping.value()[0];
//        }
//
//        RestController restController = AnnotationUtils.findAnnotation(clazz, RestController.class);
//        // 检查是否为Controller类
//        boolean isController = clazz.getSimpleName().toLowerCase().contains("controller");
//
//        if (restController != null || isController) {
//            return "/" + clazz.getSimpleName().toLowerCase().replace("controller", "");
//        }
//
//        return null;
//    }
//
//    /**
//     * 获取方法的完整路径
//     *
//     * @param clazz  类
//     * @param method 方法
//     * @return 完整路径
//     */
//    private String getMethodPath(Class<?> clazz, Method method) {
//        String basePath = getBasePath(clazz);
//        if (basePath == null) {
//            basePath = "";
//        }
//
//        // 获取方法路径
//        String methodPath = getMethodMappingPath(method);
//        if (methodPath == null) {
//            return null;
//        }
//
//        // 组合完整路径
//        String fullPath = basePath + methodPath;
//        return fullPath.replaceAll("//+", "/"); // 去除多余的斜杠
//    }
//
//    /**
//     * 获取方法映射路径
//     *
//     * @param method 方法
//     * @return 映射路径
//     */
//    private String getMethodMappingPath(Method method) {
//        // 检查各种映射注解
//        RequestMapping requestMapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
//        if (requestMapping != null && requestMapping.value().length > 0) {
//            return requestMapping.value()[0];
//        }
//
//        GetMapping getMapping = AnnotationUtils.findAnnotation(method, GetMapping.class);
//        if (getMapping != null && getMapping.value().length > 0) {
//            return getMapping.value()[0];
//        }
//
//        PostMapping postMapping = AnnotationUtils.findAnnotation(method, PostMapping.class);
//        if (postMapping != null && postMapping.value().length > 0) {
//            return postMapping.value()[0];
//        }
//
//        PutMapping putMapping = AnnotationUtils.findAnnotation(method, PutMapping.class);
//        if (putMapping != null && putMapping.value().length > 0) {
//            return putMapping.value()[0];
//        }
//
//        DeleteMapping deleteMapping = AnnotationUtils.findAnnotation(method, DeleteMapping.class);
//        if (deleteMapping != null && deleteMapping.value().length > 0) {
//            return deleteMapping.value()[0];
//        }
//
//        PatchMapping patchMapping = AnnotationUtils.findAnnotation(method, PatchMapping.class);
//        if (patchMapping != null && patchMapping.value().length > 0) {
//            return patchMapping.value()[0];
//        }
//
//        // 如果没有找到映射注解，使用方法名
//        return "/" + method.getName();
//    }
//}
