package com.chua.starter.monitor.support.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.Ordered;

/**
 * 数据搜索接口的控制器冲突消歧。
 * <p>
 * 宿主应用（monitor）的 classpath 内同时存在两组控制器：
 * <ul>
 * <li>spring-api-support-common-service-starter 的 {@code SysHolidayController} 与
 * spring-api-support-common-system-starter 的 {@code SysIpController}，历史实现，路径分别为
 * {@code /v2/holiday/get} 与 {@code /v2/ip/lookup}；</li>
 * <li>spring-support-datasearch-starter（经 ai-starter 传递进入）的
 * {@code HolidayV2Controller} 与 {@code IpLookupV2Controller}，数据源已改为 SPI，路径完全相同。</li>
 * </ul>
 * 同一 URL 映射到两个方法时，Spring MVC 的
 * {@code RequestMappingHandlerMapping#getHandler()} 抛出
 * {@code Ambiguous handler methods mapped for ...}，
 * 外层 {@code WebRequest#isPass()} 捕获异常后保持 handlerMethod 为空，
 * {@code AuthFilter} 判断为“需要认证”：HEAD 探测不带 Token，返回 403。
 * 前端 {@code backendProbe} 把 403 视为未实现，把流量切到第三方数据源。
 * <p>
 * 本处理器在注册中心层面移走两个历史控制器的 BeanDefinition，
 * 让 datasearch 的新接口独享对应路径； {@code ServiceConfiguration} /
 * {@code SystemAutoConfiguration} 中的其余 Bean（ {@code /v2/user}、 {@code /v2/captcha}、
 * {@code /manage/*} 等）不受影响。
 * <p>
 * 注意只作用于「同时装入两个模块」的宿主；当宿主的 classpath 中只有历史控制器时，
 * BeanDefinition 不存在，处理器静默跳过。
 *
 * @author CH
 * @since 4.0.0.51
 * @see com.chua.starter.oauth.client.support.web.WebRequest#isPass()
 * @see com.chua.starter.oauth.client.support.filter.AuthFilter
 */
public class DatasearchConflictResolve
        implements BeanDefinitionRegistryPostProcessor, Ordered {

    /**
     * 节假日列表控制器的 Bean 名（ {@code SysHolidayController} 被组件扫描器生成的小写名）
     */
    private static final String SYS_HOLIDAY_CONTROLLER = "sysHolidayController";

    /**
     * IP 归属地控制器的 Bean 名（ {@code SysIpController} 被组件扫描器生成的小写名）
     */
    private static final String SYS_IP_CONTROLLER = "sysIpController";

    /**
     * 优先于所有同类处理器执行，确保在 MappingRegistry 开始装配 Handler 之前完成移除。
     */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
            throws BeansException {
        remove(registry, SYS_HOLIDAY_CONTROLLER);
        remove(registry, SYS_IP_CONTROLLER);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        // 不需要后处理
    }

    /**
     * 执行移除操作
     *
     * @param registry 注册中心
     * @param beanName 控制器 Bean 名
     * @see BeanDefinitionRegistry#removeBeanDefinition(String)
     */
    private void remove(BeanDefinitionRegistry registry, String beanName) {
        if (registry.containsBeanDefinition(beanName)) {
            registry.removeBeanDefinition(beanName);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
