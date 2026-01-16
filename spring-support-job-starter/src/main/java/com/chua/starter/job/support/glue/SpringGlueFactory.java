package com.chua.starter.job.support.glue;

import com.chua.starter.common.support.configuration.SpringBeanUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Spring Glue工厂 - 支持Spring依赖注入的Glue工厂实现
 * <p>
 * 继承自 {@link GlueFactory}，重写了 {@link #injectService(Object)} 方法，
 * 支持以下注解的依赖注入：
 * <ul>
 *     <li>{@link Resource} - JSR-250标准注解</li>
 *     <li>{@link Autowired} - Spring标准注解</li>
 *     <li>{@link Qualifier} - 配合Autowired指定具体Bean</li>
 * </ul>
 * </p>
 *
 * <h3>使用示例:</h3>
 * <pre>{@code
 * // Groovy代码中可以使用Spring注入
 * import com.chua.starter.job.support.handler.JobHandler
 * import org.springframework.beans.factory.annotation.Autowired
 * 
 * public class MyHandler implements JobHandler {
 *     @Autowired
 *     private MyService myService;
 *     
 *     public void execute() {
 *         myService.doSomething();
 *     }
 * }
 * }</pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see GlueFactory
 */
@Slf4j
public class SpringGlueFactory extends GlueFactory {

    /**
     * 注入Spring服务依赖
     * <p>
     * 扫描实例的所有字段，对带有 {@link Resource} 或 {@link Autowired}
     * 注解的字段进行Spring依赖注入。
     * </p>
     *
     * @param instance 需要注入依赖的实例，不能为null
     */
    @Override
    public void injectService(Object instance) {
        if (instance == null) {
            return;
        }

        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        if (applicationContext == null) {
            log.warn("ApplicationContext未初始化，无法进行依赖注入");
            return;
        }

        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {
            // 跳过静态字段
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            Object fieldBean = resolveFieldBean(field, applicationContext);
            if (fieldBean != null) {
                injectField(instance, field, fieldBean);
            }
        }
    }

    /**
     * 解析字段对应的Spring Bean
     *
     * @param field              字段
     * @param applicationContext Spring上下文
     * @return 解析到的Bean，如果无法解析则返回null
     */
    private Object resolveFieldBean(Field field, ApplicationContext applicationContext) {
        // 处理 @Resource 注解
        Resource resource = AnnotationUtils.getAnnotation(field, Resource.class);
        if (resource != null) {
            return resolveResourceBean(field, resource, applicationContext);
        }

        // 处理 @Autowired 注解
        Autowired autowired = AnnotationUtils.getAnnotation(field, Autowired.class);
        if (autowired != null) {
            return resolveAutowiredBean(field, applicationContext);
        }

        return null;
    }

    /**
     * 解析 @Resource 注解的Bean
     */
    private Object resolveResourceBean(Field field, Resource resource, ApplicationContext applicationContext) {
        try {
            // 优先按name查找
            if (resource.name() != null && !resource.name().isEmpty()) {
                return applicationContext.getBean(resource.name());
            }
            // 其次按字段名查找
            return applicationContext.getBean(field.getName());
        } catch (Exception e) {
            // 最后按类型查找
            try {
                return applicationContext.getBean(field.getType());
            } catch (Exception ex) {
                log.warn("无法解析@Resource依赖: field={}, type={}", field.getName(), field.getType().getName());
                return null;
            }
        }
    }

    /**
     * 解析 @Autowired 注解的Bean
     */
    private Object resolveAutowiredBean(Field field, ApplicationContext applicationContext) {
        try {
            // 检查是否有 @Qualifier
            Qualifier qualifier = AnnotationUtils.getAnnotation(field, Qualifier.class);
            if (qualifier != null && qualifier.value() != null && !qualifier.value().isEmpty()) {
                return applicationContext.getBean(qualifier.value());
            }
            // 按类型查找
            return applicationContext.getBean(field.getType());
        } catch (Exception e) {
            log.warn("无法解析@Autowired依赖: field={}, type={}", field.getName(), field.getType().getName());
            return null;
        }
    }

    /**
     * 将Bean注入到字段
     */
    private void injectField(Object instance, Field field, Object bean) {
        try {
            field.setAccessible(true);
            field.set(instance, bean);
            log.debug("成功注入依赖: {} -> {}", field.getName(), bean.getClass().getSimpleName());
        } catch (IllegalArgumentException | IllegalAccessException e) {
            log.error("依赖注入失败: field={}, error={}", field.getName(), e.getMessage());
        }
    }
}
