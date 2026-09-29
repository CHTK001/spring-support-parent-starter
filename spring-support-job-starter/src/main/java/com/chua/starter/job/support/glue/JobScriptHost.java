package com.chua.starter.job.support.glue;

import com.chua.common.support.task.script.ScriptHost;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

/**
 * 片段执行宿主的 Spring 实现。
 *
 * <p>把 {@link ApplicationContext} 适配成 utils 层的 {@link ScriptHost}：脚本引擎在 utils 层，
 * 不能引用 Spring，于是「取 Bean」与「字段装配」这类容器语义经本类注入。
 * 引擎拿到的是接口，因此换成别的宿主（单测、命令行）也能跑。</p>
 *
 * <p>实例随每次执行新建一个：它很轻（只持一个容器引用），做成单例反而要多考虑生命周期。
 * 容器允许为 null（脱离 Spring 装配的场景），此时两个方法都安全降级。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public class JobScriptHost implements ScriptHost {

    /**
     * 宿主容器，可能为 null
     */
    private final ApplicationContext applicationContext;

    /**
     * 构造 片段执行宿主。
     *
     * @param applicationContext 宿主容器，允许为 null
     */
    public JobScriptHost(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 按类型取宿主 Bean。
     *
     * <p>取不到时返回 null 而不是抛异常：片段里把 bean 当可选依赖用是常见写法，
     * 引擎需要能区分「没有」和「容器坏了」。</p>
     *
     * @param type 目标类型
     * @param <T>  目标类型泛型
     * @return 命中的 Bean；无容器或无候选时返回 null
     */
    @Override
    public <T> T getBean(Class<T> type) {
        if (applicationContext == null || type == null) {
            return null;
        }
        try {
            return applicationContext.getBean(type);
        } catch (NoSuchBeanDefinitionException ex) {
            return null;
        }
    }

    /**
     * 对片段实例做 Spring 字段装配，使 {@code @Autowired} 生效。
     *
     * <p>用 {@code AUTOWIRE_BY_TYPE} 而不是按名装配：片段类名由合成器生成，
     * 字段名是用户起的，按名装配会大量漏配。</p>
     *
     * @param instance 片段实例，允许为 null
     */
    @Override
    public void inject(Object instance) {
        if (applicationContext == null || instance == null) {
            return;
        }
        AutowireCapableBeanFactory factory = applicationContext.getAutowireCapableBeanFactory();
        if (factory != null) {
            factory.autowireBeanProperties(instance, AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE, false);
        }
    }
}
