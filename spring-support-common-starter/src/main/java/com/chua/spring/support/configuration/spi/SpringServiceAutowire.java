package com.chua.spring.support.configuration.spi;

import com.chua.common.support.spi.autowire.ServiceAutowire;
import com.chua.spring.support.configuration.SpringBeanUtils;
import org.springframework.context.ApplicationContext;

/**
 * Spring 环境的 SPI 装配器。
 *
 * <p>{@code AutoServiceAutowire} 会按本类的全限定名反射探测并挂到装配链首位。
 * SPI 实现不是 Spring Bean（由 {@code ClassUtils} 无参构造创建），这里补上容器的字段注入，
 * 使扩展点既能按 {@code @Spi} 发现，又能直接使用 Mapper / Service。</p>
 *
 * <p>上下文未就绪时静默跳过：实例会被 SPI 缓存，装配失败的实例无法自愈，
 * 因此注册中心应在首次使用时才解析定义，而不是在容器启动早期就触发实例化。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class SpringServiceAutowire implements ServiceAutowire {

    /**
     * 用容器的自动装配能力补齐 SPI 实例的依赖。
     *
     * @param object 待装配对象
     * @return 原对象
     */
    @Override
    public Object autowire(Object object) {
        if (object == null || SpringBeanUtils.getApplicationContextOrNull() == null) {
            return object;
        }
        SpringBeanUtils.autowireBean(object);
        return object;
    }

    /**
     * 交由容器创建并装配 Bean。
     *
     * @param implClass 实现类
     * @return Bean 实例，上下文未就绪时返回 null
     */
    @Override
    public Object createBean(Class<?> implClass) {
        ApplicationContext context = SpringBeanUtils.getApplicationContextOrNull();
        if (context == null) {
            return null;
        }
        return context.getAutowireCapableBeanFactory().createBean(implClass);
    }
}
